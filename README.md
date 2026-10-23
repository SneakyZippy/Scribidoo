# Scribidoo

A fast, distraction-free handwritten note-taking and sketching app built with Kotlin & Compose Multiplatform. Designed specifically for active digitizer pens (Surface Pen, S-Pen, USI) on Android tablets and Windows Touch PCs.

## Highlights

- **Active Stylus First**: Low-latency ink rendering with quadratic Bézier smoothing and pressure sensitivity.
- **Configurable Palm Rejection**: Digitizer hover priority with adjustable finger & palm rejection thresholds so you can rest your hand on the glass.
- **Floating Toolbar**: Drag it anywhere on screen. Switch between a 1-row horizontal bar or a compact 2-column vertical dock.
- **Canvas Flexibility**: Paged sheets (A4/letter) or an infinite dot-grid canvas with smooth pan & pinch-zoom.
- **Inking Tools**:
  - Fountain & Ballpoint Pen with pressure-sensitive width.
  - Chisel-tip Highlighter with multiply blend-mode layering.
  - Precision point eraser & whole-stroke eraser.
  - Movable & resizable text boxes (long-press to reposition or resize).
- **Import & Export**: Annotate imported PDFs/images and export pages as high-resolution PNGs or multi-page PDFs.

## Architecture

Shared vector canvas core across platforms:

```
├── shared/     # Vector math, Bézier smoothing, eraser engine, models & state
├── app/        # Android tablet/phone target (Jetpack Compose, MotionEvent router)
└── desktop/    # Windows Touch PC target (Compose Multiplatform, Skia, pointerInput)
```

## Getting Started

### Prerequisites
- JDK 17 or higher
- Android SDK (for `:app`)

### Running on Windows
To launch the desktop app directly:
```bash
./gradlew :desktop:run
```

To create a standalone Windows installer or portable build:
```bash
./gradlew :desktop:packageMsi
# or portable folder:
./gradlew :desktop:createDistributable
```

### Running on Android
Open the project in Android Studio, or build and install via CLI:
```bash
./gradlew :app:installDebug
```

## Gestures & Controls

| Action | Stylus / Touch | Mouse (Desktop) |
|---|---|---|
| **Draw / Erase** | Active Pen tip (pressure-sensitive) | Left click + drag |
| **Pan Canvas** | 2-finger drag | Right click + drag or Middle click |
| **Zoom** | 2-finger pinch | Mouse scroll wheel (or Ctrl + Scroll) |
| **Move / Resize Text** | Long-press text box & drag | Drag text box handle |

## License
MIT
