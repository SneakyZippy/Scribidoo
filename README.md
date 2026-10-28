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

#### Option A: Windows Installer (.exe / .msi)
Creates an installer that prompts for install location, installs per-user, and automatically adds **Desktop & Start Menu shortcuts**:
```bash
# Generate .exe installer:
./gradlew :desktop:packageExe
# Output: desktop/build/compose/binaries/main/exe/Scribidoo-1.0.0.exe

# Or generate .msi installer:
./gradlew :desktop:packageMsi
# Output: desktop/build/compose/binaries/main/msi/Scribidoo-1.0.0.msi
```

#### Option B: Standalone Portable Application (No installation needed)
If you want to copy Scribidoo to a USB drive or laptop and run it immediately without installing any developer tools, JDK, or running an installer wizard:

- **1-Click Build on Dev PC**: Double-click [`build-portable.bat`](file:///d:/Projects/Scribidoo/build-portable.bat) (or run `./gradlew :desktop:packagePortableZip`).
  - Output files are automatically copied to root `dist/`:
    - `dist/Scribidoo-Portable.zip` (ready to copy via USB or cloud drive)
    - `dist/Scribidoo-Portable/Scribidoo.exe` (unpacked directory)
  - Copy `Scribidoo-Portable.zip` to your laptop, extract it anywhere, and double-click `Scribidoo.exe`. It runs standalone without needing Java or any other dependencies installed!

- **Download directly via GitHub Actions**:
  - Push to GitHub, go to the **Actions** tab, select the latest build, and download `Scribidoo-Portable-Windows` directly on your laptop via your browser.

### Running on Android
Open the project in Android Studio, or build and install via CLI:
```bash
./gradlew :app:installDebug
```

## Gestures & Controls

| Action | Stylus / Touch (Stylus Only Mode) | Stylus / Touch (Touch Draw Mode) | Mouse (Desktop) |
|---|---|---|---|
| **Draw** | Active Pen tip (pressure-sensitive) | Pen tip or 1-finger drag | Left click + drag |
| **Erase** | Pen eraser tail or Eraser tool | Eraser tool | Left click with Eraser tool |
| **Pan Canvas** | 1-finger drag or 2-finger drag | 2-finger drag | Right click + drag or Middle click |
| **Zoom** | 2-finger pinch | 2-finger pinch | Mouse scroll wheel (or Ctrl + Scroll) |
| **Move / Resize Text** | Long-press text box & drag | Long-press text box & drag | Drag text box handle |
| **Palm Rejection** | Complete palm immunity | Adjustable sensitivity | N/A |

## License
MIT
