#!/usr/bin/env python3
"""Audit the actual Play bundle, including permissions, update code and native alignment."""
import argparse
import hashlib
import json
import struct
import subprocess
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("bundle", type=Path)
parser.add_argument("--bundletool", required=True, type=Path, help="Official bundletool-all JAR")
parser.add_argument("--java", default="java")
args = parser.parse_args()
command = [args.java, "-jar", str(args.bundletool)]
subprocess.run(command + ["validate", "--bundle=" + str(args.bundle)], check=True, stdout=subprocess.DEVNULL)
manifest = subprocess.check_output(command + ["dump", "manifest", "--bundle=" + str(args.bundle), "--module=base"], text=True)
root = ET.fromstring(manifest)
android = "{http://schemas.android.com/apk/res/android}"
application = root.find("application")
permissions = {e.get(android + "name") for e in root.findall("uses-permission")}
assert not permissions.intersection({"android.permission.INTERNET", "android.permission.REQUEST_INSTALL_PACKAGES", "android.permission.QUERY_ALL_PACKAGES", "android.permission.MANAGE_EXTERNAL_STORAGE"}), permissions
assert application.get(android + "debuggable", "false") == "false", "Debuggable release"
assert [e.get(android + "name") for e in application.findall("activity")] == ["dev.pluto.launcher.MainActivity"], "Unexpected fixture activity"
assert all(e.get(android + "name") != "androidx.core.content.FileProvider" for e in application.findall("provider")), "Update FileProvider present"
assert int(root.find("uses-sdk").get(android + "targetSdkVersion")) >= 36
native = {}
forbidden = (b"application/vnd.android.package-archive", b"api.github.com/repos/andershfranzen/pluto-launcher/releases", b"MANAGE_UNKNOWN_APP_SOURCES", b"github_updates", b"PerformanceFixtures")
with zipfile.ZipFile(args.bundle) as bundle:
    assert "BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof" in bundle.namelist(), "Missing baseline profile"
    for name in bundle.namelist():
        if name.endswith(".dex"):
            data = bundle.read(name)
            assert not any(term in data for term in forbidden), "Updater or fixture code remains in " + name
        if not name.endswith(".so"):
            continue
        data = bundle.read(name)
        assert data[:4] == b"\x7fELF", name
        is64 = data[4] == 2
        order = "<" if data[5] == 1 else ">"
        offset = struct.unpack_from(order + ("Q" if is64 else "I"), data, 32 if is64 else 28)[0]
        size, count = struct.unpack_from(order + "HH", data, 54 if is64 else 42)
        alignment = [struct.unpack_from(order + ("Q" if is64 else "I"), data, offset + i * size + (48 if is64 else 28))[0]
                     for i in range(count) if struct.unpack_from(order + "I", data, offset + i * size)[0] == 1]
        assert alignment and all(n >= 16384 for n in alignment), (name, alignment)
        native[name] = alignment
print(json.dumps({
    "bundle": str(args.bundle), "sha256": hashlib.sha256(args.bundle.read_bytes()).hexdigest(),
    "package": root.get("package"), "versionName": root.get(android + "versionName"),
    "versionCode": root.get(android + "versionCode"), "permissions": sorted(permissions),
    "baselineProfile": True, "updaterAbsent": True, "nativeLoadAlignment": native,
}, indent=2))
