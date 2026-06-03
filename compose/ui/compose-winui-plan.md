# compose-winui implementation plan

## Architecture
- [x] Implement compose-winui as a standalone `compose-ui` platform target, comparable in responsibility to `androidMain`.
- [x] Keep compose-winui independent from `desktopMain`, AWT, Swing, and Skiko AWT/desktop runtime behavior.
- [x] Place `winuiMain` under `skikoMain` in the source-set graph because the upstream `uiMain` inheritance graph must remain unchanged.
- [x] Preserve WinUI/XAML actuals where `skikoMain` uses generic or Win32-backed behavior; use a WinUI-first compile bridge until `skiko-winui` can supply the rendering sources that should be shared.
- [x] Use `kotlin-winrt` as the WinRT and WinUI projection/runtime foundation instead of duplicating COM or Windows App SDK bootstrap code in `compose-ui`.
- [x] Share WinUI-specific Compose semantics in `winuiMain`, with `winuiJvmMain` providing current JVM runtime details and `winuiMingwMain` deferred until `kotlin-winrt` provides mingw support.
- [ ] Treat Android `AndroidView` interop as the behavioral reference for factory, update, reuse, detach, release, layout, focus, and input behavior.

## UIKit target parity gap
- [ ] Treat the existing UIKit target as the near-term architecture reference for production readiness, not just Android/Desktop. The WinUI target currently has a real `Owner`, recomposer, application/window domain, and basic `WinUIView` interop, but it is still well behind UIKit in rendering, accessibility, text input, native interop synchronization, and test depth.
- [ ] Close the rendering architecture gap with UIKit's `ComposeSceneMediator` + `MetalView` / `MetalRedrawer` stack by adding a WinUI-native scene/rendering host that owns frame scheduling, surface resize, drawing submission, interop synchronization, and disposal as one coherent layer.
- [ ] Close the accessibility architecture gap with UIKit's `AccessibilityMediator` by mapping Compose `SemanticsOwner` changes to UI Automation peers/elements, including focus, actions, scroll state, live-region-like notifications, and interop/native accessibility participation.
- [ ] Close the text input architecture gap with UIKit's `NativeTextInputView` / `ComposeTextInputView` stack by replacing the current WinUI text-input lifecycle stubs with a real IME/editing bridge, including selection, composition, keyboard visibility, software keyboard control where available, and text-toolbar coordination.
- [ ] Close the interop transaction gap with UIKit's `UIKitInteropContainer` by moving WinUI native child insertion, removal, z-order, layout, clipping, and native property updates into a render-synchronized transaction model instead of ad hoc root-content sync callbacks.
- [ ] Close the interop input/focus gap with UIKit's cooperative/non-cooperative interaction modes by supporting WinUIView native focus transfer, native pointer/keyboard handling inside hosted controls, Compose event delivery outside hosted controls, and predictable Tab / Shift+Tab traversal across Compose and WinUI controls.
- [ ] Close the platform-dependency gap by removing direct ABI event/property workarounds as `kotlin-winrt` generated event sources, interface registries, nullable WinRT properties, and resource/application lifecycle support become reliable in compose-winui.
- [ ] Close the test architecture gap with UIKit's `iosTest` / `uikitInstrumentedTest` coverage by splitting the current large WinUI sample smoke into focused tests for scene/rendering, interop, accessibility, keyboard/text input, window/lifecycle, pointer/scroll, resource loading, memory/disposal, and integration launch.
- [ ] Use the following rough maturity target when prioritizing work: Owner/composition/window is around 60-70% of the UIKit architecture shape, WinUIView interop basics are around 50-60%, and overall WinUI target maturity is around 35-45% until rendering host, UI Automation, real IME, transaction-based interop, and broader tests are implemented.

