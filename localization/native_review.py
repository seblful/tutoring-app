"""Write one review sheet per language for a native speaker: the strings a tutor sees most, beside English."""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "localization" / "native-review"

# Setup, Today, the schedule and a lesson, money, people, and what reaches the tray and home screen.
SCREENS = {
    "onboarding_": "Setup",
    "first_": "First steps",
    "nav_": "Navigation",
    "today_": "Today",
    "action_": "Buttons",
    "schedule_": "Schedule",
    "lesson_": "Lesson",
    "stat_": "Figures",
    "payment_": "Payments",
    "people_": "People",
    "students_": "Students",
    "hours_": "Durations",
    "reminder_": "Reminders",
    "agenda_": "Daily agenda",
    "unfinished_": "Unmarked lessons",
    "widget_": "Widgets",
}

# Flagged while writing the store listing; a reviewer settles each first.
SUSPECTS = {
    "stat_debt": {
        "ar": "a transliteration of “owes”, not a word",
        "zh-CN": "a transliteration of “owes”, not a word",
        "zh-TW": "a transliteration of “owes”, not a word",
        "pt-BR": "reads “I owe”",
        "pt-PT": "reads “I owe”",
        "it": "reads “I owe”",
        "ko": "awkward",
    },
    "lesson_kind_individual": {
        "uk": "“natural person”, a legal term",
        "es": "“an individual (person)”, not a one-to-one lesson",
        "pt-BR": "“an individual (person)”, not a one-to-one lesson",
        "pt-PT": "“an individual (person)”, not a one-to-one lesson",
        "ar": "“an individual (person)”, not a one-to-one lesson",
        "fi": "“an individual (person)”, not a one-to-one lesson",
    },
}


def texts(resource: ET.Element) -> str:
    """A resource as one table cell: a string, or each plural form and array item labelled."""
    if resource.tag == "string":
        return cell(resource.text or "")
    return " · ".join(
        f"*{item.get('quantity', str(index))}:* {cell(item.text or '')}"
        for index, item in enumerate(resource)
    )


def cell(text: str) -> str:
    return text.replace("\\n", " ⏎ ").replace("\\'", "'").replace('\\"', '"').replace("|", "\\|").strip()


def screen_of(name: str) -> str | None:
    return next((label for prefix, label in SCREENS.items() if name.startswith(prefix)), None)


def sheet(tag: str, language: str, source: ET.Element, translation: ET.Element) -> str:
    english = {item.get("name"): item for item in source if item.get("translatable") != "false"}
    translated = {item.get("name"): item for item in translation}
    lines = [
        f"# {language} ({tag}) — native review",
        "",
        "Read each line as it would appear on a phone. Mark **OK**, or write what a tutor would say instead.",
        "`%1$s`, `%1$d` and the like are filled in by the app (a name, a number) and must stay.",
        "",
    ]
    suspects = [
        (name, notes[tag]) for name, notes in SUSPECTS.items()
        if tag in notes and name in english and name in translated
    ]
    if suspects:
        lines += ["## Settle these first", "", "| Key | English | Now | Why | Suggestion |", "|---|---|---|---|---|"]
        for name, why in suspects:
            lines.append(f"| `{name}` | {texts(english[name])} | {texts(translated[name])} | {why} | |")
        lines.append("")
    by_screen: dict[str, list[str]] = {}
    for name, resource in english.items():
        screen = screen_of(name)
        if screen is None or name not in translated:
            continue
        by_screen.setdefault(screen, []).append(
            f"| `{name}` | {texts(resource)} | {texts(translated[name])} | |"
        )
    for screen in SCREENS.values():
        rows = by_screen.get(screen)
        if not rows:
            continue
        lines += [f"## {screen}", "", "| Key | English | Now | OK / suggestion |", "|---|---|---|---|", *rows, ""]
    return "\n".join(lines)


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=OUT)
    args = parser.parse_args(argv)
    manifest = json.loads((ROOT / "localization" / "manifest.json").read_text(encoding="utf-8"))
    source = ET.parse(ROOT / manifest["source"]).getroot()
    args.out.mkdir(parents=True, exist_ok=True)
    for entry in manifest["languages"]:
        if entry["tag"] in ("en", "ru"):
            continue  # the source and the owner's own language
        translation = ET.parse(ROOT / entry["path"]).getroot()
        text = sheet(entry["tag"], entry["language"], source, translation)
        (args.out / f"{entry['tag']}.md").write_text(text + "\n", encoding="utf-8", newline="\n")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
