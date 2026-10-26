# Native review

One sheet per translated language (`<tag>.md`) with the ~200 strings a tutor sees most often: setup, Today,
the schedule, lessons, payments, people, reminders and widgets. Russian is the owner's own and is not
included. Strings flagged as likely wrong come first under **Settle these first**.

Give each reviewer their language's sheet and ask them to check:

- **Meaning:** does it say what the English says, in the context of a private tutor's lessons and money?
- **Words a tutor uses:** "lesson", "student", "owes", "paid" as people say them, not as a dictionary does.
- **Length:** buttons, tabs and figure labels sit in narrow spaces; shorter is better when it means the same.
- **Counts:** for plural rows (*one:*, *few:*, *other:* …), try 1, 2, 5, 21 and 0 with each form.
- **Placeholders:** `%1$s`, `%2$d` and the like must stay exactly as they are; their order may change.

Copy the agreed wording into `app/src/main/res/values-<tag>/strings.xml`, then run
`python -X utf8 localization/verify_translations.py --report localization/verification.json`.

Regenerate the sheets after strings change: `python -X utf8 localization/native_review.py`.