## Gradle targets and source sets
- [x] Add a JVM target for WinUI, for example `jvm("winuiJvm")`, configured for JDK 22 or newer because `kotlin-winrt` JVM support uses the Java Foreign Function and Memory API.
- [ ] Add a Windows native target, `mingwX64("winuiMingw")`, after `kotlin-winrt` implements mingw support.
- [x] Add `winuiMain` under `skikoMain` while keeping WinUI-specific actuals selected for the current WinUI JVM compile path.
- [x] Add `winuiJvmMain` as a dependent of `winuiMain`.
- [ ] Add `winuiMingwMain` as a dependent of `winuiMain` after the mingw target is enabled.
- [x] Add matching test source sets for shared WinUI behavior and target-specific JVM behavior.
- [x] Do not make `winuiMain`, `winuiJvmMain`, or `winuiMingwMain` depend on `desktopMain`, AWT, Swing, or `org.jetbrains.skiko.SkiaLayer`.
- [ ] Replace the temporary WinUI JVM compile-source bridge with a principled source-set split now that `io.github.compose-fluent:skiko-winui:0.0.0-SNAPSHOT` is resolvable, so shared Skiko scene/rendering code can be reused without compiling conflicting Skiko generic actuals. Initial Maven dependency wiring, a narrow `WinUISkikoRenderHost` adapter, and the first `WinUIComposeView` render-surface connection compile on 2026-06-02. `SKIKO-002` and `SKIKO-003` are fixed as of 2026-06-03; runtime sample validation reaches the full current smoke path. Latest kotlin-winrt snapshots `0.1.0-20260603.042831-23` / plugin `0.1.0-20260603.043142-4` still leave only the deferred `KWINRT-024` native process-exit crash, which is not a current skiko-winui integration blocker.
- [x] Wire `winuiMain` to the local `kotlin-winrt` runtime without depending on checked-in `winrt-projections`.
- [x] Apply the local `kotlin-winrt` Gradle plugin for WinUI projection generation when running on JDK 22 or newer.
- [x] Declare the Windows App SDK NuGet package through the `winRt` DSL instead of directly depending on projection modules.
- [x] Explicitly declare the minimal WinRT projection `type(...)` entries required by `compose-ui` and repository-local samples for now.

