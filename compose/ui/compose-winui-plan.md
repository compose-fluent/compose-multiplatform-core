# compose-winui implementation plan

## Architecture
- [x] Implement compose-winui as a standalone `compose-ui` platform target, comparable in responsibility to `androidMain`.
- [x] Keep `winuiMain` independent from `skikoMain`, `desktopMain`, AWT, and Swing.
- [x] Use `kotlin-winrt` as the WinRT and WinUI projection/runtime foundation instead of duplicating COM or Windows App SDK bootstrap code in `compose-ui`.
- [x] Share WinUI-specific Compose semantics in `winuiMain`, with `winuiJvmMain` providing current JVM runtime details and `winuiMingwMain` deferred until `kotlin-winrt` provides mingw support.
- [ ] Treat Android `AndroidView` interop as the behavioral reference for factory, update, reuse, detach, release, layout, focus, and input behavior.

## Gradle targets and source sets
- [x] Add a JVM target for WinUI, for example `jvm("winuiJvm")`, configured for JDK 22 or newer because `kotlin-winrt` JVM support uses the Java Foreign Function and Memory API.
- [ ] Add a Windows native target, `mingwX64("winuiMingw")`, after `kotlin-winrt` implements mingw support.
- [x] Add `winuiMain` as a direct dependent of `commonMain`.
- [x] Add `winuiJvmMain` as a dependent of `winuiMain`.
- [ ] Add `winuiMingwMain` as a dependent of `winuiMain` after the mingw target is enabled.
- [x] Add matching test source sets for shared WinUI behavior and target-specific JVM behavior.
- [x] Do not make `winuiMain`, `winuiJvmMain`, or `winuiMingwMain` depend on `skikoMain`, `desktopMain`, AWT, Swing, or `org.jetbrains.skiko.SkiaLayer`.
- [x] Wire `winuiMain` to the local `kotlin-winrt` runtime without depending on checked-in `winrt-projections`.
- [x] Apply the local `kotlin-winrt` Gradle plugin for WinUI projection generation when running on JDK 22 or newer.
- [x] Declare the Windows App SDK NuGet package through the `winRt` DSL instead of directly depending on projection modules.
- [x] Explicitly declare the minimal WinRT projection `type(...)` entries required by `compose-ui` and repository-local samples for now.

## compose-ui platform abstractions
- [x] Add initial `WinUIComposeView` in `winuiMain`, mirroring the role of Android's root owner rather than desktop's Skiko scene layer.
- [x] Replace the initial `WinUIComposeView` placeholder with a real WinUI `Owner`, recomposer, and frame scheduler.
- [ ] Implement the `Owner` contract for WinUI: root `LayoutNode`, measure/layout scheduling, drawing invalidation, snapshot observation, semantics owner, focus owner, pointer processing, and test root support.
- [x] Wire the initial WinUI `Owner` to Compose `MeasureAndLayoutDelegate`, root constraints, root measure policy, and positioned-callback dispatch so layout work is no longer a no-op.
- [x] Attach the WinUI root `LayoutNode` to an initial `WinUIOwner` so reusable node lifecycle goes through common `LayoutNode.onReuse()` instead of a WinUI-only rootless path.
- [x] Implement a WinUI `setContent` entry point that creates a `Composition` with `UiApplier` and provides WinUI composition locals.
- [x] Add an initial `Window.setContent` entry point that creates a `WinUIComposeView`, installs its root into the WinUI `Window`, and returns the view for lifecycle management.
- [x] Add initial `Application { Window { ... } }` domains for WinUI JVM, including Windows App SDK bootstrap, RuntimeScope initialization, WinUI resource manager registration, and a window scope backed by `WinUIComposeView`.
- [x] Keep the WinUI application domain in `Application.winui.kt` and the WinUI window domain in `Window.winui.kt`.
- [x] Expose initial WinUI window capabilities from the generated `microsoft.ui.xaml.Window`: `extendsContentIntoTitleBar`, `WindowBackdrop`, and `WindowScope` access to `window`, `appWindow`, `compositor`, and `dispatcherQueue`.
- [x] Add concrete backdrop projection types for Mica and Desktop Acrylic through explicit Windows App SDK `type(...)` entries and validate Mica-to-DesktopAcrylic updates in the sample.
- [x] Add an initial `Window(onCloseRequest = ...)` close-request hook and close the native WinUI window when its Compose node is released.
- [x] Move initial WinUI application/window disposal onto the WinUI UI thread via `DispatcherQueue` for `exitApplication` and window close/removal paths.
- [ ] Broaden the initial close/removal work into full declarative multi-window lifetime semantics, including cancelable close policy if WinUI exposes a suitable pre-close event.
- [ ] Provide WinUI actuals for common platform hooks such as time, delayed posting, view configuration, window info, URI handling, haptics, semantics region, focusability, and platform velocity tracking.
- [x] Route initial WinUI JVM delayed posting back through the registered WinUI `DispatcherQueue` instead of running callbacks directly on the scheduler thread.
- [x] Provide initial WinUI composition locals for density, layout direction, view configuration, font resolution, URI handling, active-window focus state, and AppWindow-backed container size.
- [x] Provide initial WinUI clipboard hooks for `LocalClipboardManager`, `LocalClipboard`, `ClipEntry`, `ClipMetadata`, and `NativeClipboard`, with kotlin-winrt workarounds tracked as `KWINRT-010` through `KWINRT-012`.
- [x] Provide initial WinUI text input and IME integration hooks, with minimal stubs only where behavior is explicitly deferred.
- [ ] Provide WinUI accessibility integration hooks that can later map Compose semantics to UI Automation.
- [ ] Ensure lifecycle, retained values, and saveable state behavior have WinUI equivalents instead of relying on Android `ViewTree*Owner` APIs.

