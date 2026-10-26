"""The native-review sheets show what a reviewer needs and nothing they can't act on."""

import unittest
import xml.etree.ElementTree as ET

from native_review import sheet

SOURCE = ET.fromstring(
    "<resources>"
    '<string name="stat_debt">Owes</string>'
    '<string name="today_title">Today | now</string>'
    '<plurals name="hours_short"><item quantity="one">%1$d h</item><item quantity="other">%1$d h</item></plurals>'
    '<string name="today_brand" translatable="false">Klas</string>'
    '<string name="backup_title">Backup</string>'
    "</resources>"
)
ARABIC = ET.fromstring(
    "<resources>"
    '<string name="stat_debt">عويس</string>'
    '<string name="today_title">اليوم</string>'
    '<plurals name="hours_short"><item quantity="one">ساعة</item><item quantity="other">%1$d س</item></plurals>'
    '<string name="backup_title">نسخ احتياطي</string>'
    "</resources>"
)


class SheetTest(unittest.TestCase):
    def setUp(self):
        self.text = sheet("ar", "Arabic", SOURCE, ARABIC)

    def test_a_flagged_string_is_settled_first(self):
        first = self.text.index("Settle these first")
        self.assertLess(first, self.text.index("## Today"))
        self.assertIn("| `stat_debt` | Owes | عويس | a transliteration", self.text)

    def test_only_the_busiest_screens_are_listed(self):
        self.assertIn("`today_title`", self.text)
        self.assertNotIn("`backup_title`", self.text)
        self.assertNotIn("`today_brand`", self.text, "an untranslatable string has nothing to review")

    def test_cells_survive_a_table(self):
        self.assertIn("Today \\| now", self.text)
        self.assertIn("*one:* ساعة · *other:* %1$d س", self.text)

    def test_a_language_with_nothing_flagged_has_no_such_section(self):
        self.assertNotIn("Settle these first", sheet("de", "German", SOURCE, ARABIC))


if __name__ == "__main__":
    unittest.main()
