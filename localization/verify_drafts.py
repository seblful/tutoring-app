"""Check translation resource contracts; this does not certify linguistic accuracy."""

from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from dataclasses import asdict, dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORMAT = re.compile(r"%(?:\d+\$)?[-#+ 0,(<]*\d*(?:\.\d+)?[tT]?[a-zA-Z%]")
MARKER = re.compile(r"ZXQ|QXZ|\[{1,2}\s*\d{3,}\s*\]{1,2}|(?-i:\bTODO:)", re.IGNORECASE)
QUANTITIES = {
    "en": {"one", "other"},
    "ru": {"one", "few", "many", "other"},
    "uk": {"one", "few", "many", "other"},
    "pl": {"one", "few", "many", "other"},
    "be": {"one", "few", "many", "other"},
    "ar": {"zero", "one", "two", "few", "many", "other"},
    "fr": {"one", "many", "other"},
    "es": {"one", "many", "other"},
    "it": {"one", "many", "other"},
    "pt-BR": {"one", "many", "other"},
    "pt-PT": {"one", "many", "other"},
    **{
        tag: {"one", "other"}
        for tag in ("de", "tr", "nl", "el", "fi", "da", "nb", "sv", "kk", "hi")
    },
    **{tag: {"other"} for tag in ("zh-CN", "zh-TW", "ja", "ko")},
}
NO_BREAK_JOINERS = (r"\u00A0", r"\u2060", "\u00a0", "\u2060")


@dataclass(frozen=True)
class Issue:
    locale: str
    resource: str
    reason: str


def unescaped_quote(text: str) -> bool:
    for index, character in enumerate(text):
        if character not in "\"'":
            continue
        backslashes = 0
        position = index - 1
        while position >= 0 and text[position] == "\\":
            backslashes += 1
            position -= 1
        if backslashes % 2 == 0:
            return True
    return False


