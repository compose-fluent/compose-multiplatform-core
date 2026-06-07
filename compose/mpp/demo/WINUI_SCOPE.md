# WinUI MPP Sample Scope

This file defines the first repository-local `compose/mpp/demo` scope that must
run on the WinUI JVM target before the sample is treated as representative.

## Required module and source scope

- Module: `:compose:mpp:demo`.
- Shared source: `compose/mpp/demo/src/commonMain`.
- WinUI-specific source to add: `compose/mpp/demo/src/winuiJvmMain`.
- Entry content: `androidx.compose.mpp.demo.App.Content`.
- Initial window size and title should match the desktop entry:
  `1024.dp x 850.dp`, title `Compose MPP demo`.
- The WinUI entry must not depend on `desktopMain`, Swing, AWT,
  `singleWindowApplication`, `WindowState`, `SwingPanel`, or Skiko desktop/AWT
  runtime artifacts.

## Required navigation scope

The first WinUI run must start at `MainScreen` and expose the existing
top-level `MainScreen` routes from `commonMain`:

- `Components`
- `BugReproducers`
- `Graphics`
- `Example1`
- `ImageViewer`
- `ViewModelExample`
- `LottieAnimation`
- `ApplicationLayouts`
- `InteropOrder`
- `AndroidTextFieldSamples`
- `Android TextBrushDemo`
- `AndroidAccessibilityDemos`

The first validation may launch a narrower initial route through
`App(initialScreenName = ...)`, but the WinUI sample variant is not complete
until the full top-level list above is reachable.

## Required resources

- `src/commonMain/resources/RobotoFlex-VariableFont.ttf`
- `src/desktopMain/resources/NotoColorEmoji.ttf`

The initial WinUI resource staging must load both files from the WinUI runtime
classpath or staged resource directory. `RobotoFlex-VariableFont.ttf` is used
by `VariableFonts`; `NotoColorEmoji.ttf` is preloaded by the desktop entry and
should be preserved by the WinUI entry unless replaced by an explicit WinUI font
preload path.

No image resources are currently bundled in `commonMain` or `desktopMain`.
Image-related sample paths still need API/runtime validation because the
sample contains image rendering code.

## Required WinUI actuals or guards

- `androidx.compose.mpp.demo.TestInteropView` must use `WinUIView` and a WinUI
  `UIElement`; it must not use `SwingPanel`, `JPanel`, or `java.awt.Color`.
- `androidx.compose.mpp.demo.components.DragAndDropExample` must either use
  WinUI drag-and-drop transfer APIs or be guarded with a tracked WinUI gap until
  native drag-and-drop transfer data is available.
- `androidx.compose.mpp.demo.components.text.loadResource` must load resources
  from the WinUI staged runtime resources.
- `androidx.compose.mpp.demo.main` / WinUI entry bootstrap must use the
  compose-winui `Application` / `Window` path and Windows App SDK initialization
  chain already used by `compose/ui/ui/winui-samples`.

## Platform-only exclusions for the first WinUI variant

The first WinUI JVM variant does not include `webMain`, `wasmJsMain`,
`iosMain`, `macosMain`, or `nativeMain` platform-only demos. Those paths stay
outside the WinUI validation scope until equivalent WinUI abstractions are
implemented or guarded.
