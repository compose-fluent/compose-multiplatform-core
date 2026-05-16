# kotlin-winrt issues found by compose-winui

This file tracks kotlin-winrt gaps that affect compose-winui. Use stable
`KWINRT-###` ids from compose-winui workaround comments so the workaround can be
removed when the upstream behavior is fixed.

Keep closed issues short. They should state the final outcome and validation
baseline, not every retest attempt.

## Current upstream triage

- **Open upstream/runtime:** `KWINRT-013` only if a fresh native crash log or
  dump identifies which shutdown callback still enters an FFM upcall.
- **Open upstream/plugin:** `KWINRT-020` for generated interface projection
  registration from merged compiler-support. `KWINRT-008` has a follow-up for
  full Windows SDK PRI pipeline parity beyond the current sample coverage.
- **Open compose-side workarounds:** `KWINRT-004` and `KWINRT-020`.
- **Compose/application policy, not kotlin-winrt helpers:** `KWINRT-012`
  clipboard synchronization and `KWINRT-019` focus timing.
- **Closed/fixed or superseded:** `KWINRT-001`, `KWINRT-002`, `KWINRT-003`,
  `KWINRT-005`, `KWINRT-006`, `KWINRT-007`, `KWINRT-009`, `KWINRT-010`,
  `KWINRT-014`, `KWINRT-015`, `KWINRT-016`, `KWINRT-017`, `KWINRT-018`,
  `KWINRT-021`, and `KWINRT-022`.

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
  compose-winui cannot use it to clear the backdrop.
- **compose-winui workaround:** `Window.winui.kt` uses the generated setter for
  non-null backdrops and a narrowly scoped null ABI call for
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

## KWINRT-008: XamlControlsResources cannot be installed from compose-winui Application

- **Status:** Fixed for compose-winui sample; upstream follow-up remains for full
  Windows SDK PRI pipeline parity.
- **Observed in:** `Application.resources` and `XamlControlsResources`.
- **Resolution:** compose-winui no longer needs a repository-local `App.xaml`;
  kotlin-winrt stages and initializes enough WinUI resources for the sample.
- **Validation:** `runWinUIViewSample` validates a live `TextBox` in the
  `Application { Window { ... } }` path.
- **Follow-up target:** Align the Gradle plugin with Windows SDK targets for
  `Page`, `ApplicationDefinition`, `PRIResource`, manifest default language,
  `ProjectPriIndexName`, `AppxPriInitialPath`, duplicate filtering, and
  `WinAppSdkExpandPriContent` behavior.

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

- **Status:** Partially fixed upstream; still relevant as a multi-module graph
  baseline.
- **Observed in:** base library -> winui library -> app graphs that generate
  the same WinRT type identity in multiple modules.
- **Current state:** The exact active failure moved to `KWINRT-020` for runtime
  registration of merged interface projection support. Keep customized source
  sets, transitive identity, and support artifact merging in repository-local
  validation.

## KWINRT-012: Clipboard async needs a dispatcher-safe helper

- **Status:** Closed as a kotlin-winrt issue.
- **Observed in:** synchronous clipboard reads in compose/application policy.
- **Resolution:** kotlin-winrt should provide generated APIs and async runtime
  lifetime handling, not a clipboard-specific synchronous cache.
- **compose-winui target:** Keep clipboard synchronization policy inside
  compose-winui/application code.

## KWINRT-013: JVM FFM upcall can crash while WinRT callbacks race shutdown

- **Status:** Not currently reproduced after current kotlin-winrt updates and
  compose workaround removal.
- **Observed in:** JVM shutdown with possible late WinRT callbacks.
- **Next evidence needed:** If a native crash returns, preserve and analyze
  `hs_err_pid*.log`, `replay_pid*.log`, WER, or dump files to identify the
  callback category before changing runtime shutdown ownership.
- **Resolution target:** If the callback is DispatcherQueue, timer, frame clock,
  text input, or another delegate/upcall, bring that delegate handle into the
  runtime shutdown registry rather than adding a WinUI special case.

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

- **Status:** Still open in compose-winui with `external/kotlin-winrt`
  `35829fd0`.
- **Observed in:** `DisplayRequest.requestActive()` /
  `DisplayRequest.requestRelease()` from downstream sample classpaths.
- **Symptom:** The merged compiler-support artifact contains
  `Windows.System.Display.IDisplayRequest`, but the downstream sample still
  throws `Generated interface projection factory for
  'windows.system.display.IDisplayRequest' is not registered` when calling the
  generated `DisplayRequest.requestActive()`.
- **compose-winui workaround:** `WinUIDisplayRequestController.winui.kt` still
  calls the default interface ABI directly for keep-screen-on. Search for
  `KWINRT-020`.
- **Resolution target:** Ensure merged compiler-support interface-native
  projection entries are loaded and registered at runtime in downstream
  multi-module applications.
- **Validation:** Direct generated calls still fail in `runWinUIViewSample`
  with `35829fd0`; restoring the narrow ABI fallback lets the sample pass.

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
