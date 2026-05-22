# kotlin-winrt issues found by compose-winui

This file tracks kotlin-winrt gaps that affect compose-winui. Use stable
`KWINRT-###` ids from compose-winui workaround comments so the workaround can be
removed when the upstream behavior is fixed.

Keep closed issues short. They should state the final outcome and validation
baseline, not every retest attempt.

## Current upstream triage

- **Open upstream/runtime:** `KWINRT-008`, `KWINRT-024`.
- **Open upstream/plugin:** none currently tracked from compose-winui.
- **Open compose-side workarounds:** `KWINRT-004`, `KWINRT-008`.
- **Compose/application policy, not kotlin-winrt helpers:** `KWINRT-012`
  clipboard synchronization and `KWINRT-019` focus timing.
- **Closed/fixed or superseded:** `KWINRT-001`, `KWINRT-002`, `KWINRT-003`,
  `KWINRT-005`, `KWINRT-006`, `KWINRT-007`, `KWINRT-009`,
  `KWINRT-010`, `KWINRT-011`, `KWINRT-013`, `KWINRT-014`, `KWINRT-015`,
  `KWINRT-016`, `KWINRT-017`, `KWINRT-018`, `KWINRT-020`, `KWINRT-021`,
  `KWINRT-022`, and `KWINRT-023`.

## KWINRT-001: Generated event source registry ABI mismatch

- **Status:** Fixed upstream in `external/kotlin-winrt` `56c9267c`.
- **Observed in:** generated WinRT event accessors such as `Window.closed`,
  `AppWindow.changed`, `AppWindow.closing`, pointer/key events, and text toolbar
  menu events.
- **Resolution:** compose-winui removed manual event registration workarounds
  and uses generated `WinRtEvent.add/remove`.
- **Validation:** `runWinUIViewSample` completes while using generated event
  accessors; `:compose:ui:ui` generated `event-sources.tsv` contains the WinUI
  event descriptors.

## KWINRT-002: Interface projection registry is not reliably initialized

- **Status:** Fixed upstream after `efc6cc8714ec59812ffb817cc29e424d1ff60d6f`.
- **Observed in:** `DispatcherQueue.hasThreadAccess`.
- **Resolution:** compose-winui removed its manual interface projection registry
  bootstrap.
- **Validation:** `compileKotlinWinuiJvm`, `winuiJvmTest`, and
  `runWinUIViewSample` pass without the compose-side bootstrap.

## KWINRT-003: Nullable XAML content properties throw on null ABI returns

- **Status:** Fixed upstream and retested in compose-winui with
  `external/kotlin-winrt` `97f15295`.
- **Observed in:** `ContentControl.content` after setting it to `null`.
- **Resolution:** Generated `ContentControl.content` is nullable in the compose
  projection, and no compose-side `WINRT_E_NULL_ABI_RETURN` workaround remains.
- **Validation:** `compileKotlinWinuiJvm` and `runWinUIViewSample` pass; the
  sample reads generated `ContentControl.content` for button content checks.

## KWINRT-004: Nullable XAML runtime-class properties are generated as non-null setters

- **Status:** Open.
- **Observed in:** `Window.systemBackdrop`.
- **Symptom:** The generated setter requires non-null `SystemBackdrop`, so
  compose-winui cannot use it to clear the backdrop. The current generated
  non-null setter path is also blocked by the broader interface projection
  member support gap in `KWINRT-008`.
- **compose-winui workaround:** `Window.winui.kt` uses the public
  `IWindow2.SYSTEMBACKDROP_SETTER_SLOT` ABI path for both non-null backdrops and
  `WindowBackdrop.None`. Search for `KWINRT-004`.
- **Validation:** `runWinUIViewSample` covers `Mica -> DesktopAcrylic -> None`.

## KWINRT-005: Windows.System.Launcher projection pulls invalid Package wrappers

- **Status:** Fixed upstream and retested in compose-winui with
  `external/kotlin-winrt` `97f15295`.
- **Observed in:** `Windows.System.Launcher` and dependent
  `Windows.ApplicationModel.Package` projections.
