"""Regressions missed by the original translation checks."""

import unittest
import xml.etree.ElementTree as ET

from verify_drafts import validate_resources


class ResourceValidationTest(unittest.TestCase):
    def test_adjacent_entry_leak_without_format_arguments(self):
        source = ET.fromstring(
            '<resources><string name="required">Required</string></resources>'
        )
        broken = ET.fromstring(
            '<resources><string name="required">Pakollinen\n[0020]] Esimerkiksi: 25 tai 25,50</string></resources>'
        )
        _, issues = validate_resources(source, broken, "fi")
        self.assertTrue(any("batch marker" in issue.reason for issue in issues))
        self.assertTrue(any("adjacent-entry leak" in issue.reason for issue in issues))

    def test_unmarked_adjacent_text_is_still_detected(self):
        source = ET.fromstring(
            '<resources><string name="time">15 minutes later</string></resources>'
        )
        broken = ET.fromstring(
            '<resources><string name="time">15 minutter senere\nelev eller gruppe</string></resources>'
        )
        _, issues = validate_resources(source, broken, "da")
        self.assertTrue(any("adjacent-entry leak" in issue.reason for issue in issues))

    def test_missing_arabic_plural_categories(self):
        text = '<resources><plurals name="lessons"><item quantity="one">%1$d lesson</item><item quantity="other">%1$d lessons</item></plurals></resources>'
        _, issues = validate_resources(ET.fromstring(text), ET.fromstring(text), "ar")
        self.assertTrue(any("plural categories" in issue.reason for issue in issues))

    def test_reordered_arguments_are_allowed(self):
        source = ET.fromstring(
            '<resources><string name="count">%1$d of %2$d</string></resources>'
        )
        translated = ET.fromstring(
            '<resources><string name="count">%2$d명 중 %1$d명</string></resources>'
        )
        _, issues = validate_resources(source, translated, "ko")
        self.assertEqual(issues, [])

    def test_extra_argument_is_detected(self):
        source = ET.fromstring(
            '<resources><string name="count">%1$d</string></resources>'
        )
        translated = ET.fromstring(
            '<resources><string name="count">%1$d %1$d</string></resources>'
        )
        _, issues = validate_resources(source, translated, "ko")
        self.assertTrue(any("format arguments" in issue.reason for issue in issues))

    def test_spanish_todo_is_a_word(self):
        source = ET.fromstring(
            '<resources><string name="show_all">Show all</string></resources>'
        )
        translated = ET.fromstring(
            '<resources><string name="show_all">Mostrar todo</string></resources>'
        )
        _, issues = validate_resources(source, translated, "es")
        self.assertEqual(issues, [])

    def test_slavic_one_can_include_count_needed_for_21(self):
        source = ET.fromstring(
            '<resources><plurals name="copies"><item quantity="one">Last copy</item><item quantity="other">Last %1$d copies</item></plurals></resources>'
        )
        translated = ET.fromstring(
            '<resources><plurals name="copies"><item quantity="one">%1$d копия</item><item quantity="few">%1$d копии</item><item quantity="many">%1$d копий</item><item quantity="other">%1$d копии</item></plurals></resources>'
        )
        _, issues = validate_resources(source, translated, "ru")
        self.assertEqual(issues, [])


if __name__ == "__main__":
    unittest.main()
