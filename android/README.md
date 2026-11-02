# Abreisser for Android

A native Android version of the Abreisser web app: design tear-off flyers (Abreißzettel) with images, formatted text and QR codes, stack them on A4 and print them or save them as PDF.

Written in Kotlin with Jetpack Compose. The page is drawn with `android.graphics.Canvas`, so the on-screen preview and the printed PDF come from the same code.

## Features

Everything the web app does:

- **Main part:** title, description and an optional image, with alignment, sizes and image position. White-on-transparent logos can be recoloured to black.
- **Tear-off strips:** image, text and QR code per strip, identical or individual per strip, with empty fields falling back to the shared values.
- **Text formatting:** `**bold**` and `*italic*` for parts of the text (B / I buttons on the selection), or bold, italic and underline for the whole text. Four font styles, size, alignment and reading direction.
- **Page layout:** rows per A4 page, number of pages, margins, gaps, cut lines and outlines, with the same warnings (text cut off, rows that don't fit, QR dots too small, links without `https://`).
- **Printing** through the Android print dialog (A4, no margins), including “Save as PDF”, and **Share PDF** to send the file to another app.
- German and English, following the system language or picked in the ⋮ menu.
- Settings and images are kept between launches.

Phones get a bottom navigation with the three steps plus the preview; tablets and large screens show the editor next to the preview.

## Building

Requires JDK 17 and the Android SDK (API 35).

```bash
cd android
./gradlew assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/`. Open the `android` folder in Android Studio to run it on a device or emulator.

The `Android` GitHub Actions workflow builds the debug APK on every push that touches `android/` and uploads it as a build artifact.