- **Resolution:** compose-winui uses the natural generated
  `Launcher.launchUriAsync(uri)` path.
- **Validation:** `compileKotlinWinuiJvm` and `runWinUIViewSample` pass with the
  generated one-argument overload.

## KWINRT-006: Nullable UIElement.Clip setter is generated as non-null

- **Status:** Fixed upstream after `efc6cc8714ec59812ffb817cc29e424d1ff60d6f`.
- **Observed in:** `Microsoft.UI.Xaml.UIElement.clip`.
- **Resolution:** compose-winui removed the workaround and uses the generated
  nullable `UIElement.clip` property for both setting and clearing.
- **Validation:** WinUI interop clipping tests and `runWinUIViewSample` pass.

## KWINRT-007: Canvas attached property setters can crash the WinUI runtime

- **Status:** Not reproduced with current kotlin-winrt; no upstream action
  without fresh native crash evidence.
- **Observed in:** `Canvas.setLeft(UIElement, Double)`,
  `Canvas.setTop(UIElement, Double)`, and `DependencyObject.setValue(...)` for
  `Canvas.LeftProperty` / `Canvas.TopProperty`.
- **Resolution:** compose-winui removed the margin fallback and now uses
  generated `Canvas.setLeft` / `Canvas.setTop` for WinUIView placement.
- **Validation:** `runWinUIViewSample` passes and verifies initial and updated
  `Canvas.getLeft` / `Canvas.getTop` wrapper positions.
- **Next evidence needed:** If this reproduces, attach `hs_err`, WER, or dump
  analysis before changing kotlin-winrt.

## KWINRT-008: Generated JVM interface projection members are incomplete for WinUI

- **Status:** Open upstream/runtime.
- **Observed in:** the real compose-winui `Application { Window { ... } }` path
  after authored `Application` CCW registration and resource staging.
- **Current finding:** This is deeper than the earlier
  `XamlControlsResources`/PRI symptom. Current kotlin-winrt can stage enough
  WinUI resources for the sample and no longer needs a repository-local
  `App.xaml`, but the JVM artifact runtime still rejects many generated public
  interface projection members needed by normal WinUI integration.
- **Managed evidence:** compose-winui hit
  `WinRtUnsupportedOperationException: Generated interface projection member
  ... is not supported by the JVM artifact runtime` for:
  `IApplication.getResources`, `IWindow.getDispatcherQueue`,
  `IWindow.getCompositor`, `IWindow.setContent`, `IWindow2.getAppWindow`,
  `IWindow2.getSystemBackdrop`, `IWindow2.setSystemBackdrop`, and
  `IRectangleGeometry.setRect`. `Panel.children` also needed a public metadata
  slot fallback rather than relying on the generated getter.
- **Projection registration evidence:** several generated interface factories
  were also missing from the lowercase runtime lookup until compose-winui
  registered aliases locally, including `microsoft.ui.xaml.IWindow`,
  `microsoft.ui.xaml.media.IRectangleGeometry`,
  `microsoft.ui.xaml.controls.IPanel`, and
  `windows.system.display.IDisplayRequest`.
- **compose-winui workaround:** `Application.winui.kt` registers required
  interface aliases for the current graph. `Window.winui.kt` uses public WinUI
  metadata slots for `Window.Content`, `Window.Compositor`, `Window.AppWindow`,
  and `Window.SystemBackdrop`; `WinUIPanelChildren.winui.kt` centralizes the
  public `Panel.Children` slot fallback; `WinUIView.winui.kt` uses dependency
  property read/write for `RectangleGeometry.Rect`.
- **Validation boundary:** after these workarounds the sample reaches the WinUI
  window path, Compose content updates, owner/input smoke paths, and text input
  session cancellation. The current native crash is tracked separately as
  `KWINRT-024`; it is not another unsupported generated interface member.

## KWINRT-009: Collection-returned XAML base wrappers cannot be rewrapped publicly

- **Status:** Fixed upstream for compose-winui wrapper paths.
- **Observed in:** XAML collections returning base `IInspectable`/`UIElement`
  wrappers that need public rewrap into generated runtime classes.