## compose-ui platform abstractions
- [x] Add initial `WinUIComposeView` in `winuiMain`, mirroring the role of Android's root owner rather than desktop's Skiko scene layer.
- [x] Replace the initial `WinUIComposeView` placeholder with a real WinUI `Owner`, recomposer, and frame scheduler.
- [ ] Implement the `Owner` contract for WinUI: root `LayoutNode`, measure/layout scheduling, drawing invalidation, snapshot observation, semantics owner, focus owner, pointer processing, and test root support.
- [x] Wire the initial WinUI `Owner` to Compose `MeasureAndLayoutDelegate`, root constraints, root measure policy, and positioned-callback dispatch so layout work is no longer a no-op.
- [x] Schedule WinUI `Owner` measure/layout passes from measure, relayout, positioned-callback, and snapshot-observed layout invalidations.
- [x] Schedule WinUI layout-completed listener delivery even when listener registration is the only pending layout work.
- [x] Wire WinUI `Owner` layout-node tracking into `RectManager` and remove stale rect entries on detach.
- [x] Wire initial WinUI `Owner` layer invalidation, size, position, clipping, and transform matrix state so layout bounds observers can see layer translations.
- [x] Attach the WinUI root `LayoutNode` to an initial `WinUIOwner` so reusable node lifecycle goes through common `LayoutNode.onReuse()` instead of a WinUI-only rootless path.
- [x] Implement a WinUI `setContent` entry point that creates a `Composition` with `UiApplier` and provides WinUI composition locals.
- [x] Add an initial `Window.setContent` entry point that creates a `WinUIComposeView`, installs its root into the WinUI `Window`, and returns the view for lifecycle management.
- [x] Add initial `Application { Window { ... } }` domains for WinUI JVM, including Windows App SDK bootstrap, RuntimeScope initialization, WinUI resource manager registration, and a window scope backed by `WinUIComposeView`.
- [x] Keep the WinUI application domain in `Application.winui.kt` and the WinUI window domain in `Window.winui.kt`.
- [x] Expose initial WinUI window capabilities from the generated `microsoft.ui.xaml.Window`: `extendsContentIntoTitleBar`, `WindowBackdrop`, and `WindowScope` access to `window`, `appWindow`, `compositor`, and `dispatcherQueue`.
- [x] Add concrete backdrop projection types for Mica and Desktop Acrylic through explicit Windows App SDK `type(...)` entries and validate Mica-to-DesktopAcrylic updates in the sample.
- [x] Add WinUI `WindowBackdrop.None` clearing through the `IWindow2.SystemBackdrop` ABI setter while `kotlin-winrt` generates a non-null setter.
- [x] Add an initial `Window(onCloseRequest = ...)` close-request hook and close the native WinUI window when its Compose node is released.
- [x] Keep WinUI `Window` node release idempotent after a native close so Compose state removal still completes the node lifecycle without a second close request.
- [x] Move initial WinUI application/window disposal onto the WinUI UI thread via `DispatcherQueue` for `exitApplication` and window close/removal paths.
- [x] Broaden the initial close/removal work into full declarative multi-window lifetime semantics, including cancelable close policy if WinUI exposes a suitable pre-close event.
- [ ] Provide WinUI actuals for common platform hooks such as time, delayed posting, view configuration, window info, URI handling, haptics, semantics region, focusability, and platform velocity tracking.
- [x] Route initial WinUI JVM delayed posting back through the registered WinUI `DispatcherQueue` instead of running callbacks directly on the scheduler thread.
- [x] Provide initial WinUI composition locals for density, layout direction, view configuration, font resolution, URI handling, active-window focus state, and AppWindow-backed container size.
- [x] Provide initial WinUI clipboard hooks for `LocalClipboardManager`, `LocalClipboard`, `ClipEntry`, `ClipMetadata`, and `NativeClipboard`; the synchronous `ClipboardManager` text cache is compose-winui policy, not a kotlin-winrt helper requirement.
- [x] Provide initial WinUI text input and IME integration hooks, with minimal stubs only where behavior is explicitly deferred.
- [x] Provide initial WinUI text-toolbar state tracking for copy/paste/cut/select-all/autofill menu requests.
- [x] Provide WinUI accessibility integration hooks that can later map Compose semantics to UI Automation.
- [x] Ensure lifecycle, retained values, and saveable state behavior have WinUI equivalents instead of relying on Android `ViewTree*Owner` APIs.

