# Google Play publication

Pluto's first Play candidate is **0.1.7 (version code 8)**, package `dev.pluto.launcher`,
Android 11+, target API 36. It is not published until Play Console accepts the release.

## Distribution and build

The default build remains the GitHub distribution, with its existing self-updater and
signing configuration. Public GitHub APKs must still be signed with the preserved
`~/.config/.android/pluto-release.keystore` certificate; this differs from the machine's
default debug key. `-PplutoDistribution=play` selects the Play source set while keeping
the existing Gradle task names and profile generation workflow. The Play source set
contains no APK downloader or installer. Its release and debug manifests remove
`INTERNET`, `REQUEST_INSTALL_PACKAGES` and the update FileProvider. Onboarding skips
GitHub consent even when older settings contain automatic-update preferences.
The General settings page opens Pluto's Play listing for updates.

```sh
JAVA_HOME=/home/a5/.local/share/mise/installs/java/17 \
ANDROID_HOME=/home/a5/Android/Sdk \
./gradlew -PplutoDistribution=play :app:bundleRelease :app:lintRelease :app:testDebugUnitTest --no-daemon
```

Private signing properties default to
`~/.config/pluto-launcher/play-signing.properties`. Override that path with
`PLUTO_PLAY_SIGNING_PROPERTIES`. The properties are `storeFile`, `storePassword`,
`keyAlias` and `keyPassword`. The generated upload key and properties are mode 0600,
outside the repository. Keep a private backup of both. `upload-certificate.pem` is
the public certificate, suitable for Play Console. The final bundle is
`app/build/outputs/bundle/release/app-release.aab`.

To install a debug test build over the emulator's existing GitHub-signed app without
losing its layout, add `-PplutoDebugKey=/home/a5/.config/.android/pluto-release.keystore`
to both debug APK and instrumented-test APK builds. This does not alter the Play
upload key. Never use connected tests on the user's phone.

Audit the final package with
`python scripts/verify-play-bundle.py app/build/outputs/bundle/release/app-release.aab --bundletool /path/to/bundletool-all.jar`.

Enroll in Play App Signing. Google controls the app signing certificate used for
installs; the local upload certificate authenticates bundle uploads. The current
GitHub APK uses the old Android debug certificate, which is preserved. Do not
uninstall a user's existing launcher to resolve a signature mismatch: that erases
their layout. Cross-distribution updates cannot be promised until the final app
signing certificate is known.

## Store materials

`listing-en-US.json` contains the title, descriptions, support email, URLs and
release notes. Use the Personalization category. The public support contact is
**anders@afive.dev**. The developer Google account is **anders@haasfranzen.dk**.

- Icon: `graphics/icon.png`, square 512×512 PNG; Google supplies the corner mask.
- Feature graphic: `graphics/feature-graphic.png`, 1024×500 RGB PNG.
- Screenshots: seven RGB PNGs in `graphics/`, each 1440×2560 or 2560×1440.
- Suggested screenshot order: phone, drawer, landscape, console, menu,
  rom-tab-setup, rom-default-emulator.
- Privacy URL: <https://andershfranzen.github.io/pluto-launcher/privacy/>.

Screenshot sources are actual 0.1.5 app captures, whose main interfaces are unchanged
in 0.1.7. Their complete UI is preserved within 9:16 or 16:9 canvases because the
original 1080×2412 device ratio exceeds Play's 2:1 limit. No game cover artwork is
included in the store packet. Regenerate with Pillow and `rsvg-convert`:
`python scripts/prepare-play-assets.py`.

The existing 52.5-second promotional MP4 is in `captures/promo/`. Play accepts a
YouTube URL for a preview video, not a local MP4. No YouTube upload has been made.

## Data safety and app-content answers

The audited Play build has no Internet permission, analytics, ads, remote crash
reporting, authentication or server-side storage. In the Data safety form, declare
**no collected or shared user data**: local-only processing is outside Play's
definition of collection. User-directed file hand-off to an emulator is excluded
from sharing. Optional notification metadata remains in memory on the device.
The privacy policy also covers the GitHub distribution's optional network updates.

- Ads: no.
- App access: all launcher features are available without credentials. Android
  permission grants, a controller, ROM files and an installed emulator are optional.
- Account creation: none; account-deletion URL is not applicable.
- Permissions: no broad package visibility, all-files access, contacts, location,
  camera, microphone or install-packages permission in the Play release.
- Accessibility: not an accessibility tool. Purpose: **app functionality**, solely
  the user-triggered lock-screen action. No personal or sensitive data is collected
  or shared through accessibility. `canRetrieveWindowContent=false`; no events.
- Notification listener: explain notification dots, local metadata use, and the
  in-app affirmative consent screen before Android settings opens.
- IARC/content rating and target audience must describe the actual launcher. Do not
  claim any age rating until the questionnaire produces it; don't invent legal
  identity, organization details or testing participation.

Accessibility reviewer instructions:

1. Open Pluto, complete or skip optional setup, then open Settings → Layout & rotation.
2. Turn on “Double-tap to lock”. Read the standalone disclosure; Cancel leaves it off.
3. Repeat, choose “Agree and continue”, and enable “Pluto screen lock” in Android's
   Accessibility settings. Return Home and double-tap empty space to lock the screen.
4. Android 13+ may require allowing restricted settings when this build is sideloaded;
   that is not the Play installation path.

Play requires a short video showing startup, the complete disclosure, decline and
retry, consent/Android permission grant and the actual lock action. Upload the
review recording to an account-controlled, reviewer-accessible URL and enter that
URL in the Accessibility declaration. The promotional video is not a replacement.

## Account and release progress

Registration/payment are complete for the requested Google account; Google identity
approval is pending. Google rejected sign-in in the shared browser with “This browser
or app may not be secure,” so authenticated Console access is still needed.
Registration costs US$25 once. A new personal account also requires
Android device verification and a closed test with **at least 12 testers opted in
continuously for 14 days**, followed by an application for production access.

Once account access is available: create Pluto Launcher as a free app, enroll in
Play App Signing, upload the signed bundle to internal testing, complete listing
and app-content declarations, then launch the required closed test. Keep tester
feedback and actual fixes to support the production-access application.

## Official references checked 12 October 2026

- [Register a developer account](https://support.google.com/googleplay/android-developer/answer/6112435)
- [Permissions and self-update restriction](https://support.google.com/googleplay/android-developer/answer/9888170)
- [Accessibility disclosure and review video](https://support.google.com/googleplay/android-developer/answer/10964491)
- [Data safety definitions](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Privacy policy](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Store assets](https://support.google.com/googleplay/android-developer/answer/9866151)
- [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756)
- [New personal-account testing](https://support.google.com/googleplay/android-developer/answer/14151465)
- [Target SDK requirements](https://support.google.com/googleplay/android-developer/answer/11926878)