- **Resolution:** compose-winui removed repository-local rewrap helpers for the
  covered paths.
- **Validation:** `runWinUIViewSample` covers window content, interop insertion,
  and removal with generated wrappers.

## KWINRT-010: Static runtime class shells do not expose callable static members

- **Status:** Fixed upstream.
- **Observed in:** static WinRT helpers used by clipboard and other platform
  services.
- **Resolution:** compose-winui removed manual static activation/vtable
  workarounds where generated static members are now callable.
- **Validation:** `PlatformClipboard.winui.kt` compiles against generated
  clipboard APIs and `winuiJvmTest` passes.

## KWINRT-011: Repeated projection generation creates incompatible internal wrappers

- **Status:** Fixed upstream for the current compose-winui graph baseline.
- **Observed in:** base library -> winui library -> app graphs that generate
  the same WinRT type identity in multiple modules.
- **Resolution:** kotlin-winrt now merges the generated compiler-support
  artifacts and loads generated registries from caller classloaders.
- **Validation:** `compileKotlinWinuiJvm` passes with the compose-winui
  multi-module projection graph after syncing `external/kotlin-winrt`
  `5d35f2f9`.

## KWINRT-012: Clipboard async needs a dispatcher-safe helper

- **Status:** Closed as a kotlin-winrt issue.
- **Observed in:** synchronous clipboard reads in compose/application policy.
- **Resolution:** kotlin-winrt should provide generated APIs and async runtime
  lifetime handling, not a clipboard-specific synchronous cache.
- **compose-winui target:** Keep clipboard synchronization policy inside
  compose-winui/application code.

## KWINRT-013: JVM FFM upcall can crash while WinRT callbacks race shutdown

- **Status:** Fixed upstream; exact fixing commit not identified from
  compose-winui.
- **Observed in:** JVM shutdown with possible late WinRT callbacks.
- **Resolution:** After syncing current kotlin-winrt, compose-winui no longer
  reproduces the shutdown/upcall crash during sample validation. If a native
  crash returns, preserve and analyze `hs_err_pid*.log`, `replay_pid*.log`, WER,
  or dump files before reopening.
- **Validation:** The only crash log seen during this retest was a Gradle JVM
  native-memory failure during configuration/compilation, with no WinRT/upcall
  frames; it was not a `KWINRT-013` recurrence.

## KWINRT-014: Generated attached dependency property getters can rewrap with module-mangled internals

- **Status:** Fixed upstream and retested in compose-winui with
  `external/kotlin-winrt` `97f15295`.
- **Observed in:** attached dependency property getters such as automation
  properties.
- **Resolution:** compose-winui uses generated
  `AutomationProperties.accessibilityViewProperty`,
  `AutomationProperties.getAccessibilityView`, and
  `DependencyObject.clearValue`.
- **Validation:** `runWinUIViewSample` passes while applying
  `WinUIInteropProperties.isNativeAccessibilityEnabled=false` and reading back
  `AccessibilityView.Raw`.

## KWINRT-015: AutomationProperties.SetAccessibilityView can native-crash detached XAML elements

- **Status:** Not reproduced with current kotlin-winrt; no upstream action
  without fresh native crash evidence.
- **Observed in:** `AutomationProperties.SetAccessibilityView(...)` on detached
  or offscreen XAML elements.
- **Resolution:** compose-winui now wires
  `WinUIInteropProperties.isNativeAccessibilityEnabled` through generated
  `AutomationProperties.setAccessibilityView` and clears the override on
  release.
- **Validation:** `runWinUIViewSample` passes with native accessibility disabled
  for hosted WinUI buttons.
- **Next evidence needed:** If the crash reproduces, inspect native logs/dumps
  first. If the dump shows a WinUI detached/offscreen precondition, compose-winui
  should defer until attached/loaded.

## KWINRT-016: Generated Application.Start intrinsic is not lowered for compose-winui

- **Status:** Fixed upstream in local kotlin-winrt `ec8c5a52` and later
  verified in the compose graph.
- **Observed in:** generated `Application.start`.
- **Resolution:** compose-winui uses generated `Application.start` for the
  application entry path.