## WinUI rendering host
- [ ] Implement a WinUI-native rendering host that does not require an AWT component or Skiko AWT layer. The first `skiko-winui` surface is installed under the WinUI root content on 2026-06-02. Runtime sample validation now reaches the full smoke path after `SKIKO-003` root-content sync fixes; the known `KWINRT-024` native teardown crash is tracked separately as non-blocking.
- [x] Define the shared `winuiMain` rendering-facing abstraction used by `WinUIComposeView` to request frames, resize, and submit drawing work.
- [x] Add initial unit coverage for the `WinUISkikoRenderHost` adapter lifecycle: render invalidation forwarding, resize forwarding, frame-scheduler reuse, close ordering, idempotent close, suppression of post-close render/resize requests, and render diagnostics exposure.
- [ ] Implement the JVM backend in `winuiJvmMain` using `kotlin-winrt`, Windows App SDK bootstrap, DispatcherQueue, and the JVM native interop path.
- [ ] Implement the mingwX64 backend in `winuiMingwMain` after `kotlin-winrt` provides mingw runtime actuals, using Kotlin/Native interop, COM/WinRT initialization, and native Windows APIs.
- [ ] Bind the Compose render output to a WinUI-hostable native surface or composition-backed surface owned by the WinUI target. `WinUIComposeView` now draws its root `LayoutNode` into a `skiko-winui` Skia canvas through a WinUI `Canvas` root layer; fuller graphics actuals remain to be implemented, while the existing `KWINRT-024` teardown crash is deferred as non-blocking.
- [x] Keep frame scheduling on the WinUI UI thread and ensure rendering invalidations are coalesced with Compose measure/layout work. `WinUIComposeView` now starts the Skiko frame scheduler only after the WinUI root is loaded, so unattached roots can compose and run owner/interops tests without starting presentation callbacks.
- [x] Release native rendering resources, DispatcherQueue handles, COM references, and Windows App SDK registrations when the host is disposed.

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
- [x] Wire `WinUIInteropProperties.isNativeAccessibilityEnabled` support to `AutomationProperties.AccessibilityView`.
- [x] Use an initial Canvas-backed WinUI interop root container so wrapper position and z-order are controlled by Compose tree order instead of Grid layout behavior.
- [x] Implement a WinUI views handler/container that manages insertion, removal, z-order, clipping, and draw-order synchronization with the Compose tree.
- [x] Map Compose layout coordinates to WinUI bounds using unclipped bounds for the user element and clipped bounds for the wrapper.
- [x] Support focus transfer between Compose focus targets and WinUI controls, including Tab and Shift+Tab traversal.
- [x] Support basic pointer cooperation so native WinUI controls handle pointer input inside `WinUIView` bounds while Compose receives pointer input outside interop views.
- [x] Apply `Modifier.pointerHoverIcon(...)` to the WinUI root through a projected root element subclass that can set `UIElement.ProtectedCursor` through normal protected-member access; do not use direct protected-interface slot calls.
- [x] Support keyboard/native-focus cooperation so embedded WinUI controls handle their own focused keyboard input while Compose keeps predictable key dispatch outside interop views.
- [ ] Defer full nested scroll parity until after basic AndroidView-equivalent lifecycle, layout, focus, and input behavior is stable.

## kotlin-winrt dependencies
- [x] Consume `kotlin-winrt` from Maven Central snapshots for WinRT runtime, authoring, generated projection support, and the Gradle projection plugin.
- [ ] Verify `kotlin-winrt` full projection generation includes required WinUI types: `Application`, `Window`, `UIElement`, `FrameworkElement`, `Panel`, `Grid`, `Canvas`, `ContentControl`, `XamlControlsResources`, DispatcherQueue, focus/input event types, and required collection types.
- [ ] Reuse `kotlin-winrt` Windows App SDK bootstrap and resource manager support for unpackaged WinUI applications.
- [ ] Reuse `kotlin-winrt` COM reference management, event-token management, activation factory lookup, and XAML metadata provider support, including generated WinUI event sources after `KWINRT-016` is resolved.
- [ ] Add missing projection/runtime capabilities to `kotlin-winrt` first when compose-winui requires WinUI APIs that are not yet projected.
- [ ] Keep kotlin-winrt's KMP graph baseline covered with repository-local validation for customized source sets, transitive WinRT identity, support artifact merging, and multi-module generated projection ownership.
- [ ] Follow kotlin-winrt's WinUI resource bootstrap with full Windows SDK PRI pipeline alignment: `Page`, `ApplicationDefinition`, `PRIResource`, manifest default language, `ProjectPriIndexName`, `AppxPriInitialPath`, duplicate filtering, and `WinAppSdkExpandPriContent` behavior.
- [ ] Keep target-specific native interop code inside `winuiJvmMain`, and later `winuiMingwMain`; keep shared Compose/WinUI behavior in `winuiMain`.

