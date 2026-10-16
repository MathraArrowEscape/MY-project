# MATHRA KEYBOARD  👑🧸

A real Android keyboard (`InputMethodService`) with a premium 3D teddy-bear look and pop-up typing animations.
Brand / developer: **MATHRA**. Kotlin, **zero third-party dependencies**, min Android 7.0 (API 24).

## Project structure

```
MathraKeyboard/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml          (only permission: VIBRATE)
        ├── java/com/mathra/keyboard/
        │   ├── MathraIME.kt             the keyboard service: commits text, Enter actions, delete, selection,
        │   │                            suggestions, autocorrect, clipboard, voice hand-off
        │   ├── KeyboardView.kt          one Canvas-drawn view: 3D keycaps, pop/bounce, floating letters,
        │   │                            particles, multi-touch, long-press, key repeat, space-bar swipe
        │   ├── Layouts.kt               English, symbols (2 pages), Sinhala (4 pages), emoji (6 categories)
        │   ├── Themes.kt                Neon Teddy, Pink Dream, Blue Galaxy, Black Gold, Cute Pastel
        │   ├── Suggest.kt               built-in word list, completions and typo fixes (nothing is learned/stored)
        │   ├── Feedback.kt              "Teddy pop" sound (generated in code), system click, haptics
        │   ├── Art.kt                   vector teddy bear (cap + crown) drawn with Canvas
        │   ├── Prefs.kt                 settings only (never typed text)
        │   ├── SettingsActivity.kt      setup steps, animated theme previews, all toggles/sliders, privacy text
        │   └── SplashActivity.kt        animated splash screen
        └── res/                         launcher icon (built from your logo), themes, manifest metadata
```

## Build the APK

Requirements: **Android Studio (Koala or newer)** or JDK 17 + Android SDK 34 command-line tools.

**Android Studio (easiest)**
1. `File > Open` and choose the `MathraKeyboard` folder. Let Gradle sync (it downloads Gradle 8.7 and the Android plugin).
2. `Build > Build Bundle(s) / APK(s) > Build APK(s)`.
3. The APK is at `app/build/outputs/apk/debug/app-debug.apk`.

**Command line**
```bash
cd MathraKeyboard
gradle wrapper --gradle-version 8.7     # one-time: creates ./gradlew (needs Gradle installed), or just open in Android Studio
./gradlew assembleDebug                 # -> app/build/outputs/apk/debug/app-debug.apk
```

**Release APK (for sharing/publishing)**: create a keystore, add a `signingConfig` to `release` in
`app/build.gradle.kts`, then run `./gradlew assembleRelease`.

## Install and turn it on

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
(or copy the APK to the phone and open it.)

1. Open **MATHRA KEYBOARD** and tap **1 Enable** (opens Settings > Languages & input > On-screen keyboard),
   switch MATHRA Keyboard on. Android shows a standard warning for every third-party keyboard.
2. Tap **2 Switch** and choose MATHRA Keyboard.
3. Try it in the "Try it" box.

## Using it

| Action | How |
|---|---|
| Change theme quickly | tap the bear (top-left of the keyboard) |
| Open settings | long-press the bear, or tap the gear |
| English / Sinhala | tap the `සි` / `EN` key. Long-press it, or the space bar, for the keyboard picker |
| Sinhala pages | the left key on the third row cycles consonants, more consonants, vowels, vowel signs |
| Digits without the number row | long-press the top-row letters |
| Select text | turn Shift on, then swipe left/right on the space bar. Without Shift it just moves the cursor |
| Select all | long-press Shift |
| Paste / copy | tap / long-press the clipboard icon |
| Voice typing | tap the mic. It hands over to an installed voice-typing keyboard, if the phone has one |

## Privacy and permissions

- No `INTERNET` permission, no analytics, no ads, no third-party libraries.
- Typed text goes only to the app you are typing in. It is never logged, stored or sent anywhere.
- Suggestions and autocorrect read the few characters before the cursor in memory only, and are off in password, e-mail, URL, number and "no suggestions" fields.
- The only permission is `VIBRATE` (key haptics, can be switched off).
- `allowBackup` is off. Only keyboard settings (theme, toggles) are saved, in app-private storage.
- This is an honest description of the code, **not a security audit**. Anyone who needs a strong guarantee should review the source and build it themselves.

## Honest limitations

- **Not compiled or run in the environment where this was written.** It was written carefully against the Android 14 SDK and
  statically checked, but expect to fix a small compile error or two on first build, and test on a real device.
- **GIF search is not included.** GIF keyboards need an internet connection plus a third-party GIF service, which conflicts with the
  no-network privacy goal. Emoji, clipboard (paste/copy) and voice shortcuts are included.
- **Sinhala is a direct Unicode layout** (tap the actual letters), not phonetic "Singlish" typing. Suggestions/autocorrect are English only.
- **Accessibility:** the keys are drawn on a Canvas, so TalkBack support is basic. A full virtual-view accessibility tree is the next improvement.
- "Teddy pop" sound is a tiny synthesised tone. System click uses Android's own key sounds (needs "touch sounds" enabled in system settings).
- Suggestions use a small built-in word list (about 600 common words), so they are simpler than a big commercial keyboard's.

## Customising

- Colours: edit `Themes.kt`. Each theme is one `MTheme(...)` entry.
- Animation feel: `POP` and the spring constants (`exp(-9f * t) * cos(22f * t)`) in `KeyboardView.kt`.
- Layouts / Sinhala letters: `Layouts.kt`.
