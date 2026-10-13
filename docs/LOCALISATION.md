# Localisation

Pluto's interface text lives in Android string resources and ships in 13 languages:
English (default), Simplified Chinese, Hindi, Spanish, Arabic, French, Bengali, Portuguese
(Brazil), Russian and Indonesian — the ten most spoken languages by total speakers in
Ethnologue's 2025 ranking — plus Danish, German and Swedish. Android 13+ lists them in the
per-app language picker (Settings → System → Languages → App languages); on older versions
Pluto follows the phone's language. Right-to-left languages are mirrored throughout,
including console mode.

## Where the text is

| File | What it holds |
| --- | --- |
| `app/src/main/res/values/strings.xml` | Labels Android shows for Pluto's services. |
| `values/strings_settings.xml` | Settings, onboarding, controller screen, updates, privacy, crash reports. |
| `values/strings_overlay.xml` | App actions, edit mode, pickers, dialogs, widgets. |
| `values/strings_roms.xml` | ROM tabs, diagnostics, console mode chrome and browse grid. |
| `values/strings_home.xml` | Home, drawer, search, button legends, messages, built-in category names. |

Each `values-<lang>` folder has files with the same names. XML comments above a string say
where it appears and how short it must stay; read them before translating.

In Kotlin, composables use `stringResource`/`pluralStringResource`. Code outside composition
(the `SettingsText`, `OverlayText` and `HomeText` objects, view-model messages) reads through
`dev.pluto.launcher.ui.UiText`. `MainActivity` handles locale changes itself, so it re-attaches
`UiText` and keys the composition on the locale: switching language rebuilds the screen with
the new text.

Built-in categories (All apps, Games, Tools) are stored under their English names. They are
shown translated (`Category.displayName`) until the user renames one, after which the user's
own name is shown.

Not translated on purpose: product and emulator names, the layout backup file format, and the
crash report text that goes into a GitHub issue.

## Changing English text

- **New string:** add it to the English file. Translations can follow later: until they do,
  Android shows that string in English, and the checker reports the language as less than
  100% translated. Release lint only warns about missing translations.
- **Changed meaning:** edit the English. Every translation of that key becomes *stale* and the
  checker lists it, so translators know what to revisit. Fixing a typo that changes nothing for
  translators still marks them stale; accept them unchanged (below).
- **New code:** use `stringResource` in composables; reach for `UiText` only outside composition.

## Translating

1. Read your language's section of [`l10n/glossary.md`](../l10n/glossary.md) and keep to its terms.
2. Translate into `app/src/main/res/values-<lang>/` (same five file names as the English). Keep
   placeholders such as `%1$s` and `%2$d` (reorder them freely), give plurals every quantity the
   language needs, and write apostrophes as `\'`.
3. Check, without a build:

   ```sh
   scripts/check-translations.py <lang>
   ```

   Errors (broken placeholders, unknown keys, plurals without `other`, unescaped apostrophes,
   malformed XML) fail it and CI. Missing and stale strings are listed as warnings with a
   completeness percentage; `--strict` makes them fail too.
4. When the translations are done and match today's English, record that:

   ```sh
   scripts/check-translations.py --accept <lang>            # the whole language
   scripts/check-translations.py --accept <lang> KEY KEY…   # only the strings you updated
   ```

   This writes `l10n/fingerprints/<lang>.tsv`, a hash of the English each translation was made
   from. Commit it with the translation; it is how the next English change is noticed.

To add a language, also add `<locale android:name="<lang>"/>` to
`app/src/main/res/xml/locales_config.xml` and a section to the glossary.

## Looking at it

Debug builds include Android's pseudo-locales. `en-XA` shows every translated string accented
and about a third longer (`[Šéţţîñĝš one two]`), so English that is still hard-coded stands out
and truncation shows up before any real translation exists; `ar-XB` mirrors the layout like
Arabic. Switch Pluto alone, without changing the phone's language:

```sh
adb shell cmd locale set-app-locales dev.pluto.launcher --locales en-XA   # or ar-XB, de, hi…
adb shell cmd locale set-app-locales dev.pluto.launcher --locales ""      # back to the system
```

## Testing constraints

- **The benchmark journeys and instrumented UI tests assert on English text**
  (`By.text("Finish")`, `onNodeWithText("Theme & background")`). They run in the emulator's
  default locale, so they are unaffected, but they must not be run with an app locale set.
- JVM unit tests have no Android resources: domain code returns data (enums, counts) and the
  UI formats it. Calling `UiText` from a JVM test throws.