## Tests and validation
- [x] Add a repository-local compose-winui sample that compiles against `:compose:ui:ui` and calls `WinUIView { Button() }`.
- [x] Add a runnable Windows App SDK/WinUI smoke task for the compose-winui sample so projection/runtime wiring is validated outside `kotlin-winrt`'s own samples.
- [x] Add repository-local WinUIView lifecycle smoke validation for factory, initial update, leaving composition, re-entering composition, and final release.
- [x] Add repository-local window smoke validation for `Application { Window { ... } }`, title propagation, `WindowScope.window`, and `WindowBackdrop.Mica`.
- [x] Add repository-local secondary `Window` close/removal smoke validation for native pre-close cancellation triggering `onCloseRequest` and Compose state removal while another WinUI window remains active.
- [x] Add repository-local application-domain recomposition smoke validation for state-driven `Window` title, titlebar, and backdrop parameter updates.
- [x] Upgrade the compose-winui sample to render Compose content inside `WinUIComposeView` once the WinUI Owner, recomposer, and frame scheduler are implemented.
- [x] Add compile validation for the new WinUI JVM source set.
- [x] Add repository-local reusable `WinUIView` smoke validation for reset on deactivation, reactivation without recreation, and final release on disposal.
- [x] Add repository-local composition-local smoke validation for WinUI density, layout direction, view configuration, font resolver, and URI handler.
- [x] Add repository-local architecture-owner composition-local smoke validation for WinUI lifecycle, saved-state registry, and ViewModel store owners.
- [x] Add repository-local architecture-owner smoke validation for WinUI `viewModel()` creation with `SavedStateHandle`, retention across `disposeComposition()`, and `ViewModelStore` clearing on `dispose()`.
- [x] Add repository-local architecture-owner smoke validation for WinUI `LocalNavigationEventDispatcherOwner` host-default provisioning.
- [x] Add repository-local composition-local smoke validation for WinUI `LocalClipboardManager` and `LocalClipboard` plain-text round-trips.
- [x] Add repository-local FillableData smoke validation for WinUI text, boolean, list-index, and date-millis values.
- [x] Add repository-local `WindowInfo` smoke validation for active window focus plus positive container px/dp size.
- [x] Add repository-local `WindowInfo` smoke validation for `Window.Activated` focus loss when a secondary WinUI window is activated and closed.
- [x] Add repository-local owner disposal smoke coverage by disposing `WinUIComposeView` after lifecycle/reuse validation and using `dispose()` from the WinUI window release path.
- [x] Add repository-local WinUI text input session lifecycle smoke validation for session replacement and disposal cancellation while the native IME connection remains deferred.
- [x] Add repository-local WinUI owner unit validation for text input session replacement canceling the previous platform input method session.
- [x] Add repository-local WinUI `LocalLifecycleOwner` smoke validation for resumed composition state and destroyed state after `WinUIComposeView.dispose()`.
- [x] Add repository-local WinUI `rememberSaveable` smoke validation for restoring state across `WinUIComposeView.disposeComposition()` and subsequent `setContent()`.
- [x] Add repository-local WinUI `retain` smoke validation for restoring retained values across `WinUIComposeView.disposeComposition()` and subsequent `setContent()`.
- [ ] Add compile validation for the new WinUI mingwX64 source set after `kotlin-winrt` supports mingw.
- [x] Add tests proving WinUI source sets do not depend on `desktopMain`, AWT, Swing, or Skiko AWT classes, and that WinUI keeps its own XAML/WinRT actuals where Skiko has generic or Win32-backed behavior.
- [x] Add lifecycle tests for `WinUIView`: factory once, update after creation, repeated update on state changes, reset on reuse, release on final disposal.
- [x] Add layout tests for bounds, clipping, z-order, placement, unplacement, and relayout after density or size changes.
- [x] Add repository-local WinUIView smoke validation for fixed Compose size and position propagation to the native WinUI wrapper and child element.
- [x] Add repository-local WinUIView smoke validation for installing a native clip rectangle from `clipToBounds=true`.
- [x] Add repository-local WinUIView smoke validation for updating interop properties and clearing native clip.
- [x] Add repository-local WinUIView smoke validation for updating native interaction state across hit testing, Tab focus, and `Control.isEnabled`.
- [x] Add repository-local WinUIView smoke validation for restoring native interaction state and clearing native clip on release.
- [x] Add repository-local WinUIView smoke validation for native WinUI event-token registration and release cleanup after generated WinUI event sources are usable from compose-winui (`KWINRT-016`).
- [x] Add repository-local WinUIView smoke validation for toggling native accessibility participation through `AutomationProperties.AccessibilityView`.
- [x] Add repository-local WinUIView smoke validation for unclipped native child bounds inside clipped Compose wrapper bounds.
- [x] Add repository-local WinUIView smoke validation for relayout after Compose size and position state changes.
- [x] Add repository-local WinUI owner smoke validation for snapshot-observed layout state invalidating measure/layout without recomposition.
- [x] Add repository-local WinUI owner smoke validation for layout-completed listener dispatch after installing an `OnPlacedModifier`.
- [x] Add repository-local WinUI owner smoke validation for `onLayoutRectChanged` occlusion and detach cleanup.
- [x] Add repository-local WinUI owner smoke validation for graphics-layer translation propagating through `onLayoutRectChanged` bounds.
- [x] Add repository-local WinUI owner smoke validation for `RootForTest.sendKeyEvent` dispatching through preview and key handlers.
- [x] Add repository-local WinUI owner smoke validation for Tab and Shift+Tab focus traversal through `RootForTest.sendKeyEvent`.
- [x] Add repository-local WinUI owner smoke validation for `RootForTest.semanticsOwner` exposing semantics nodes and properties.
- [x] Add repository-local WinUI owner smoke validation for end-apply listener de-duplication and same-cycle draining.
- [x] Add repository-local WinUI owner smoke validation for `RootForTest.setUncaughtExceptionHandler` routing layout exceptions.
- [x] Add repository-local WinUI owner test validation for keep-screen-on, accessibility testing controls, frame-rate vote, scroll-change dispatch, and root invalidation state hooks.
- [x] Add repository-local WinUI owner test validation for semantics/layout event routing, root invalidation callbacks, scroll callbacks, and sensitive-content state hooks.
- [x] Add repository-local WinUI owner test validation for out-of-frame executor scheduling and `RootForTest.measureAndLayoutForTest()` draining.
- [x] Add repository-local WinUI owner test validation for window/screen coordinate mapping delegation.
- [x] Add repository-local WinUI owner test validation for text-toolbar shown/hidden state and request callbacks.
- [x] Add repository-local WinUI owner test validation that disposal suppresses pending out-of-frame work, window/interop updates, end-apply listeners, and future owner scheduling callbacks.
- [x] Add repository-local WinUI owner test validation that disposal releases active keep-screen-on and sensitive-content platform state and suppresses future state changes.
- [x] Add repository-local WinUI platform hook unit validation for URI scheme handling and WinUI view-configuration defaults.
- [x] Wire WinUI keep-screen-on to `Windows.System.Display.DisplayRequest` and sensitive content to top-level window capture protection.
- [x] Add repository-local WinUI accessibility bridge test validation for semantics/layout/scroll invalidation batching and test-forced accessibility enablement.
- [x] Add repository-local WinUI owner smoke validation for `RootForTest.sendIndirectPointerEvent` dispatching through the focused indirect pointer input node.
- [x] Add repository-local WinUI owner smoke validation for basic pointer press/release dispatch through `PointerInputModifierNode`.
- [x] Add repository-local WinUI owner smoke validation for pointer move dispatch and coordinate propagation through `PointerInputModifierNode`.
- [x] Add repository-local WinUI owner smoke validation for root pointer enter/exit dispatch through `PointerInputModifierNode`.
- [x] Add repository-local WinUI owner smoke validation for root pointer-wheel scroll dispatch and `scrollDelta` propagation through `PointerInputModifierNode`.
- [x] Add repository-local WinUI owner smoke validation for canceling active pointer input when the owner is disposed.
- [x] Add repository-local WinUIView smoke validation that pointer events inside native interop bounds are left for WinUI while outside events still dispatch to Compose.
- [x] Route WinUI interop view layout changes through `Owner.onInteropViewLayoutChange` so native root synchronization is scheduled when hosted views move or resize.
- [x] Add repository-local WinUI interop transaction queue unit coverage for dropped frames, late completions, empty transactions, merge behavior, and ring-buffer overflow.
- [x] Add repository-local WinUIView smoke validation for placement/unplacement without native recreation or release.
- [x] Add repository-local WinUIView smoke validation for interop insertion, middle removal, and z-order preservation.
- [x] Add repository-local WinUIView smoke validation for multiple WinUI control types: `Button`, `TextBox`, and `ToggleSwitch`.
- [x] Add repository-local WinUI owner focus smoke validation for Compose `FocusRequester` requests accepted while attempting native WinUI root focus.
- [x] Add repository-local WinUI platform focus owner unit validation for native root focus attempt, exception handling, and clear-focus delegation.
- [x] Route focused embedded WinUI view bounds into `PlatformFocusOwner.getEmbeddedViewFocusRect()` so focus search can use native interop geometry once embedded focus succeeds.
- [x] Add repository-local WinUIView smoke validation for Compose `FocusRequester` focus transfer to a native WinUI control after compose-winui moves the native focus request to a loaded/layout-ready point.
- [x] Add focus and input smoke coverage for Compose focus targets around hosted WinUI controls, keyboard events, Compose pointer delivery outside hosted controls, and hosted-control pointer exclusion. Tab traversal and native focus transfer are covered by the adjacent root traversal and loaded/layout-ready native focus smokes.
- [x] Add repository-local WinUI key input processor coverage proving native-child key events are left to WinUI and do not update Compose modifier state.
- [x] Add repository-local WinUI drag-and-drop manager coverage for platform drag session start, move, changed, drop, exit, end, and target-interest cleanup.
- [x] Wire WinUI root XAML drag/drop events into Compose drag-and-drop target dispatch with event-token cleanup on `WinUIComposeView.dispose()`.
- [x] Align WinUI `PopupProperties` value equality with other Compose platforms and cover all public fields in WinUI JVM tests.
- [x] Replace the initial WinUI inline `Popup` placeholder with a zero-size popup host layout that applies popup semantics and position-provider placement without depending on skiko rendering layers.
- [x] Replace the initial WinUI inline `Dialog` placeholder with a zero-size centered dialog host layout, dialog semantics, and `DialogProperties` value equality coverage.
- [x] Add Windows JVM integration smoke test that shows Compose content with embedded WinUI `Button` and `ToggleSwitch`.
- [x] Retest shutdown/upcall validation after current kotlin-winrt sync: `KWINRT-013` is treated as fixed upstream, though compose-winui did not identify the exact fixing commit. If `upcallLinker.cpp:66` returns, preserve and analyze `hs_err`, WER, or dump output before reopening it.
- [x] Extend the Windows JVM integration smoke to a live WinUI `TextBox` after `KWINRT-008` is resolved.
- [ ] Add Windows mingwX64 integration smoke test for the same shared `WinUIView` sample after the mingw target is enabled.
- [ ] Add shutdown tests that verify composition disposal releases WinUI event tokens, COM references, rendering resources, and runtime registrations.
- [ ] Split the current monolithic `runWinUIViewSample` smoke into focused WinUI JVM test/smoke suites, mirroring UIKit's split between unit/instrumented coverage: scene/rendering, interop lifecycle/layout/input, accessibility, text input/keyboard, window/lifecycle, pointer/scroll, resource loading, disposal/leaks, and launch integration.
- [ ] Add WinUI rendering-host tests comparable to UIKit `MetalRedrawer` and layer tests: resize, invalidation coalescing, frame pacing, render/interop transaction ordering, disposal after pending frame callbacks, and nonblank surface output once drawing is implemented.
- [x] Add initial WinUI rendering-host adapter unit tests for resize, render requests, frame-scheduler reuse, idempotent close, post-close suppression, and render diagnostics exposure.
- [x] Add repository-local attached-window Skiko render diagnostics smoke coverage that verifies a window-owned `WinUIComposeView` reaches a render frame without `WinUISkikoRenderHost` reporting a render failure.
- [ ] Add WinUI UI Automation tests comparable to UIKit accessibility tests: semantics tree projection, accessibility focus, custom actions, scroll actions, live-region notifications, interop native accessibility inclusion/exclusion, and geometry updates after layout.
- [ ] Add WinUI text input and keyboard tests comparable to UIKit keyboard/text-field tests: focus entry, IME session lifecycle, composing text, selection updates, clipboard/edit menu interaction, software keyboard show/hide behavior where available, and keyboard-driven focus order.
- [ ] Re-run existing Android, desktop, and iOS compose-ui interop tests to confirm the new WinUI target does not regress existing targets.

