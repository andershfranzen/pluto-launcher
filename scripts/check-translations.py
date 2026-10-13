#!/usr/bin/env python3
"""Check Pluto's translations against the English string resources, without Gradle.

    scripts/check-translations.py                    every language: report, fail on errors
    scripts/check-translations.py de fr              only values-de and values-fr
    scripts/check-translations.py --strict de        also fail on missing or stale strings
    scripts/check-translations.py --accept de        record de as matching today's English
    scripts/check-translations.py --accept de KEY…   …for these keys only

Errors (exit status 1): files that are not well-formed XML, keys or plurals the English does
not have, format placeholders (%1$s, %2$d …) that differ from the English, plurals without an
"other" form, and unescaped apostrophes. These break the build or the text at runtime.

Warnings (reported; fatal only with --strict): strings not translated yet, which Android shows
in English, and stale translations. A translation is stale when the English text changed after
it was written. l10n/fingerprints/<lang>.tsv records, per key, a hash of the English the
translation was made from; after updating a translation, run --accept for that language (and
those keys) so the new English is recorded. A translated key with no recorded hash counts as
stale too, so a new translation is accepted deliberately.
"""
import argparse
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PLACEHOLDER = re.compile(r"%(?:(\d+)\$)?([-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%])")


def placeholders(text: str) -> list[str]:
    found = []
    for index, match in enumerate(PLACEHOLDER.finditer(text or "")):
        if match.group(2) == "%":
            continue
        position = match.group(1) or str(index + 1)
        found.append(f"{position}${match.group(2)[-1]}")
    return sorted(found)


def text_of(element: ET.Element) -> str:
    return "".join(element.itertext())


def load(folder: Path):
    """name -> text for strings, name -> {quantity: text} for plurals, and file errors."""
    strings, plurals, errors = {}, {}, []
    for file in sorted(folder.glob("strings*.xml")):
        try:
            root = ET.parse(file).getroot()
        except ET.ParseError as error:
            errors.append(f"{file.name}: not well-formed XML ({error})")
            continue
        for element in root:
            name = element.get("name")
            if element.get("translatable") == "false":
                continue
            if element.tag == "string":
                strings[name] = text_of(element)
            elif element.tag == "plurals":
                plurals[name] = {item.get("quantity"): text_of(item) for item in element.findall("item")}
        for line_number, line in enumerate(file.read_text(encoding="utf-8").splitlines(), 1):
            for value in re.findall(r">([^<]*)<", line):
                if re.search(r"(?<!\\)'", value):
                    errors.append(f"{file.name}:{line_number}: unescaped apostrophe (write \\')")
    return strings, plurals, errors


def fingerprint(value) -> str:
    """Hash of one English source: a string, or a plural's quantity → text map."""
    source = value if isinstance(value, str) else json.dumps(value, sort_keys=True, ensure_ascii=False)
    return hashlib.sha1(source.encode("utf-8")).hexdigest()[:12]


def english_sources(base) -> dict:
    strings, plurals, _ = base
    return {**{k: fingerprint(v) for k, v in strings.items()}, **{k: fingerprint(v) for k, v in plurals.items()}}


def read_fingerprints(path: Path) -> dict:
    if not path.is_file():
        return {}
    rows = (line.split("\t") for line in path.read_text(encoding="utf-8").splitlines() if "\t" in line)
    return {key: digest for key, digest in rows}


def write_fingerprints(path: Path, recorded: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(f"{key}\t{recorded[key]}\n" for key in sorted(recorded)), encoding="utf-8")


