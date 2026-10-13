#!/usr/bin/env python3
"""Check Pluto's translations against the English string resources, without Gradle.

    scripts/check-translations.py              every values-<lang> folder
    scripts/check-translations.py de fr        only these folders (values-de, values-fr)

For each language it reports keys missing or unknown compared with app/src/main/res/values,
format placeholders that differ from the English (%1$s, %2$d …), plurals without an "other"
form, unescaped apostrophes, and files that are not well-formed XML. Exit status is 1 when
anything is wrong, so it can run in CI. Android lint checks some of this too, but only after
a full build.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "app/src/main/res"
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
    """strings: name -> text, plurals: name -> {quantity: text}, plus parse errors."""
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
        raw = file.read_text(encoding="utf-8")
        for line_number, line in enumerate(raw.splitlines(), 1):
            for value in re.findall(r">([^<]*)<", line):
                if re.search(r"(?<!\\)'", value):
                    errors.append(f"{file.name}:{line_number}: unescaped apostrophe (write \\')")
    return strings, plurals, errors


def check(language: str, base) -> list[str]:
    base_strings, base_plurals, _ = base
    strings, plurals, problems = load(RES / f"values-{language}")
    for name in sorted(set(base_strings) - set(strings)):
        problems.append(f"missing string {name}")
    for name in sorted(set(base_plurals) - set(plurals)):
        problems.append(f"missing plurals {name}")
    for name in sorted(set(strings) - set(base_strings)):
        problems.append(f"unknown string {name} (not in English)")
    for name in sorted(set(plurals) - set(base_plurals)):
        problems.append(f"unknown plurals {name} (not in English)")
    for name, text in strings.items():
        if name in base_strings and placeholders(text) != placeholders(base_strings[name]):
            problems.append(f"placeholders differ in {name}: {placeholders(text)} vs English {placeholders(base_strings[name])}")
    for name, items in plurals.items():
        if name not in base_plurals:
            continue
        if "other" not in items:
            problems.append(f"plurals {name} has no 'other' form")
        english = placeholders(base_plurals[name].get("other", ""))
        for quantity, text in items.items():
            if placeholders(text) and placeholders(text) != english:
                problems.append(f"placeholders differ in {name}[{quantity}]: {placeholders(text)} vs English {english}")
    return problems


def main(args: list[str]) -> int:
    base = load(RES / "values")
    if base[2]:
        print("English resources:", *base[2], sep="\n  ")
        return 1
    languages = args or sorted(p.name.removeprefix("values-") for p in RES.glob("values-*")
                               if list(p.glob("strings*.xml")))
    failed = False
    for language in languages:
        problems = check(language, base)
        status = "ok" if not problems else f"{len(problems)} problem(s)"
        print(f"{language}: {status}")
        for problem in problems[:200]:
            print("  " + problem)
        failed = failed or bool(problems)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