## kotlin-winrt blockers
- `KWINRT-024`: Deferred / non-blocking after the 2026-06-03 latest snapshot retest.
  `runWinUIViewSample` still reaches the full current smoke path, then exits with
  `NTSTATUS 0xC0000005`; Store WinDbg dump evidence points to a XAML
  fail-fast/stowed exception after the final smoke log, not an FFM upcall frame.
  Do not block current skiko-winui integration or follow-on compose-winui work
  on this issue.
- `KWINRT-023`: Fixed for compose-winui by compiling kotlin-winrt generated
  authoring sources into the WinUI JVM target; the hand-written
  `WinUIXamlApplication` authoring registration workaround has been removed.
- `KWINRT-008`: Mostly fixed for the compose-winui validation path; `App.xaml`
  is no longer required. Keep removing interface/property workarounds only after
  exact compose graph retests prove the generated projection path works.
- `KWINRT-013`: Treated as fixed upstream. The exact fixing commit was not
  identified from compose-winui; reopen only with fresh native crash evidence
  and log/dump analysis.
- `KWINRT-020`: Fixed by the current registry/classloader changes. compose-winui
  removed the keep-screen-on ABI fallback and now uses generated
  `DisplayRequest.requestActive()` / `requestRelease()` directly.
- KMP graph baseline: Keep testing customized source sets, transitive identity,
  and support artifact merging. Do not regress to a single-module JVM sample as
  the only kotlin-winrt validation shape.