## WinUI rendering host
- [ ] Implement a WinUI-native rendering host that does not require an AWT component or Skiko AWT layer.
- [ ] Define the shared `winuiMain` rendering-facing abstraction used by `WinUIComposeView` to request frames, resize, and submit drawing work.
- [ ] Implement the JVM backend in `winuiJvmMain` using `kotlin-winrt`, Windows App SDK bootstrap, DispatcherQueue, and the JVM native interop path.
- [ ] Implement the mingwX64 backend in `winuiMingwMain` after `kotlin-winrt` provides mingw runtime actuals, using Kotlin/Native interop, COM/WinRT initialization, and native Windows APIs.
- [ ] Bind the Compose render output to a WinUI-hostable native surface or composition-backed surface owned by the WinUI target.
- [ ] Keep frame scheduling on the WinUI UI thread and ensure rendering invalidations are coalesced with Compose measure/layout work.
- [ ] Release native rendering resources, DispatcherQueue handles, COM references, and Windows App SDK registrations when the host is disposed.

## WinUIView interop
- [x] Add public `WinUIView` composable API for embedding a WinUI `UIElement` in Compose UI.
- [x] Match Android `AndroidView` lifecycle semantics: `factory` creates the view, `update` runs after creation and on recomposition, `onReset` opts into reuse, and `onRelease` runs once when the instance is permanently discarded.
- [x] Add initial `WinUIInteropProperties` for interaction, native accessibility participation, clipping/overlay behavior, and future WinUI-specific interop switches.
- [x] Add `InteropView` actual for WinUI `UIElement`.
- [x] Add initial `InteropViewGroup` for the WinUI wrapper used to host interop children.
- [x] Implement an initial WinUI view holder that owns the user `UIElement`, wrapper element, modifier updates, density updates, lifecycle callbacks, and native release cleanup hooks for event tokens.
- [x] Add initial WinUIView measure policy plus size and root-position propagation from Compose layout to the WinUI wrapper and `FrameworkElement` child.
- [x] Wire initial `WinUIInteropProperties.clipToBounds` support to a WinUI `RectangleGeometry` clip on the native wrapper.
- [x] Wire initial `WinUIInteropProperties.isUserInteractionEnabled` support to WinUI hit testing, user-element Tab focus, and `Control.isEnabled`.
- [ ] Wire `WinUIInteropProperties.isNativeAccessibilityEnabled` support to `AutomationProperties.AccessibilityView` after detached-element crashes and projection wrapper conflicts are resolved (`KWINRT-014`, `KWINRT-015`).
- [x] Use an initial Canvas-backed WinUI interop root container so wrapper position and z-order are controlled by Compose tree order instead of Grid layout behavior.
- [x] Implement a WinUI views handler/container that manages insertion, removal, z-order, clipping, and draw-order synchronization with the Compose tree.
- [x] Map Compose layout coordinates to WinUI bounds using unclipped bounds for the user element and clipped bounds for the wrapper.
- [ ] Support focus transfer between Compose focus targets and WinUI controls, including Tab and Shift+Tab traversal.
- [ ] Support basic pointer and keyboard input so native WinUI controls can handle their own interaction while Compose receives events outside interop views.
- [ ] Defer full nested scroll parity until after basic AndroidView-equivalent lifecycle, layout, focus, and input behavior is stable.