def validate_resources(
    reference: ET.Element,
    translation: ET.Element,
    locale: str,
) -> tuple[int, list[Issue]]:
    """Check every leaf, preserving a plural's quantity-specific argument contract."""
    issues: list[Issue] = []
    source = {item.attrib["name"]: item for item in reference}
    names = [item.get("name") for item in translation]
    duplicates = sorted(
        str(name) for name, count in Counter(names).items() if count > 1
    )
    if duplicates:
        issues.append(Issue(locale, "resources", f"duplicate names: {duplicates}"))
    for name in sorted(source.keys() - set(names)):
        issues.append(Issue(locale, name, "missing resource"))
    values = 0
    for resource in translation:
        name = resource.get("name", "<unnamed>")
        original = source.get(name)
        if original is None:
            issues.append(Issue(locale, name, "unknown resource"))
            continue
        if resource.tag != original.tag:
            issues.append(Issue(locale, name, "resource type differs from English"))
            continue
        if resource.tag == "plurals":
            quantities = [item.get("quantity") for item in resource]
            if set(quantities) != QUANTITIES[locale] or len(quantities) != len(
                set(quantities)
            ):
                issues.append(
                    Issue(locale, name, "incorrect or duplicate plural categories")
                )
            by_quantity = {item.get("quantity"): item for item in original}
            pairs = [
                (item, by_quantity["one" if item.get("quantity") == "one" else "other"])
                for item in resource
            ]
        elif resource.tag == "string-array":
            if len(resource) != len(original):
                issues.append(Issue(locale, name, "array length differs from English"))
            pairs = list(zip(resource, original))
        elif resource.tag == "string":
            pairs = [(resource, original)]
        else:
            issues.append(Issue(locale, name, "unsupported resource type"))
            continue
        for index, (item, source_item) in enumerate(pairs):
            values += 1
            label = f"{name}[{item.get('quantity', str(index))}]"
            text = item.text or ""
            source_text = source_item.text or ""
            if not text.strip():
                issues.append(Issue(locale, label, "empty translation"))
            if MARKER.search(text):
                issues.append(
                    Issue(
                        locale,
                        label,
                        "translation batch marker or TODO leaked into text",
                    )
                )
            if (
                ("\n" in text or "\r" in text)
                and "\n" not in source_text
                and "\r" not in source_text
            ):
                issues.append(
                    Issue(
                        locale,
                        label,
                        "unexpected literal line break; possible adjacent-entry leak",
                    )
                )
            expected_arguments = Counter(FORMAT.findall(source_text))
            actual_arguments = Counter(FORMAT.findall(text))
            # English may say "its last copy", while Russian needs the number for 21, 31, ...
            optional_plural_count = (
                resource.tag == "plurals"
                and not expected_arguments
                and actual_arguments
                == Counter(FORMAT.findall(by_quantity["other"].text or ""))
                and all(
                    re.fullmatch(r"%(?:\d+\$)?d", token) for token in actual_arguments
                )
            )
            if actual_arguments != expected_arguments and not optional_plural_count:
                issues.append(
                    Issue(locale, label, "format arguments differ from English")
                )
            if Counter(re.findall(r"\\[ntr]", text)) != Counter(
                re.findall(r"\\[ntr]", source_text)
            ):
                issues.append(
                    Issue(
                        locale,
                        label,
                        "escaped line-break/tab directives differ from English",
                    )
                )
            if unescaped_quote(text):
                issues.append(Issue(locale, label, "unescaped Android quote"))
            if (
                name in {"price_per_hour", "price_per_hour_per_student"}
                and "/" in text
                and not any(f"/{joiner}" in text for joiner in NO_BREAK_JOINERS[1::2])
            ):
                issues.append(Issue(locale, label, "hourly unit may wrap after slash"))
            if name == "students_count" and not any(
                f"%1$d{joiner}" in text or f"{joiner}%1$d" in text
                for joiner in NO_BREAK_JOINERS
            ):
                issues.append(
                    Issue(locale, label, "student count may split from label")
                )
            if len(item) or len(source_item):
                issues.append(
                    Issue(locale, label, "inline markup requires explicit validation")
                )
            if (
                original.get("translatable") == "false" or name == "app_name"
            ) and text != source_text:
                issues.append(Issue(locale, label, "nontranslatable value changed"))
            if name.endswith("_filename"):
                suffix = re.search(r"\.[a-z]+$", source_text)
                if suffix and not text.endswith(suffix[0]):
                    issues.append(Issue(locale, label, "filename extension changed"))
    return values, issues


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--report", type=Path, help="Write the technical verification report as JSON"
    )
    arguments = parser.parse_args()
    manifest_path = ROOT / "localization/drafts/manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    reference = ET.parse(ROOT / manifest["source"]).getroot()
    files = {
        "en": ROOT / manifest["source"],
        "ru": ROOT / "app/src/main/res/values-ru/strings.xml",
        **{
            draft["tag"]: manifest_path.parent / draft["path"]
            for draft in manifest["drafts"]
        },
    }
    issues: list[Issue] = []
    if (
        len(files) != manifest["totalLanguageVariants"]
        or set(files) != QUANTITIES.keys()
    ):
        issues.append(
            Issue("manifest", "languages", "language inventory is inconsistent")
        )
    declared = {
        path.resolve() for tag, path in files.items() if tag not in {"en", "ru"}
    }
    actual = {path.resolve() for path in manifest_path.parent.glob("*/strings.xml")}
    if declared != actual:
        issues.append(
            Issue("manifest", "files", "declared and actual draft files disagree")
        )
    summaries = []
    for locale, path in files.items():
        if locale not in QUANTITIES:
            issues.append(Issue(locale, "resources", "plural rules are not declared"))
            continue
        try:
            translation = ET.parse(path).getroot()
        except (OSError, ET.ParseError) as error:
            issues.append(Issue(locale, "resources", str(error)))
            continue
        values, failures = validate_resources(reference, translation, locale)
        issues.extend(failures)
        summaries.append(
            {
                "locale": locale,
                "resources": len(translation),
                "values": values,
                "errors": len(failures),
            }
        )
        print(
            f"{locale}: {len(translation)} resources, {values} values, {len(failures)} errors"
        )
    report = {
        "status": "technical_checks_failed" if issues else "technical_checks_passed",
        "scope": "Every resource in English, Russian and all 23 translation drafts.",
        "linguisticCertification": False,
        "limitation": "These checks do not establish fluent wording, semantic equivalence, or visual fit. Native-speaker and screen review remain necessary.",
        "checks": [
            "inventory",
            "unique resource names",
            "resource types",
            "array lengths",
            "plural categories",
            "format argument multiplicity",
            "Android quotes",
            "escaped whitespace",
            "nonbreaking student counts and hourly units",
            "batch text leakage",
            "nontranslatable values",
            "filename extensions",
        ],
        "languages": summaries,
        "issues": [asdict(issue) for issue in issues],
    }
    if arguments.report:
        arguments.report.write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
    for issue in issues:
        print(f"{issue.locale}: {issue.resource}: {issue.reason}", file=sys.stderr)
    return 1 if issues else 0


if __name__ == "__main__":
    raise SystemExit(main())
