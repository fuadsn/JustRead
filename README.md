<div align="center">

<img src="docs/icon.png" alt="JustRead icon" width="128" height="128">

# JustRead

**A quiet EPUB reader, built in Kotlin.**

A calm, focused reader with real page curls, careful typography, themes for day and night,
and a small library that remembers where you left off. A native Android app written with
Jetpack Compose. No store, no accounts, no ads.

[![status](https://img.shields.io/badge/status-stable-3DA35D)](#status)
[![built in Kotlin](https://img.shields.io/badge/built%20in-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![runs on](https://img.shields.io/badge/runs%20on-Android%208.0%2B-3DDC84?logo=android&logoColor=white)](#download)
[![license](https://img.shields.io/badge/license-GPL--3.0-555555)](LICENSE)
[![release](https://img.shields.io/github/v/release/JobinBiju/JustRead?label=release&color=6A4A2B)](https://github.com/JobinBiju/JustRead/releases/latest)

<br>

<img src="docs/screenshots.png" alt="The library, a page of the sample book, and a page mid-curl" width="100%">

</div>

## Features

- **Opens your own EPUBs.** EPUB 2 and 3: OPF metadata, the spine, the EPUB 3 `nav` table of contents
  with an NCX fallback, covers, the book's own CSS, and footnotes that open in a popover. On Android,
  books open from the file picker or straight from another app.
- **Real page curls.** The page rolls round a cylinder under your finger, with the print showing
  faintly through the back and soft shadows on the pages below. Slide and continuous scroll are
  there too.
- **Its own pagination.** Text is laid out into pages by the app, not a web view: widows and orphans
  are kept to two lines, words are hyphenated, images fit the page, and one or two columns are chosen
  to suit the screen.
- **Typography you can tune.** Eight bundled reading fonts (Literata, Source Serif 4, Lora,
  Newsreader, Alegreya Sans, Atkinson Hyperlegible, Lexend, Roboto) or the publisher's own, with
  twelve size steps, bold text, justification, and line, letter and word spacing.
- **Themes for any light.** Parchment, White, Sepia, Stone, Dusk and Night, each with a dark
  variant. Appearance can follow the system, or the room through the light sensor.
- **A library that keeps your place.** Continue reading, sort by recent, title, author, date added
  or progress, grid or list views, search, bookmarks, and a daily reading goal.
- **Comfort settings.** In-app brightness and a rotation lock.

## Status

Stable and in active use. It reads reflowable EPUBs well. Fixed-layout books are shown reflowed,
and encrypted (DRM) books are refused.

## Download

Get the latest APK from [Releases](https://github.com/JobinBiju/JustRead/releases/latest). Most
phones want the `arm64-v8a` build; the `universal` one works anywhere. Android 8.0 or newer.

## Building

You need JDK 17 or newer and the Android SDK.

```sh
# build and install a debug build on a connected device or emulator
./gradlew :app:installDebug

# release APKs, one per ABI plus a universal one, copied to dist/
./gradlew releaseApk
```

Local release builds are signed with your debug key, so they install directly for testing.
Published releases are built and signed by the **Release** workflow when a `v*` tag is pushed (see
[CONTRIBUTING.md](CONTRIBUTING.md#releases)).

## How it's put together

Everything lives in one Android module, `app/`, under `app/src/main/kotlin/app/reader/`:

| File | What it does |
|---|---|
| `Epub.kt`, `Xml.kt`, `Css.kt` | Reading the EPUB package, its XHTML and its CSS |
| `Paginator.kt`, `PageArt.kt` | Laying text out into pages and drawing them |
| `Curl.kt` | The page curl: geometry, shader and fallback |
| `Reader.kt`, `Panels.kt` | The reader screen and its sheets |
| `Library.kt`, `App.kt` | The library and the app's state |
| `Settings.kt`, `Theme.kt`, `Widgets.kt` | Reader settings, themes, colours and shared controls |
| `Platform.kt` | Storage, ZIP, pictures, the light sensor and the shader hook |
| `android/MainActivity.kt` | The file picker, opening books from other apps, brightness, rotation lock and sharing |

The page curl is drawn by an AGSL runtime shader on Android 13 and newer. For each pixel it works
out which point of the paper lands there and whether that point shows the front or the back. Older
Android versions draw the page in thin strips squeezed to follow the roll.

The launcher icon is generated from the artwork in `app/icon-src/`
(`python3 icon-src/make_icons.py`, run from `app/`; needs Pillow).

## Contributing

Bug reports, fixes and ideas are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) first.

## License

JustRead is free software, released under the [GNU General Public License v3.0](LICENSE).

## Credits

The bundled typefaces are used under the SIL Open Font License. Each font's licence is in
`app/src/main/assets/licenses/`.
