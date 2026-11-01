# Abreisser

Design tear-off flyers (Abreißzettel) with images, formatted text and QR codes, stack them on A4 and print straight from the browser.

## Features

- **Main section:** title, description and an optional image, with adjustable alignment, sizes and image position. White-on-transparent logos can be recoloured to black so they show up on paper.
- **Tear-off strips:** each strip holds an image, text and a QR code. All strips can be identical, or each one can be different. Empty fields fall back to the values above them.
- **Text formatting:** bold, italic and underline, for the whole text or only the selected part. Choice of font, size and reading direction.
- **Page layout:** stack several flyers on one A4 sheet, with margins, gaps and optional cut lines.
- **German and English interface.**
- No build step, no backend. Everything runs in the browser.

## Usage

Open `index.html` in a browser, or serve the folder locally:

```bash
python -m http.server 8765
```

Then go to <http://localhost:8765>.

### Presets

`index.html?preset=name` loads `presets/name.json` over the default settings. A preset may set any of the app's state sections (`page`, `header`, …). Preset files are not committed. Only `presets/.gitkeep` is tracked.

## Deployment

The site is static and uses relative paths, so it can be served from GitHub Pages as is. In the repository settings, go to **Pages**, set the source to **Deploy from a branch** and select `main` / `(root)`.

## Dependencies

- [qrcode-generator](https://github.com/kazuhikoarase/qrcode-generator) 1.4.4, loaded from cdnjs