- **Validation:** `runWinUIViewSample` enters and exits the
  `Application { Window { ... } }` path successfully.

## KWINRT-017: WinUI Application access native-failfasts inside Application.Start callback

- **Status:** Superseded by the fixed generated `Application.start` path and
  resource bootstrap.
- **Observed in:** early `Application.current` / resource access during
  startup.
- **Resolution:** No active compose-winui workaround remains for this id.
- **Validation:** Covered by `runWinUIViewSample`.

## KWINRT-018: ContentControl.Content string getter did not round-trip assigned strings

- **Status:** Fixed upstream.
- **Observed in:** `ContentControl.content` when assigned a string.
- **Resolution:** compose-winui sample reads generated `ContentControl.content`
  directly for button content checks.
- **Validation:** `runWinUIViewSample` logs both initial and updated compose
  button content values.

## KWINRT-019: Live WinUI controls reject programmatic focus transfer from Compose

- **Status:** Closed as a kotlin-winrt blocker unless new ABI evidence appears.
- **Observed in:** `UIElement.focus(...)` timing on live WinUI controls.
- **Current finding:** Generated projection focus calls can return true once the
  element is loaded/layout-ready.
- **compose-winui target:** Move focus requests to loaded/layout-ready points
  rather than adding a kotlin-winrt helper or ABI workaround.
- **Validation:** `WinUIPlatformFocusOwnerTest` covers current focus behavior;
  `runWinUIViewSample` now covers compose-winui deferring native focus transfer
  until the embedded WinUI control is loaded/layout-ready.

## KWINRT-020: DisplayRequest default interface projection is not registered

- **Status:** Fixed upstream in `external/kotlin-winrt` `5d35f2f9`.
- **Observed in:** `DisplayRequest.requestActive()` /
  `DisplayRequest.requestRelease()` from downstream sample classpaths.
- **Resolution:** kotlin-winrt now loads generated registries from caller
  classloaders and uses direct interface projection registry tokens.
  compose-winui removed the keep-screen-on ABI fallback and now calls generated
  `DisplayRequest.requestActive()` / `requestRelease()` directly.
- **Validation:** with `external/kotlin-winrt` `1bd45755`,
  `compileKotlinWinuiJvm` passes and `runWinUIViewSample` reaches the generated
  `DisplayRequest.requestActive()` / `requestRelease()` path without projection
  registration failures.

## KWINRT-021: UIElement.ProtectedCursor requires a subclass access path

- **Status:** Closed as a kotlin-winrt bug. This is expected WinUI API shape.
- **Observed in:** `Microsoft.UI.Xaml.UIElement.ProtectedCursor`.
- **Resolution:** compose-winui removed the direct `IUIElementProtected` slot
  call. `WinUIRootContentHost` now uses a compose-owned `ContentControl`
  subclass, `WinUIRootContentControl`, which sets the protected
  `UIElement.ProtectedCursor` property through normal Kotlin protected access.
  `WinUIPointerCursorAdapter` maps Compose pointer icons to
  `InputSystemCursor` instances.
- **Validation:** `compileKotlinWinuiJvm`, `winuiJvmTest`, and
  `runWinUIViewSample` pass with the root subclass path.

## KWINRT-022: WinRT flags project as enum values instead of bitmasks

- **Status:** Fixed upstream in `external/kotlin-winrt` `56c9267c`.
- **Observed in:** `Windows.System.VirtualKeyModifiers`, read from
  `PointerRoutedEventArgs.keyModifiers`.
- **Resolution:** Generated flags are now value-class bitmasks; compose-winui
  removed manual modifier decoding.
- **Validation:** `WinUIPointerKeyboardModifiersTest` covers individual flags,
  combined flags, and unknown bits.

## KWINRT-023: WinUI authored Application did not register IApplicationOverrides

- **Status:** Fixed for compose-winui wiring after syncing
  `external/kotlin-winrt` `ad2b9df4`.
- **Observed in:** `:compose:ui:ui:winui-samples:runWinUIViewSample`, where
  WinUI failed fast with `E_NOINTERFACE` for
  `Microsoft.UI.Xaml.IApplicationOverrides`.