def check(language: str, base, res: Path, fingerprints: Path):
    """(errors, missing keys, stale keys, translated count, total count) for one language."""
    base_strings, base_plurals, _ = base
    strings, plurals, errors = load(res / f"values-{language}")
    for name in sorted(set(strings) - set(base_strings)):
        errors.append(f"unknown string {name} (not in English)")
    for name in sorted(set(plurals) - set(base_plurals)):
        errors.append(f"unknown plurals {name} (not in English)")
    for name, text in sorted(strings.items()):
        if name in base_strings and placeholders(text) != placeholders(base_strings[name]):
            errors.append(f"placeholders differ in {name}: {placeholders(text)} vs English {placeholders(base_strings[name])}")
    for name, items in sorted(plurals.items()):
        if name not in base_plurals:
            continue
        if "other" not in items:
            errors.append(f"plurals {name} has no 'other' form")
        english = placeholders(base_plurals[name].get("other", ""))
        for quantity, text in items.items():
            if placeholders(text) and placeholders(text) != english:
                errors.append(f"placeholders differ in {name}[{quantity}]: {placeholders(text)} vs English {english}")
    missing = sorted((set(base_strings) - set(strings)) | (set(base_plurals) - set(plurals)))
    sources = english_sources(base)
    recorded = read_fingerprints(fingerprints / f"{language}.tsv")
    translated = [k for k in sources if k in strings or k in plurals]
    stale = sorted(k for k in translated if recorded.get(k) != sources[k])
    return errors, missing, stale, len(translated), len(sources)


def accept(language: str, keys: list[str], base, res: Path, fingerprints: Path) -> int:
    strings, plurals, errors = load(res / f"values-{language}")
    if errors:
        print(f"{language}: fix these first:", *errors, sep="\n  ")
        return 1
    sources = english_sources(base)
    translated = {k for k in sources if k in strings or k in plurals}
    unknown = [k for k in keys if k not in translated]
    if unknown:
        print(f"{language}: not translated (or not in English), cannot accept: {', '.join(unknown)}")
        return 1
    path = fingerprints / f"{language}.tsv"
    # Keys the English no longer has are dropped; untranslated keys are never recorded.
    recorded = {k: v for k, v in read_fingerprints(path).items() if k in translated}
    for key in keys or sorted(translated):
        recorded[key] = sources[key]
    write_fingerprints(path, recorded)
    print(f"{language}: recorded {len(keys) if keys else len(translated)} key(s) as matching today's English")
    return 0


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("languages", nargs="*", help="values-<lang> qualifiers; default: all")
    parser.add_argument("--strict", action="store_true", help="also fail on missing or stale strings")
    parser.add_argument("--accept", metavar="LANG", help="record LANG's translations as current (optionally only the KEYs given as further arguments)")
    parser.add_argument("--res", type=Path, default=ROOT / "app/src/main/res", help=argparse.SUPPRESS)
    parser.add_argument("--fingerprints", type=Path, default=ROOT / "l10n/fingerprints", help=argparse.SUPPRESS)
    args = parser.parse_args(argv)

    base = load(args.res / "values")
    if base[2]:
        print("English resources:", *base[2], sep="\n  ")
        return 1
    if args.accept:
        return accept(args.accept, args.languages, base, args.res, args.fingerprints)

    languages = args.languages or sorted(p.name.removeprefix("values-") for p in args.res.glob("values-*")
                                         if list(p.glob("strings*.xml")))
    failed = False
    for language in languages:
        errors, missing, stale, translated, total = check(language, base, args.res, args.fingerprints)
        # Rounded down: 100% only when nothing is missing.
        percent = 100 * translated // total if total else 100
        summary = f"{language}: {percent}% translated ({translated}/{total})"
        if stale:
            summary += f", {len(stale)} stale"
        if errors:
            summary += f", {len(errors)} error(s)"
        print(summary)
        for error in errors[:200]:
            print("  error: " + error)
        if missing:
            print(f"  missing ({len(missing)}; shown in English): " + ", ".join(missing[:30]) + (" …" if len(missing) > 30 else ""))
        if stale:
            print(f"  stale ({len(stale)}; English changed since translated — update, then --accept {language} KEY…): "
                  + ", ".join(stale[:30]) + (" …" if len(stale) > 30 else ""))
        failed = failed or bool(errors) or (args.strict and bool(missing or stale))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
