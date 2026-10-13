# Localisation

Pluto mirrors right-to-left layouts (`android:supportsRtl="true"`). The language list for
Android 13+'s per-app language picker is ready in `app/src/main/res/xml/locales_config.xml`,
but the manifest does not reference it yet: offering Danish or German in that picker while
almost every screen stays English would be a broken promise. Add
`android:localeConfig="@xml/locales_config"` to `<application>` once the UI text below has
been extracted. Until then, the translated strings apply when the whole phone uses that language.

## What is translated today

Only the strings that already live in `app/src/main/res/values/strings.xml`:

| Key | Where it is read |
| --- | --- |
| `app_name` | Launcher label. Never translate: it is the product name. |
| `notification_dots_service_label` | Android's notification-access list. |
| `screen_lock_service_label` | Accessibility services list. |
| `screen_lock_service_description` | The sentence a user reads before enabling "Pluto screen lock". |

Shipped: English (default), Danish, German, French and Swedish. Every language in
`app/src/main/res/xml/locales_config.xml` must have a `values-<lang>` folder, and every
`values-<lang>` folder must be listed there — Android's lint check enforces both directions.

## Adding a language

1. Copy `app/src/main/res/values/strings.xml` to `app/src/main/res/values-<lang>/strings.xml`
   and translate the values (leave `app_name` as `Pluto`).
2. Add `<locale android:name="<lang>"/>` to `app/src/main/res/xml/locales_config.xml`.
3. Check it on a device without changing the whole system language:

   ```sh
   adb shell cmd locale set-app-locales dev.pluto.launcher --locales <lang>
   adb shell cmd locale set-app-locales dev.pluto.launcher --locales ""   # back to the system
   ```

Right-to-left languages (`ar`, `he`, `fa`, `ur`) need no layout work: the launcher already
mirrors for them, including console mode. Check the screen-lock description and the
notification-access row, which are the two places these strings appear.

## The real work: the UI text is in Kotlin

Nearly all of Pluto's text is inline in Kotlin — `SettingsText` and `OverlayStrings` hold a
few hundred constants, and layouts contain literals such as `"Finding games…"`. Until those
are extracted into `strings.xml`, a new language changes only the four rows above.

The extraction is deliberately not half-done. When it happens, it should be one pass that
moves `SettingsText`, `OverlayStrings` and the inline literals into resources and switches
them to `stringResource`, keeping the wording identical so the tests below keep passing.
Rough order of value: settings screens, onboarding, the management screens, console chrome,
then small labels.

Two constraints worth knowing before that pass:

- **Format placeholders.** Several strings interpolate values (`"${n} games"`); those become
  `plurals` or `%1$d`-style resources so translators can reorder them.
- **The benchmark journeys assert on English text** (`By.text("Finish")`, `By.text("Skip")`
  in `baselineprofile/src/.../Journeys.kt`). The benchmarks run on the emulator's default
  locale, so they are unaffected, but a translation of those words must not be assumed in
  the journeys.
