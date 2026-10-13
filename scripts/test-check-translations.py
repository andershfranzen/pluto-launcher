#!/usr/bin/env python3
"""Self-test for check-translations.py: run it against a crafted resource tree.

Runs with plain python3, no dependencies, so CI can call it next to the other script tests.
"""
import subprocess
import sys
import tempfile
from pathlib import Path

CHECKER = Path(__file__).with_name("check-translations.py")

ENGLISH = """<resources>
    <string name="app_name" translatable="false">Pluto</string>
    <string name="greeting">Hello</string>
    <string name="games">%1$d of %2$s</string>
    <plurals name="apps"><item quantity="one">%d app</item><item quantity="other">%d apps</item></plurals>
</resources>"""


def write(res: Path, folder: str, body: str) -> None:
    (res / folder).mkdir(parents=True, exist_ok=True)
    (res / folder / "strings.xml").write_text(body, encoding="utf-8")


def run(res: Path, prints: Path, *args):
    return subprocess.run([sys.executable, str(CHECKER), "--res", str(res), "--fingerprints", str(prints), *args],
                          capture_output=True, text=True)


def expect(condition: bool, message: str, result=None) -> None:
    if not condition:
        detail = f"\n--- output ---\n{result.stdout}{result.stderr}" if result else ""
        sys.exit(f"FAIL: {message}{detail}")


def main():
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        res, prints = root / "res", root / "fingerprints"
        write(res, "values", ENGLISH)

        # A complete translation nobody has accepted yet passes, with every key reported stale.
        write(res, "values-de", """<resources>
            <string name="greeting">Hallo</string>
            <string name="games">%1$d von %2$s</string>
            <plurals name="apps"><item quantity="one">%d App</item><item quantity="other">%d Apps</item></plurals>
        </resources>""")
        result = run(res, prints, "de")
        expect(result.returncode == 0 and "100% translated" in result.stdout and "3 stale" in result.stdout,
               "a complete but never-accepted translation passes, reported as stale", result)
        expect(run(res, prints, "--strict", "de").returncode == 1, "--strict fails on stale strings")

        expect(run(res, prints, "--accept", "de").returncode == 0, "accepting a clean language works")
        result = run(res, prints, "--strict", "de")
        expect(result.returncode == 0 and "stale" not in result.stdout, "accepted translations are current", result)

        # The English changes: only that key goes stale, and accepting just it clears it.
        write(res, "values", ENGLISH.replace(">Hello<", ">Hello there<"))
        result = run(res, prints, "de")
        expect(result.returncode == 0 and "1 stale" in result.stdout and "greeting" in result.stdout,
               "an English change marks only that translation stale", result)
        expect(run(res, prints, "--accept", "de", "greeting").returncode == 0, "accepting one key works")
        expect("stale" not in run(res, prints, "de").stdout, "the accepted key is current again")

        # Missing strings fall back to English: a warning with a percentage, fatal only when strict.
        write(res, "values-fr", """<resources>
            <string name="greeting">Bonjour</string>
        </resources>""")
        result = run(res, prints, "fr")
        expect(result.returncode == 0 and "33% translated (1/3)" in result.stdout and "missing (2" in result.stdout,
               "an incomplete language reports its completeness without failing", result)
        expect(run(res, prints, "--strict", "fr").returncode == 1, "--strict fails on missing strings")
        expect(run(res, prints, "--accept", "fr", "games").returncode == 1, "an untranslated key cannot be accepted")
        many = "".join(f'<string name="s{i}">x</string>' for i in range(200))
        write(res, "values", ENGLISH.replace("</resources>", many + "</resources>"))
        write(res, "values-nl", "<resources>" + "".join(f'<string name="s{i}">y</string>' for i in range(200))
              + '<string name="greeting">Hallo</string><string name="games">%1$d van %2$s</string></resources>')
        expect("99% translated (202/203)" in run(res, prints, "nl").stdout, "an almost complete language never shows 100%")
        write(res, "values", ENGLISH.replace(">Hello<", ">Hello there<"))

        # Errors always fail: placeholders, unknown keys, plurals without other, apostrophes, bad XML.
        write(res, "values-es", """<resources>
            <string name="greeting">L'hola</string>
            <string name="games">%1$s de %2$s</string>
            <string name="bogus">x</string>
            <plurals name="apps"><item quantity="one">%d app</item></plurals>
        </resources>""")
        result = run(res, prints, "es")
        expect(result.returncode == 1, "errors fail the check", result)
        for fragment in ("unescaped apostrophe", "placeholders differ in games", "unknown string bogus", "no 'other' form"):
            expect(fragment in result.stdout, f"reports: {fragment}", result)
        expect(run(res, prints, "--accept", "es").returncode == 1, "a language with errors cannot be accepted")

        write(res, "values-it", "<resources><string name=\"greeting\">Ciao</resources>")
        expect("not well-formed" in run(res, prints, "it").stdout, "malformed XML is reported")
    print("check-translations: all self-tests passed")


if __name__ == "__main__":
    main()
