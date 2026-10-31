# amade-imageeditor

**amade-imageeditor** is the image editor of the [amadeOS](https://github.com/amadeos-eu/amadeos) desktop: it crops, turns, straightens, resizes and corrects the colours of a photo, with unlimited undo, and saves it as JPEG, PNG, WebP, TIFF or BMP. Built like the other amadeOS applications.

![License](https://img.shields.io/badge/license-EUPL--1.2-blue)
![Java](https://img.shields.io/badge/Java-25-orange)

![amade-imageeditor](docs/screenshots/amade-imageeditor.png)

## Features

- **Opens** JPEG, PNG, WebP, TIFF, BMP and GIF, and also PNM, TGA, PSD, IFF, PICT, SGI and ICNS: from the file manager, by drag and drop, with *File → Open* (Ctrl+O) or on the command line. Photos appear upright as the camera's EXIF orientation says.
- **Crop** (Ctrl+K): drag the frame or its eight handles, or type position and size. Ratios Free, Original, 1:1, 4:3, 3:2, 16:9, 5:4 and Custom (for example 7:5), portrait or landscape, with a rule-of-thirds or grid overlay.
- **Straighten** (Ctrl+T): an angle slider from −45° to 45°, or draw a line along the horizon (or a wall) and the picture turns so that the line becomes level. *Crop to the picture* cuts away the empty corners.
- **Turn and flip**: 90° left and right (Ctrl+L, Ctrl+R), flip horizontally and vertically.
- **Adjust colours** (Ctrl+J): brightness, contrast, saturation, warmth and black and white, live in the picture; Space shows the original for comparison. *Auto Levels* stretches the tones in one step.
- **Resize** (Ctrl+E): width and height in pixels or percent, keeping the ratio, with presets (3840, 1920, 1280, 1024, 800 px on the long side, 50 %) and three quality levels.

  ![Adjust colours](docs/screenshots/amade-imageeditor-adjust.png)
- **Unlimited undo and redo** (Ctrl+Z, Ctrl+Y); the menu names the step (*Undo Crop*).
- **Save** (Ctrl+S) and **Save As** (Ctrl+Shift+S) with format, JPEG and WebP quality and lossless WebP. *Save* writes back into the opened file; *Save As* says whether the chosen name replaces the original or keeps it. Files are written to a temporary name first and then moved into place, so a failed save never leaves half a picture.
- **Metadata** from JPEG to JPEG: camera data, the description (XMP, IPTC) and the location are kept, each can be switched off; *Keep location (GPS)* is on by default. The orientation is set to upright, the size corrected and the old preview picture removed, so no other program shows the uncropped photo.
- **Lossless turns**: if a JPEG was only turned or flipped, `jpegtran` writes it without decoding it again (the package recommends `libjpeg-turbo-progs`).
- **WebP** is written with `cwebp` (the package recommends `webp`), TIFF with LZW compression.
- **Copy** the picture (Ctrl+C) into the clipboard.
- Five languages (English, Deutsch, Français, Español, Italiano), following the amadeOS desktop language; *Options → Language* overrides it.
- Theme colours and Motif look from [amade-commons](https://github.com/amadeos-eu/amade-commons).

## Architecture

Java 25, Swing and Guice; start-up, theme and language come from `AmadeApplication` in amade-commons. All picture operations are plain Java2D. Formats come from the [TwelveMonkeys](https://github.com/haraldk/TwelveMonkeys) ImageIO plugins, the EXIF orientation from [metadata-extractor](https://github.com/drewnoakes/metadata-extractor).

| Package | Contents |
|---|---|
| `bootstrap` | `AmadeImageEditorApplication`, `ImageEditorModule` |
| `domain` | `Edit` (crop, turn, straighten, resize, adjust, auto levels), `History` (undo and redo), `CropBox`, `AspectRatio`, `Adjustments`, `ImageFormat`, `SaveOptions`, `ResizeQuality`, `Orientation`, `ZoomSteps` |
| `repository` | `EditorPreferences` (`~/.config/amadeos/imageeditor/imageeditor.properties`: save settings per format, resize quality, last folder) |
| `service` | `ImageLoader`, `ImageOperations` (the edits on pixels), `ImageSaver` (formats, `jpegtran`, `cwebp`, atomic writes), `JpegMetadata` (EXIF, XMP and IPTC from the original into the new JPEG) |
| `ui.main` | window, presenter, menus, `EditorCanvas` (picture, crop frame, level line, comparison), `ToolPanel`, `ResizePanel`, `SavePanel` |

Every edit is a value in the history. The picture after each step is kept as long as memory allows; otherwise it is computed again from the original. The presenter knows the window only through the `MainView` interface, so the tests drive the whole editor (tools, undo, save, unsaved changes) without Swing.

## Build and run

```sh
./gradlew run --args="~/Pictures/lake.jpg"   # start with a picture
./gradlew test                               # Swing tests need a display: xvfb-run ./gradlew test
./gradlew deb                                # build/distributions/amade-imageeditor_<version>_all.deb
./gradlew installToAmadeOs                   # the .deb into ../amadeos/config/packages.chroot/ for the ISO build
```

If `../amade-commons` is checked out next to this repository, Gradle builds against that checkout instead of the JitPack version.

## Planned

- *Edit* in the image viewer opens the picture here
- Painting, text and shapes; layers
- Red-eye removal, sharpening, blur
- Writing the title, caption and keywords

## License

[EUPL-1.2](LICENSE)
