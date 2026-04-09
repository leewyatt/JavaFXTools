# JavaFX Tools

[![Version](https://img.shields.io/jetbrains/plugin/v/com.itcodebox.fxtools.id?label=version)](https://plugins.jetbrains.com/plugin/14287-javafx-tools)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/com.itcodebox.fxtools.id)](https://plugins.jetbrains.com/plugin/14287-javafx-tools)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

All-in-one JavaFX development toolkit for IntelliJ IDEA — CSS intelligence, gutter previews, FXML code assistance, Ikonli icon browsing, SVG path extraction, and FxmlKit integration.

Works with both **Community** and **Ultimate** editions. Requires IntelliJ IDEA **2024.2+** and Java **17+**.

<img src="screenshots/img.png" alt="Overview" width="600">

---

## Features

### CSS Intelligence

**Property Completion & Documentation**
- 210+ built-in `-fx-*` properties with type-aware value completion
- Third-party library support — ControlsFX, GemsFX, JFoenix properties auto-detected from classpath
- Quick Documentation (F1) for all properties with multi-library source attribution
- CSS variable completion with cross-file resolution
- CSS transition properties (`transition`, `transition-property`, `transition-duration`, etc.)
- `-fx-cursor` value completion with visual cursor shape icons

**Gutter Previews**
- Color previews — hex, rgb, rgba, hsl, hsla, named colors, `derive()`
- Gradient previews — `linear-gradient()`, `radial-gradient()`
- SVG path previews — `-fx-shape` rendered as scaled path icons
- Effect previews — `dropshadow()`, `innershadow()` with blur visualization
- Ikonli icon previews — `-fx-icon-code` rendered as SVG gutter icons
- CSS variable resolution — variables resolved through chains (depth 10) to their final color/gradient/SVG
- **Multi-value paint support** — `-fx-background-color` and `-fx-border-color` show one icon per paint segment (up to 4)

<img src="screenshots/img_1.png" alt="Gutter Previews" width="600">

**Click-to-Edit**
- Click a color icon -> opens embedded PaintPicker with real-time write-back
- Click a gradient icon -> opens PaintPicker in gradient mode
- Click an effect icon -> opens Effect Editor (DropShadow / InnerShadow with 4 blur types)
- Click an SVG icon -> opens path preview with size controls
- All edits support single Ctrl+Z undo

<img src="screenshots/img_5.png" alt="PaintPicker" width="380">

**Inline CSS (Java & FXML)**
- `setStyle("...")` in Java — gutter previews + click-to-edit + auto-popup completion
- `style="..."` in FXML — same preview and editing support
- Text block support — each line gets independent gutter icons
- `SVGPath.setContent("...")` — read-only SVG preview
- Ctrl+Click navigation from inline CSS variables to their `.css` definition site

<img src="screenshots/img_3.png" alt="Inline CSS — Effect Editor (Dark)" width="500">

<img src="screenshots/img_4.png" alt="Inline CSS — Effect Editor (Light)" width="500">

---

### Ikonli Icon Integration

**Icon Browser ToolWindow**
- 114 icon packs, 63,000+ icons from the [Ikonli](https://github.com/kordamp/ikonli) library
- Search across all packs with fuzzy keyword matching
- Pack filter for narrowing results
- Detail panel with preview, icon name, pack name, and source link
- One-click copy: SVG path / Java code / CSS code / Maven / Gradle coordinates
- Double-click an icon to insert its SVG path data at the editor cursor

<img src="screenshots/img_2.png" alt="Icon Browser" width="450">

**Code Assistance**
- `-fx-icon-code` value completion in CSS with SVG preview per candidate
- `<FontIcon iconLiteral="..."/>` completion in FXML
- Java enum constant gutter icons (e.g. `FontAwesome.HOME` shows the icon inline)
- Classpath-aware: only packs on your project classpath appear in completion


---

### SVG Path Extractor

**ToolWindow Panel**
- Drop or open an SVG file to extract and merge path data
- Side-by-side preview: original SVG vs extracted path
- Convert basic shapes (rect, circle, ellipse, line, polyline, polygon) to path data
- Normalize coordinates to a target size with configurable precision
- One-click copy of the extracted path string

**Right-click Actions**
- **Copy SVG Path Data** — right-click .svg files in the project view to copy merged path data to clipboard (multi-select supported)
- **Open in SVG Path Extractor** — opens the file in the SVG Path Extractor ToolWindow panel

---

### FXML Code Assistance

**Navigation**
- Bidirectional View <-> FXML <-> CSS navigation via gutter icons
- Controller <-> FXML navigation (works in all JavaFX projects, not just FxmlKit)
- `@FxmlPath` annotation: Ctrl+Click, completion, rename refactoring
- Resource path navigation for `<Image url="..."/>`, `<fx:include source="..."/>`
- `%key` i18n navigation to `.properties` files (with `com.intellij.properties` plugin)

**Inspections & Quick Fixes** (14 inspections)
- Missing FXML / Controller / CSS files -> Create File quick fix
- fx:id field not found in controller -> Create Field quick fix with type inference
- Event handler method not found -> Create Method quick fix (33 event types)
- @FXML field type mismatch -> Change Type quick fix
- Unused @FXML fields and methods detection
- Invalid resource paths, unused CSS selectors, i18n key validation


---

### FxmlKit Integration

A structured MVC pattern for JavaFX with convention-based file resolution.

**New -> FxmlKit View wizard**
- Creates View + ViewProvider + Controller + FXML + CSS files from a single dialog
- Segmented view type selector (View / ViewProvider)
- Optional i18n resource bundle configuration with locale selection
- Live file tree preview showing generated file structure

<img src="screenshots/img_6.png" alt="FxmlKit Wizard" width="450">

<img src="screenshots/img_9.png" alt="Resource Bundle — Existing" width="400">

<img src="screenshots/img_10.png" alt="Resource Bundle — Create New" width="400">

**Property Generation** (Alt+Insert / Cmd+N)
- 10 property types: String, Integer, Long, Float, Double, Boolean, Object, List, Map, Set
- ReadOnly wrapper generation
- Lazy initialization option
- CSS Styleable property generation with `CssMetaData` boilerplate
- Live code preview in dialog

<img src="screenshots/img_7.png" alt="Property Live Templates" width="500">

<img src="screenshots/img_8.png" alt="Property Options" width="400">

---

### Font & SVG File Actions

- **Right-click .ttf/.otf** -> Copy Font Family Name / Copy @font-face CSS
- **Right-click .svg** -> Copy SVG Path Data / Open in SVG Path Extractor
- **Smart paste**: @font-face blocks auto-resolve relative paths when pasted into CSS files
- Multi-file selection supported

---

### Settings

Settings -> Tools -> JavaFX Tools

- **Gutter Previews** — toggle gutter preview icons on/off, adjust icon size (50%~150%)
- **Icon Browser** — toggle double-click icon insertion
- **Notifications** — JFX-Central weekly digest notification

---

## Installation

**From JetBrains Marketplace:**

1. Open IntelliJ IDEA -> Settings -> Plugins -> Marketplace
2. Search for **"JavaFX Tools"**
3. Click Install, restart IDE

**Manual installation:**

1. Download the latest release `.zip` from [Releases](https://github.com/leewyatt/JavaFXTools/releases)
2. Settings -> Plugins -> gear icon -> Install Plugin from Disk -> select the `.zip`

---

## Compatibility

| Requirement | Version |
|-------------|---------|
| IntelliJ IDEA | 2024.2+ (Community or Ultimate) |
| Java | 17+ |
| JavaFX SDK | Not required (plugin uses Swing/IntelliJ Platform UI) |

**Third-party CSS library support:**

The plugin auto-detects these libraries on your project classpath and provides their CSS properties:

| Library | Marker Class | Properties |
|---------|-------------|------------|
| ControlsFX | `org.controlsfx.control.GridView` | ControlsFX-specific CSS |
| GemsFX | `com.dlsc.gemsfx.DialogPane` | GemsFX-specific CSS |
| JFoenix | `com.jfoenix.controls.JFXButton` | JFoenix Material CSS |
| Ikonli | `org.kordamp.ikonli.Ikon` | `-fx-icon-code`, `-fx-icon-size`, `-fx-icon-color` |

---

## Building from Source

```bash
# Clone
git clone https://github.com/leewyatt/JavaFXTools.git
cd JavaFXTools

# Build
./gradlew buildPlugin

# Run sandbox IDE with plugin loaded
./gradlew runIde
```

Requires Gradle 8.14 and JBR 21 (configured via `gradle.properties`).

---

## Acknowledgements

- The embedded PaintPicker (color & gradient editor) is a Swing rewrite of the PaintPicker component from [Scene Builder](https://github.com/gluonhq/scenebuilder) by Gluon. Thanks to the Scene Builder team for the excellent original JavaFX implementation.
- Ikonli icon data comes from the [Ikonli](https://github.com/kordamp/ikonli) library. Thanks to [Andres Almiray](https://github.com/aalmiray) for creating and maintaining such a comprehensive icon pack collection for JavaFX.
- The icon packs bundled in the Icon Browser originate from numerous open-source icon libraries (such as FontAwesome, Material Design Icons, Weather Icons, etc.). We are deeply grateful to the authors and communities behind each of these projects. See [ICON_PACKS.md](ICON_PACKS.md) for the full list of icon libraries and their project URLs.
- Special thanks to [Dirk Lemmermann](https://github.com/dlemmermann) for providing valuable feedback, testing, and feature suggestions that helped shape this plugin.

---

## Sponsors

[DLSC](https://www.dlsc.com) · [JFXCentral](https://www.jfxcentral.com)

---

## License

[MIT License](LICENSE) — Copyright (c) 2022 LeeWyatt