## skiko-winui status

- `SKIKO-004`: Open compose-side integration / upstream triage. An unattached
  `WinUIComposeView` render smoke can hang in `DirectContext.flushAndSubmit`;
  this differs from skiko's own sample because the skiko sample renders through
  a layer already hosted by a real WinUI window. Compose-winui now attaches the
  direct `microsoft.ui.xaml.Window.setContent` root before starting composition,
  defers `WinUIComposeView` frame-scheduler startup until the root is loaded,
  keeps diagnostics unit-tested, and validates an attached-window render
  diagnostics frame in the repository-local sample. Nonblank frame validation
  is still deferred until there is a stable attached-window pixel-read path.
- `SKIKO-003`: Closed on 2026-06-03. `WinUIComposeView.updateRootContent` now
  flushes pending root-content transactions even when the interop overlay
  identity is unchanged, and the sample validates WinUIView overlay children
  separately from the Skiko render base child. With JDK 25,
  `:compose:ui:ui:compileKotlinWinuiJvm` passes and
  `:compose:ui:ui:winui-samples:runWinUIViewSample` reaches the full current
  smoke path. The known `KWINRT-024` native teardown crash is deferred as
  non-blocking.
- `SKIKO-002`: Closed in the 2026-06-03 Maven snapshots. The published
  skiko-winui and kotlin-winrt snapshots carry the generic WinRT support needed
  by `WinUISkiaLayer`; the sample no longer fails with the generic-support
  `NoSuchMethodError`.
- `SKIKO-001`: Closed. The Maven snapshot coordinates are
  `io.github.compose-fluent:skiko-winui:0.0.0-SNAPSHOT` plus
  `io.github.compose-fluent:skiko-winui-windows:0.0.0-SNAPSHOT`.