- **Resolution:** compose-winui now compiles kotlin-winrt's
  `generated/kotlin-winrt-authoring/src/main/kotlin` output into
  `compileKotlinWinuiJvm`, so the compiler plugin can lower authored
  construction sites to `WinRTAuthoringTypeDetailsRegistrar.register()`.
  The earlier hand-written `WinUIXamlApplication` registration workaround was
  removed.
- **Validation:** `compileKotlinWinuiJvm` succeeds and bytecode for
  `WinUIRootContentHost` contains a registrar call before constructing
  `WinUIRootContentControl`. `runWinUIViewSample` reaches the full current
  smoke path, including `IApplicationOverrides.onLaunched`, before hitting the
  separate teardown crash tracked as `KWINRT-024`.

## KWINRT-024: WinUI authored/runtime lifetime crash after full smoke

- **Status:** Open upstream/runtime.
- **Observed in:** `:compose:ui:ui:winui-samples:runWinUIViewSample` with
  `external/kotlin-winrt` `1bd45755`, still reproduced after syncing
  `402eb4d8` (`Register authored type details from constructors`). The sample passes
  application startup, Compose content update, owner/input smoke paths,
  generated event cleanup, saveable/retained state restore, and text input
  session cancellation before crashing.
- **Symptom:** the sample process exits with `NTSTATUS 0xC0000005` after logging
  `compose-winui-sample: text input session cancellation`.
- **Native evidence:** latest dumps after `ad2b9df4` are
  `%LOCALAPPDATA%\CrashDumps\java.exe.46428.dmp` and
  `%LOCALAPPDATA%\CrashDumps\java.exe(1).46428.dmp`. WER reports an initial
  `APPCRASH` in `Microsoft.UI.Xaml.dll` 3.1.8.0 with exception `0xc0000005` at
  offset `0x5c5ce`, followed by `BEX64` against
  `Microsoft.UI.Xaml.dll_unloaded` at offset `0x287490`. Local minidump parsing
  maps the first exception address to the staged `Microsoft.UI.Xaml.dll`
  (`0x5c5ce`) and the second to unloaded XAML code, consistent with the earlier
  teardown bucket.
- **Latest native evidence:** after `402eb4d8`, the latest dump is
  `%LOCALAPPDATA%\CrashDumps\java.exe.38292.dmp`. WER reports
  `APPCRASH java.exe`, faulting module `Microsoft.UI.Xaml.dll` 3.1.8.0,
  exception `0xc0000005`, offset `0x2a62a0`, report id
  `400906cd-83d1-4eb2-93c8-5af392ba4faa`. Local minidump parsing maps the
  exception address to the staged XAML DLL at `+0x2a62a0`; the failing access is
  a read from `0xffffffffffffffff`. The candidate native stack is now a
  XAML/CoreMessaging/UI-thread path rather than the earlier unloaded-DLL
  teardown stack.
- **Stack evidence:** exception thread is in XAML DLL teardown, not an FFM upcall
  stub:
  `Microsoft_UI_Xaml!ctl::ComPtr<ABI::Microsoft::UI::Xaml::IFrameworkElement>::InternalRelease`,
  `Microsoft_UI_Xaml!CCustomDependencyProperty::~CCustomDependencyProperty`,
  `Microsoft_UI_Xaml!DirectUI::DynamicMetadataStorage::~DynamicMetadataStorage`,
  `Microsoft_UI_Xaml!DirectUI::DynamicMetadataStorage::Destroy`,
  `Microsoft_UI_Xaml!DeinitializeDll`, then `combase!CoUninitialize` /
  `KERNELBASE!FreeLibraryAndExitThread`.
- **Current assessment:** this points at authored/custom dependency property
  metadata or UI-thread lifetime ordering in the kotlin-winrt authoring/runtime
  path. Do not treat it as a `KWINRT-013` shutdown/upcall recurrence unless a
  future dump shows a callback/upcall frame; the latest local parse still does
  not show an FFM upcall stub as the faulting frame.