## kotlin-winrt dependencies
- [x] Use local `E:\Documents\AndroidStudioProjects\kotlin-winrt` as the development dependency source for WinRT runtime and generated WinUI projections.
- [ ] Verify `kotlin-winrt` full projection generation includes required WinUI types: `Application`, `Window`, `UIElement`, `FrameworkElement`, `Panel`, `Grid`, `Canvas`, `ContentControl`, `XamlControlsResources`, DispatcherQueue, focus/input event types, and required collection types.
- [ ] Reuse `kotlin-winrt` Windows App SDK bootstrap and resource manager support for unpackaged WinUI applications.
- [ ] Reuse `kotlin-winrt` COM reference management, event-token management, activation factory lookup, and XAML metadata provider support, including generated WinUI event sources after `KWINRT-016` is resolved.
- [ ] Add missing projection/runtime capabilities to `kotlin-winrt` first when compose-winui requires WinUI APIs that are not yet projected.
- [ ] Keep target-specific native interop code inside `winuiJvmMain`, and later `winuiMingwMain`; keep shared Compose/WinUI behavior in `winuiMain`.

## Tests and validation
- [x] Add a repository-local compose-winui sample that compiles against `:compose:ui:ui` and calls `WinUIView { Button() }`.
- [x] Add a runnable Windows App SDK/WinUI smoke task for the compose-winui sample so projection/runtime wiring is validated outside `kotlin-winrt`'s own samples.
- [x] Add repository-local WinUIView lifecycle smoke validation for factory, initial update, leaving composition, re-entering composition, and final release.
- [x] Add repository-local window smoke validation for `Application { Window { ... } }`, title propagation, `WindowScope.window`, and `WindowBackdrop.Mica`.
- [x] Add repository-local application-domain recomposition smoke validation for state-driven `Window` title, titlebar, and backdrop parameter updates.
- [x] Upgrade the compose-winui sample to render Compose content inside `WinUIComposeView` once the WinUI Owner, recomposer, and frame scheduler are implemented.
- [x] Add compile validation for the new WinUI JVM source set.
- [x] Add repository-local reusable `WinUIView` smoke validation for reset on deactivation, reactivation without recreation, and final release on disposal.
- [x] Add repository-local composition-local smoke validation for WinUI density, layout direction, view configuration, font resolver, and URI handler.
- [x] Add repository-local composition-local smoke validation for WinUI `LocalClipboardManager` and `LocalClipboard` plain-text round-trips.
- [x] Add repository-local FillableData smoke validation for WinUI text, boolean, list-index, and date-millis values.
- [x] Add repository-local `WindowInfo` smoke validation for active window focus plus positive container px/dp size.
- [x] Add repository-local owner disposal smoke coverage by disposing `WinUIComposeView` after lifecycle/reuse validation and using `dispose()` from the WinUI window release path.
- [x] Add repository-local WinUI text input session lifecycle smoke validation for session replacement and disposal cancellation while the native IME connection remains deferred.
- [x] Add repository-local WinUI `LocalLifecycleOwner` smoke validation for resumed composition state and destroyed state after `WinUIComposeView.dispose()`.
- [ ] Add compile validation for the new WinUI mingwX64 source set after `kotlin-winrt` supports mingw.
- [ ] Add tests proving WinUI source sets do not depend on `skikoMain`, `desktopMain`, AWT, Swing, or Skiko AWT classes.
- [x] Add lifecycle tests for `WinUIView`: factory once, update after creation, repeated update on state changes, reset on reuse, release on final disposal.
- [x] Add layout tests for bounds, clipping, z-order, placement, unplacement, and relayout after density or size changes.
- [x] Add repository-local WinUIView smoke validation for fixed Compose size and position propagation to the native WinUI wrapper and child element.
- [x] Add repository-local WinUIView smoke validation for installing a native clip rectangle from `clipToBounds=true`.
- [x] Add repository-local WinUIView smoke validation for updating interop properties and clearing native clip.
- [x] Add repository-local WinUIView smoke validation for updating native interaction state across hit testing, Tab focus, and `Control.isEnabled`.
- [x] Add repository-local WinUIView smoke validation for restoring native interaction state and clearing native clip on release.
- [ ] Add repository-local WinUIView smoke validation for native WinUI event-token registration and release cleanup after generated WinUI event sources are usable from compose-winui (`KWINRT-016`).
- [ ] Add repository-local WinUIView smoke validation for toggling native accessibility participation through `AutomationProperties.AccessibilityView` after `KWINRT-014` and `KWINRT-015` are resolved.
- [x] Add repository-local WinUIView smoke validation for unclipped native child bounds inside clipped Compose wrapper bounds.
- [x] Add repository-local WinUIView smoke validation for relayout after Compose size and position state changes.
- [x] Add repository-local WinUIView smoke validation for placement/unplacement without native recreation or release.
- [x] Add repository-local WinUIView smoke validation for interop insertion, middle removal, and z-order preservation.
- [x] Add repository-local WinUIView smoke validation for multiple WinUI control types: `Button`, `TextBox`, and `ToggleSwitch`.
- [x] Add repository-local WinUI owner focus smoke validation for Compose `FocusRequester` requests accepted while attempting native WinUI root focus.
- [ ] Add repository-local WinUIView smoke validation for Compose `FocusRequester` focus transfer to a native WinUI control after native `UIElement.focus(FocusState.Programmatic)` succeeds for embedded controls (`KWINRT-017`).
- [ ] Add focus and input tests for clicks, keyboard events, Tab traversal, and focus transfer between Compose and WinUI controls.
- [x] Add Windows JVM integration smoke test that shows Compose content with embedded WinUI `Button` and `ToggleSwitch`.
- [ ] Resolve the current Windows JVM sample shutdown blocker where JDK 25 FFM upcalls can abort with `upcallLinker.cpp:66` after early WinUIView smoke validation (`KWINRT-013`).
- [ ] Extend the Windows JVM integration smoke to a live WinUI `TextBox` after `KWINRT-008` is resolved.
- [ ] Add Windows mingwX64 integration smoke test for the same shared `WinUIView` sample after the mingw target is enabled.
- [ ] Add shutdown tests that verify composition disposal releases WinUI event tokens, COM references, rendering resources, and runtime registrations.
- [ ] Re-run existing Android, desktop, and iOS compose-ui interop tests to confirm the new WinUI target does not regress existing targets.

## kotlin-winrt blockers
- `KWINRT-016`: Generated WinUI event sources currently fail on JVM when compose-winui accesses `Button.click`, throwing `IncompatibleClassChangeError: Expecting non-static method ... WinRtGeneratedEventSourceRuntime.createEventSourceFactory(...)`. Re-enable repository-local native event-token registration/release smoke coverage after the generated projection and runtime call shape match again.
- `KWINRT-017`: Live embedded WinUI controls currently reject programmatic focus from compose-winui. The repository-local `WinUIView` focus-transfer smoke reached a native `Button` in the active WinUI window, but `Button.focus(FocusState.Programmatic)` returned `false`, so Compose correctly canceled the interop focus request. Re-enable Compose `FocusRequester` to native WinUI control smoke coverage after this succeeds.
