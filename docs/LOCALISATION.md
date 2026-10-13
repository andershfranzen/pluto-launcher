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

## Adding a language

1. Copy the five `values/strings*.xml` files to `app/src/main/res/values-<lang>/` and
   translate every string and plural. Keep placeholders such as `%1$s` and `%2$d` (reorder them
   freely), give plurals every quantity the language needs, and write apostrophes as `\'`.
2. Add `<locale android:name="<lang>"/>` to `app/src/main/res/xml/locales_config.xml`.
3. Check the files without a build:

   ```sh
   scripts/check-translations.py <lang>
   ```

   It reports missing or unknown keys, placeholder mismatches, plurals without `other` and
   unescaped apostrophes. CI runs it for every language.
4. Look at it on a device without changing the whole system language:

   ```sh
   adb shell cmd locale set-app-locales dev.pluto.launcher --locales <lang>
   adb shell cmd locale set-app-locales dev.pluto.launcher --locales ""   # back to the system
   ```

Release lint fails when a string is missing from any language, so a new English string needs
its translations in the same change (or a follow-up before the next release).

## Testing constraints

- **The benchmark journeys and instrumented UI tests assert on English text**
  (`By.text("Finish")`, `onNodeWithText("Theme & background")`). They run in the emulator's
  default locale, so they are unaffected, but they must not be run with an app locale set.
- JVM unit tests have no Android resources: domain code returns data (enums, counts) and the
  UI formats it. Calling `UiText` from a JVM test throws.
