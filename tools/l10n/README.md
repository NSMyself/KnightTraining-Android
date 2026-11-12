# Translations

`values-*/strings.xml` are generated; never edit them by hand.

```sh
python3 -I tools/l10n/build.py \
  ../oathbreaker/KnightTraining/Localizable.xcstrings \
  ../oathbreaker/Core/Sources/Core/Resources/Localizable.xcstrings \
  app/src/main/res tools/l10n/drafts.json --missing /tmp/missing.json
```

1. A string whose English matches an iOS catalog entry reuses that entry's translations, with iOS
   format specifiers rewritten to the Android string's own (`%1$d`, `%s`).
2. Everything else comes from `drafts.json` (`name -> language -> text`, or `-> {one, other}` for
   plurals). A draft also overrides an iOS match.
3. `--missing` lists what's left. Add drafts for every name it reports, following the locked
   glossaries and register in the iOS repo's `docs/localization.md`, then rerun.

Strings marked `translatable="false"` (brand names, number formats) are skipped. `many` is filled
from `other` for es, fr, it and pt, where CLDR's `many` only covers huge round numbers.
