# kotlin-winrt issues found by compose-winui

This file tracks kotlin-winrt gaps that currently affect compose-winui. Each
issue has a stable id so compose-winui workarounds can reference it directly.

## Current upstream triage

- **Still worth upstream runtime work:** `KWINRT-013` if it reproduces with a
  native crash log or dump; classify the callback type before adding shutdown
  lifetime handling.
- **Still worth upstream Gradle/plugin work:** `KWINRT-008` follow-up for full
  Windows SDK PRI pipeline alignment, and the KMP graph baseline covering
  customized source sets, transitive identity, and support artifact merging.
- **Needs generated-artifact inspection before runtime changes:** `KWINRT-020`
  should be checked against duplicate FQNs, `type-index.tsv`,
  `WinRTInterfaceProjectionRegistry.class`, and exact module origin in the
  compose-winui graph.
- **Compose/application policy, not kotlin-winrt helpers:** `KWINRT-012`
  synchronous clipboard caching and `KWINRT-019` focus timing should be handled
  in compose-winui unless new ABI evidence appears.
- **Crash evidence required before upstream action:** `KWINRT-007` and
  `KWINRT-015` should be driven by native crash logs or dumps. If the dump
  points to WinUI detached/offscreen preconditions, compose-winui should defer
  calls until attached/loaded.
- **Do not change runtime from stale documentation alone:** `KWINRT-001`,
  `KWINRT-003`, `KWINRT-004`, `KWINRT-005`, `KWINRT-011`, `KWINRT-014`,
  `KWINRT-021`, and `KWINRT-022` need exact-graph retests or artifact checks
  before being treated as active upstream runtime bugs.

## KWINRT-001: Generated event source registry ABI mismatch

- **Status:** Fixed upstream. Verified after updating `external/kotlin-winrt`
  to `56c9267c` (`Fix multi-module WinUI event source descriptors`) and
  switching compose-winui window, key, pointer, and text-toolbar event
  registration back to generated `WinRtEvent` accessors.
- **Observed in:** `Window.closed`, `AppWindow.changed`,
  `AppWindow.closing`, and generated WinRT event accessors
- **Symptom:** Accessing generated event properties can fail during
  `WinRTEventProjectionHelpers.installEventSources(...)` with:
  `IncompatibleClassChangeError: Expecting non-static method ... createEventSourceFactory(...)`.
- **Impact on compose-winui:** Declarative window lifecycle and `WindowInfo`
  updates need WinUI close/changed events, but using generated event accessors
  currently crashes the sample.
- **compose-winui workaround:** Removed. `Window.winui.kt`,
  `WinUIKeyInputAdapter.winui.kt`, `WinUIPointerInputAdapter.winui.kt`, and
  `WinUITextToolbar.winui.kt` now use generated `WinRtEvent.add/remove`.
- **Resolution target:** Keep generated event registration covered by
  repository-local `winuiJvmTest` and `runWinUIViewSample` validation.

## KWINRT-002: Interface projection registry is not reliably initialized

- **Status:** Fixed upstream; compose workaround removed after updating
  `external/kotlin-winrt` to `efc6cc8714ec59812ffb817cc29e424d1ff60d6f`.
- **Observed in:** `DispatcherQueue.hasThreadAccess`
- **Symptom:** Even after declaring `type("Microsoft.UI.Dispatching.IDispatcherQueue2")`,
  using `dispatcherQueue.hasThreadAccess` can fail at runtime with:
  `Generated interface projection factory for 'microsoft.ui.dispatching.IDispatcherQueue2' is not registered.`
- **Impact on compose-winui:** WinUI coroutine dispatch should avoid redundant
  `DispatcherQueue.tryEnqueue(...)` calls when already on the UI thread.
- **compose-winui workaround:** Removed. `WinUIComposeView.winui.kt` no longer
  manually bootstraps the generated interface projection registry.
- **Validation note:** Deleting the compose-winui module `build` directories and
  re-running with `--no-configuration-cache --rerun-tasks` did not resolve
  this; the failure is not just Gradle configuration-cache or local build
  directory reuse.
- **Resolution target:** Ensure generated interface projection registries are
  loaded before any generated runtime class attempts to wrap secondary
  interfaces such as `IDispatcherQueue2`.

## KWINRT-003: Nullable XAML content properties throw on null ABI returns

- **Status:** Open
- **Observed in:** `ContentControl.content` after setting it to `null`
- **Symptom:** Reading `ContentControl.content` after clearing the property can
  throw `IllegalStateException: WINRT_E_NULL_ABI_RETURN` instead of returning
  `null`.
- **Impact on compose-winui:** WinUI interop wrappers need to detach user
  `UIElement` instances on release and tests need to verify that the wrapper no
  longer owns the native child.
- **compose-winui workaround:** Release paths may still clear
  `ContentControl.content`, but validation code treats `WINRT_E_NULL_ABI_RETURN`
  as a null content result. Search for `KWINRT-003`.
- **Resolution target:** Generate nullable Kotlin property types, or otherwise
  map null WinRT ABI returns to `null`, for XAML content properties that can be
  unset.

## KWINRT-004: Nullable XAML runtime-class properties are generated as non-null setters

- **Status:** Open
- **Observed in:** `Window.systemBackdrop`
- **Symptom:** `Window.systemBackdrop` is generated as a non-null
  `SystemBackdrop` property, so compose-winui cannot call the projected setter
  with `null` to clear an existing backdrop.
- **Impact on compose-winui:** `WindowBackdrop.None` must be able to represent
  both the initial no-backdrop state and a runtime transition from `Mica`,
  `DesktopAcrylic`, or `Custom` back to no backdrop.
- **compose-winui workaround:** `Window.winui.kt` uses the generated setter for
  non-null backdrops and invokes `IWindow2.SystemBackdrop` directly with a null
  ABI pointer when `WindowBackdrop.None` needs to clear an existing backdrop.
  Search for `KWINRT-004`.
- **Resolution target:** Generate nullable Kotlin setters for WinRT
  runtime-class properties whose metadata permits null, including
  `Microsoft.UI.Xaml.Window.SystemBackdrop`.

## KWINRT-005: Windows.System.Launcher projection pulls invalid Package wrappers

- **Status:** Partially fixed upstream; compose workaround narrowed after
  updating `external/kotlin-winrt` to
  `efc6cc8714ec59812ffb817cc29e424d1ff60d6f`.
- **Observed in:** `Windows.System.Launcher`
- **Symptom:** Declaring `type("Windows.System.Launcher")` can generate
  invalid dependent projections under `Windows.ApplicationModel`, including
  `AppInfo.package` and `PackageContentGroup.package` return type mismatches.
- **Impact on compose-winui:** WinUI `UriHandler` should use the Windows
  Runtime launcher API, but the generated `Launcher` projection currently
  prevents `compileKotlinWinuiJvm` from succeeding.
- **compose-winui workaround:** The manual `ILauncherStatics` activation/vtable
  path was removed. `WinUIUriHandler.winui.kt` now uses the generated
  `Launcher.launchUriAsync(uri, options, inputData)` overload. The one-argument
  overload is still not usable in generated code, so keep this issue open until
  the natural `Launcher.launchUriAsync(uri)` path is bindable.
- **Resolution target:** Generate valid dependent projections for
  `Windows.System.Launcher`, or avoid generating invalid
  `Windows.ApplicationModel.Package` wrappers for this API surface.

## KWINRT-006: Nullable UIElement.Clip setter is generated as non-null

- **Status:** Fixed upstream; compose workaround removed after updating
  `external/kotlin-winrt` to `efc6cc8714ec59812ffb817cc29e424d1ff60d6f`.
- **Observed in:** `Microsoft.UI.Xaml.UIElement.clip`
- **Symptom:** `UIElement.clip` is generated as a non-null `RectangleGeometry`
  property, so compose-winui cannot call the projected setter with `null` to
  clear an existing clip.
- **Impact on compose-winui:** `WinUIInteropProperties.clipToBounds` needs to
  support true-to-false updates without leaving a stale clipping rectangle on
  the native wrapper.
- **compose-winui workaround:** Removed. `WinUIView.winui.kt` now uses the
  generated nullable `UIElement.clip` property for both setting and clearing.
- **Resolution target:** Generate nullable Kotlin setters for WinRT runtime
  class properties whose metadata permits null, including
  `Microsoft.UI.Xaml.UIElement.Clip`.

## KWINRT-007: Canvas attached property setters can crash the WinUI runtime

- **Status:** Not currently actionable upstream without fresh native crash
  evidence. The detached/offscreen sample path is covered by the current
  compose-winui workaround; if this reproduces again, capture and analyze the
  native crash log or dump before changing kotlin-winrt.
- **Observed in:** `Canvas.setLeft(UIElement, Double)`,
  `Canvas.setTop(UIElement, Double)`, and `DependencyObject.setValue(...)` with
  `Canvas.LeftProperty` / `Canvas.TopProperty`
- **Symptom:** Setting Canvas positioning attached properties during the
  repository-local offscreen `WinUIComposeView` smoke can crash in
  `Microsoft.UI.Xaml.dll` with `EXCEPTION_ACCESS_VIOLATION` before an HRESULT is
  returned to Kotlin.
- **Impact on compose-winui:** A Canvas-backed interop root is the desired
  container for absolute wrapper placement, but compose-winui cannot currently
  use the generated Canvas attached-property setter path safely in all smoke
  hosts.
- **compose-winui workaround:** `WinUIView.winui.kt` uses a Canvas root
  container for tree-order/z-order, but writes wrapper position through
  `FrameworkElement.margin` instead of `Canvas.Left` / `Canvas.Top`. Search for
  `KWINRT-007`.
- **Resolution target:** If a new dump shows a projection/lifetime bug, fix it
  in kotlin-winrt. If the dump shows a WinUI detached/offscreen precondition,
  compose-winui should keep Canvas positioning deferred until the element is
  attached/loaded rather than adding a kotlin-winrt guard.

## KWINRT-008: XamlControlsResources cannot be installed from compose-winui Application

- **Status:** Fixed upstream; compose workaround removed after updating
  `external/kotlin-winrt` to `6bb4cb97` (`Complete WinUI KMP resource bootstrap`).
- **Observed in:** `Microsoft.UI.Xaml.Application.resources` and
  `Microsoft.UI.Xaml.Controls.XamlControlsResources`
- **Symptom:** Installing WinUI control resources from the compose-winui
  `Application { ... }` path fails before a live `TextBox` can be validated.
  Synchronous installation from the `Application.start` callback can crash in
  `Microsoft.UI.Xaml.dll`; delaying to the DispatcherQueue lets
  `Application.resources` and `mergedDictionaries` resolve, but
  `XamlControlsResources()` still fails during construction and the callback
  bridge reports `SetRestrictedErrorInfo failed: 参数错误。`
- **Impact on compose-winui:** A live-window integration smoke can currently
  host simple controls such as `Button` and `ToggleSwitch`, but enabling a live
  `TextBox` pulls in WinUI text resources and can crash on startup or shutdown.
- **compose-winui workaround:** Removed. `Application.winui.kt` no longer loads
  a repository-local `App.xaml`, and `WinUIViewSample.kt` now validates a live
  `TextBox` in the `Application { Window { ... } }` sample path.
- **Resolution:** The kotlin-winrt WinUI KMP resource bootstrap now stages and
  initializes the required resources without a compose-winui `App.xaml`.
- **Upstream follow-up:** This is sufficient for `XamlControlsResources` in the
  compose-winui sample, but it is not yet a full MSBuild PRI pipeline. The
  Gradle plugin should keep aligning with Windows SDK targets for `Page`,
  `ApplicationDefinition`, `PRIResource`, manifest default language,
  `ProjectPriIndexName`, `AppxPriInitialPath`, duplicate filtering, and
  `WinAppSdkExpandPriContent` conditions, using the Windows SDK resolver rather
  than package-specific resource handling or ad-hoc `makepri` path lookup.

## KWINRT-009: Collection-returned XAML base wrappers cannot be rewrapped publicly

- **Status:** Fixed upstream for the compose-winui wrapper paths after
  updating `external/kotlin-winrt` to `093379d5` (`Wrap resource event callback
  arguments`).
- **Observed in:** `Canvas.children` / `Panel.children` returning `UIElement`
  values whose native runtime class is a derived XAML type such as `Canvas` or
  `FrameworkElement`
- **Symptom:** After retrieving a child through `Canvas.children`, Kotlin
  casts such as `as? Canvas` or `as? FrameworkElement` do not reliably produce
  the derived projection wrapper, even when the underlying COM identity is a
  `Canvas`. Generated runtime-class `Metadata.wrap(...)` and interface wrappers
  such as `IFrameworkElement` are internal, so compose-winui sample code has no
  stable public way to re-project the base `UIElement` wrapper as the desired
  derived runtime class or interface.
- **Impact on compose-winui:** Repository-local smoke validation can verify
  wrapper identity, `UIElement` properties such as `clip`, and the user-owned
  native view dimensions, but cannot directly assert wrapper
  `FrameworkElement.width`, `height`, or `margin` after reading the wrapper back
  from `Canvas.children`. More generally, consumer code cannot safely downcast
  XAML collection/event values to richer generated projections.
- **compose-winui workaround:** Removed from repository-local validation.
  `WinUIViewSample.kt` now reads the wrapper back from `Canvas.children`,
  casts it to `Canvas`, and validates wrapper `width`, `height`, `margin`, and
  clip state directly.
- **Resolution:** Collection projections now recover concrete XAML
  runtime-class wrappers through the runtime-class-name RCW factory path for
  this compose-winui use case.

## KWINRT-010: Static runtime class shells do not expose callable static members

- **Status:** Fixed upstream; compose workaround removed after updating
  `external/kotlin-winrt` to `efc6cc8714ec59812ffb817cc29e424d1ff60d6f`.
- **Observed in:** `Windows.ApplicationModel.DataTransfer.Clipboard` and
  `Windows.ApplicationModel.DataTransfer.StandardDataFormats`
- **Symptom:** Declaring the static WinRT runtime classes generates public class
  shells and public `StaticInterfaces`, but not public forwarding members such as
  `Clipboard.getContent()`, `Clipboard.setContent(...)`, `Clipboard.clear()`, or
  `StandardDataFormats.text`. The generated statics interfaces contain the
  correct members and vtable slots, but they are not a stable public API surface
  for compose-winui.
- **Impact on compose-winui:** WinUI `LocalClipboardManager` and
  `LocalClipboard` need to read, write, and clear the Windows clipboard, and
  need the standard text format id.
- **compose-winui workaround:** Removed. `PlatformClipboard.winui.kt` now calls
  generated static forwarders such as `Clipboard.getContent()`,
  `Clipboard.setContent(...)`, `Clipboard.clear()`, and
  `StandardDataFormats.text` directly.
- **Resolution target:** Generate public static forwarding properties/functions
  on static runtime class shells so consumers can call
  `Clipboard.getContent()`, `Clipboard.setContent(...)`, `Clipboard.clear()`,
  and `StandardDataFormats.text` directly.

## KWINRT-011: Repeated projection generation creates incompatible internal wrappers

- **Status:** Partially fixed upstream; still open for cross-module projection
  inheritance after updating `external/kotlin-winrt` to
  `efc6cc8714ec59812ffb817cc29e424d1ff60d6f`.
- **Observed in:** `Windows.ApplicationModel.DataTransfer.DataPackageView`
  and `Microsoft.UI.Xaml.WindowActivationState` generated by both
  `:compose:ui:ui` and `:compose:ui:ui:winui-samples`
- **Symptom:** Calling an `internal` generated wrapper such as
  `DataPackageView.Metadata.wrap(...)` from one Gradle module can fail at
  runtime with `NoSuchMethodError` when another module contributes a generated
  class with the same FQN but a different Kotlin module-name mangling. Trying to
  avoid the internal wrapper through `ComWrappersSupport.createRcwForComObject`
  can return `SingleInterfaceOptimizedObject` instead of a public generated
  `DataPackageView` wrapper. The same classpath shape can make
  `WindowActivationState.Metadata.fromAbi(...)` fail with a module-mangled
  `NoSuchMethodError` from `Window.Activated` handling. Retesting after the
  kotlin-winrt update also showed that a downstream JVM sample cannot project
  `Button`, `TextBox`, or `ToggleSwitch` when their generated base classes come
  from `:compose:ui:ui`: generated subclasses call internal wrapper
  constructors such as `Control(..., __winrtWrapper)` and fail to compile.
- **Impact on compose-winui:** `LocalClipboard` needs to consume
  `Clipboard.getContent()` results inside `compose-ui`, and WinUI `WindowInfo`
  needs to consume activation-state event args, while the sample also runs with
  WinUI projections on the classpath.
- **compose-winui workaround:** The clipboard ABI adapter was removed because
  generated `DataPackageView` and static clipboard forwarders are now usable
  inside `:compose:ui:ui`. `Window.winui.kt` still maps the
  `WindowActivationState` ABI integer to public enum constants directly instead
  of calling the generated internal enum helper. The sample also avoids
  downstream projection of `Button` / `TextBox` / `ToggleSwitch` by declaring
  those types in `:compose:ui:ui` for now. Search for `KWINRT-011`.
- **Resolution target:** Avoid duplicate generated classes with the same FQN
  across dependent Gradle modules, or expose public, non-mangled runtime-class
  wrapping APIs that can reliably rewrap `IInspectable` values as the desired
  generated runtime class.

## KWINRT-012: Clipboard async needs a dispatcher-safe helper

- **Status:** Closed as a kotlin-winrt issue; remaining behavior is
  compose/application policy.
- **Observed in:** `Windows.ApplicationModel.DataTransfer.Clipboard` and
  `DataPackageView.GetTextAsync`
- **Symptom:** `DataPackageView.GetTextAsync().join()` can block the WinUI UI
  thread while waiting for an async completion that needs dispatcher progress.
- **Impact on compose-winui:** The deprecated synchronous `ClipboardManager`
  API cannot safely read arbitrary WinRT clipboard text on the UI thread.
- **compose-winui behavior:** `PlatformClipboard.winui.kt` keeps a small
  in-process plain-text cache for deprecated synchronous `ClipboardManager`
  round-trips, and uses suspend/`await()` for `LocalClipboard` text reads.
- **Resolution:** kotlin-winrt should provide generated clipboard APIs and
  correct async runtime lifecycle. It should not add a clipboard-specific
  synchronous helper; any synchronous cache is a compose-winui policy decision.

## KWINRT-013: JVM FFM upcall can crash while WinRT callbacks race shutdown

- **Status:** Not currently reproduced after updating `external/kotlin-winrt`
  to `56c9267c` (`Fix multi-module WinUI event source descriptors`) and
  switching compose-winui event registration back to generated `WinRtEvent`
  accessors. Keep this as the main runtime shutdown issue to investigate if it
  appears again.
- **Observed in:** repository-local `runWinUIViewSample` on Microsoft OpenJDK
  25.0.3 with Windows App SDK callbacks
- **Symptom:** After several WinUI callback/upcall paths have run, the JVM can
  abort with `Internal Error (upcallLinker.cpp:66)` and
  `Could not attach thread for upcall. JNI error code: -1`. The generated
  `hs_err_pid*.log` reports the current thread as a native thread and the last
  pc as an FFM upcall stub.
- **Impact on compose-winui:** Full repository-local sample process validation
  is no longer blocked by this shutdown-time JVM fatal after the upstream fix
  and compose-winui workaround removal.
- **compose-winui workaround:** None active for this issue.
- **Resolution target:** On the next reproduction, do not add WinUI-specific
  shutdown special cases first. Capture `hs_err_pid*.log`, WER output, or a
  dump, identify which callback enters an FFM upcall stub after JVM shutdown
  attach fails, and record whether it is an EventSource event,
  `DispatcherQueue`, timer, frame clock, text input, or another delegate. If it
  is not an event-source callback, the matching delegate handle lifetime should
  be registered in kotlin-winrt's unified shutdown registry, aligned with
  CsWinRT delegate/event ownership.

## KWINRT-014: Generated attached dependency property getters can rewrap with module-mangled internals

- **Status:** Open
- **Observed in:** `Microsoft.UI.Xaml.Automation.AutomationProperties.accessibilityViewProperty`
  returning `Microsoft.UI.Xaml.DependencyProperty`
- **Symptom:** Accessing the generated static attached dependency property can
  throw `NoSuchMethodError` for an internal wrapper such as
  `DependencyProperty.Metadata.wrap$ui(...)` when both `:compose:ui:ui` and a
  repository-local sample generate WinUI projection classes with the same FQNs
  but different Kotlin module-name mangling.
- **Impact on compose-winui:** `WinUIInteropProperties.isNativeAccessibilityEnabled`
  needs to toggle `AutomationProperties.AccessibilityView`, but cannot safely
  restore the default value by reading `AutomationProperties.accessibilityViewProperty`
  and calling `DependencyObject.clearValue(...)` from compose-ui while sample
  projections are also on the runtime classpath.
- **compose-winui workaround:** Keep
  `WinUIInteropProperties.isNativeAccessibilityEnabled` behavior deferred for
  now; do not use the generated `accessibilityViewProperty` getter from
  compose-ui while repository-local samples also generate WinUI projections.
- **Resolution target:** Expose public, non-mangled wrappers for generated
  runtime-class values returned from static attached property getters, or avoid
  duplicate generated projection FQNs across dependent Gradle modules.

## KWINRT-015: AutomationProperties.SetAccessibilityView can native-crash detached XAML elements

- **Status:** Not currently actionable upstream without fresh native crash
  evidence. The local detached sample path has been covered; if compose-winui
  sees another native crash, analyze the native crash event before assigning it
  to kotlin-winrt.
- **Observed in:** `Microsoft.UI.Xaml.Automation.AutomationProperties.setAccessibilityView(...)`
  called on `Canvas` / `Button` instances created in repository-local
  `WinUIComposeView` offscreen smoke tests before the root is attached to a
  native `Window`.
- **Symptom:** Calling the generated public static setter in the detached
  `WinUIView` holder path can abort the JVM with a native
  `EXCEPTION_ACCESS_VIOLATION (0xc0000005)` in `Microsoft.UI.Xaml.dll` shortly
  after `compose-winui-sample: application created`, before the lifecycle smoke
  prints its first validation line.
- **Impact on compose-winui:** `WinUIInteropProperties.isNativeAccessibilityEnabled`
  cannot be safely wired to `AutomationProperties.AccessibilityView` from the
  holder constructor/property update path that also runs for offscreen
  repository-local smoke validation.
- **compose-winui workaround:** Keep native accessibility participation
  behavior deferred until compose-winui has an attachment-aware path for
  applying WinUI automation properties, or kotlin-winrt/WinUI provides a safe
  way to set attached automation properties on detached elements.
- **Resolution target:** Determine from a crash log or dump whether this is a
  Windows App SDK detached/offscreen precondition or a kotlin-winrt
  projection/lifetime issue. If it is a WinUI precondition, compose-winui should
  defer `AutomationProperties.AccessibilityView` updates to an attached/loaded
  point instead of asking kotlin-winrt to guard it.

## KWINRT-016: Generated Application.Start intrinsic is not lowered for compose-winui

- **Status:** Fixed upstream in local kotlin-winrt `ec8c5a52`, still verified
  after syncing `external/kotlin-winrt` from upstream `093379d5`.
- **Observed in:** earlier repository-local `runWinUIViewSample` attempts after
  clean `:compose:ui:ui:compileKotlinWinuiJvm` rebuilds with JDK 25.
- **Symptom:** Generated WinRT projection bytecode for
  `Application.Metadata.start(...)` could reach the runtime fallback:
  `WinRtProjectionIntrinsic.callUnit(...) was not lowered`.
- **Verification:** With `JAVA_HOME`/`ANDROIDX_JDK21` pointing at
  `C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot`, the repository-local
  `:compose:ui:ui:compileKotlinWinuiJvm` and
  `:compose:ui:ui:winui-samples:compileKotlin` tasks pass. `javap` on
  `Application$Metadata.class` shows `WinRtJvmFfmDowncallHandles` instead of
  `WinRtProjectionIntrinsic.callUnit`, and `runWinUIViewSample` enters
  `Application.Start`. The `093379d5` retest also passed
  `:compose:ui:ui:compileKotlinWinuiJvm`, confirming the compiler-plugin
  classpath/lowering path remains active for the compose-winui KMP target.
- **compose-winui workaround:** None active. `Application.winui.kt` continues
  to call the generated `XamlApplication.start { ... }` path.
- **Resolution target:** Keep the JDK 22+ / JVM target 22 WinUI JVM compiler
  setup in compose-winui so the upstream lowering remains active.

## KWINRT-017: WinUI Application access native-failfasts inside Application.Start callback

- **Status:** Superseded by the fixed generated `Application.Start` and event
  callback path. The latest `56c9267c` retest runs repository-local
  `runWinUIViewSample` to completion.
- **Observed in:** earlier local experiments that bypassed KWINRT-016 with a
  direct `IApplicationStatics.Start` ABI call.
- **Symptom:** After bypassing KWINRT-016, WinUI entered the initialization
  callback, then the process terminated with `NTSTATUS 0xC000027B` before the
  compose sample could create its window. Retesting the natural generated path
  now creates the application and runs through the sample smokes successfully.
- **Impact on compose-winui:** The earlier application-access fail-fast is no
  longer the active blocker on the generated `Application.Start` path.
- **compose-winui workaround:** None.
- **Resolution target:** After KWINRT-016 is fixed, retest the generated/runtime
  path for creating or retrieving the WinUI `Application` instance inside
  `Application.Start`, including any required `IApplicationOverrides` and
  `IXamlMetadataProvider` support for compose-winui's KMP JVM target.

## KWINRT-018: ContentControl.Content string getter did not round-trip assigned strings

- **Status:** Fixed upstream. Verified after updating `external/kotlin-winrt`
  to `05273dea` (`Validate nullable content and launcher projection`) and
  changing repository-local `WinUIViewSample` smokes to read `Button.content`
  back directly. A later `56c9267c` retest also completed full sample process
  validation successfully.
- **Observed in:** `Microsoft.UI.Xaml.Controls.Button.content`, inherited from
  `ContentControl.Content`, in repository-local `WinUIViewSample` smokes after
  syncing `external/kotlin-winrt` from upstream `ec8c5a52`.
- **Symptom:** Assigning Kotlin strings to `Button.content` succeeds well
  enough for the WinUIView update path to continue, but reading
  `button.content` back in the same smoke returns `null`. The z-order smoke
  reproduced this with both sibling buttons after their update lambdas ran:
  `first=null second=null`.
- **Impact on compose-winui:** Before the upstream fix, repository-local
  smokes could not use
  `Button.content` getter as proof that `WinUIView` update lambdas ran or that
  sibling ordering was preserved. This also makes `ContentControl.Content`
  unsuitable for state assertions until string/object projection round-tripping
  is fixed.
- **compose-winui workaround:** Removed. `WinUIViewSample` now records the value
  read from `Button.content` after assignment. Search for `KWINRT-018`.
- **Resolution:** Upstream object value readback now preserves strings assigned
  through `ContentControl.Content` setter when read back from the getter.

## KWINRT-019: Live WinUI controls reject programmatic focus transfer from Compose

- **Status:** Closed as a kotlin-winrt blocker unless new ABI evidence appears.
  Later validation showed generated `UIElement.focus(FocusState.Programmatic)`
  can return `true` after layout/loaded readiness.
- **Observed in:** A live `Button` hosted by `WinUIView` inside the
  repository-local `Application { Window { ... } }` sample path.
- **Symptom:** After the primary WinUI window is activated and the embedded
  native `Button` has been created, a Compose `FocusRequester` attached to
  `WinUIView` attempts to enter the interop focus group. `WinUIView` forwards
  that request to `Button.focus(FocusState.Programmatic)`, but the native call
  returns `false`, so Compose correctly cancels the focus transfer.
- **Impact on compose-winui:** Focus traversal can move between Compose
  focus targets, and WinUIView can expose focus properties, but the sample
  cannot yet validate moving Compose focus into a real native WinUI control.
- **compose-winui action:** Move native focus requests to a loaded/layout-ready
  point and keep the live WinUIView focus-transfer smoke disabled until that
  compose-side timing path is implemented. Existing smokes still validate owner
  focus requests and `RootForTest` Tab / Shift+Tab traversal between Compose
  focus targets.
- **Resolution:** Do not change kotlin-winrt for this without new evidence that
  the generated ABI call is wrong.

## KWINRT-020: DisplayRequest default interface projection is not registered

- **Status:** Open pending exact generated-artifact inspection. Still
  reproduced after syncing `external/kotlin-winrt` from upstream `56c9267c`
  (`Fix multi-module WinUI event source descriptors`) and temporarily replacing
  the compose-winui workaround with generated `DisplayRequest.requestActive()` /
  `requestRelease()` calls.
- **Observed in:** `Windows.System.Display.DisplayRequest`
- **Symptom:** `DisplayRequest()` activates, but calling generated
  `requestActive()` or `requestRelease()` fails with:
  `Generated interface projection factory for 'windows.system.display.IDisplayRequest' is not registered.`
- **Impact on compose-winui:** `Modifier.keepScreenOn()` should use the
  Windows Runtime `DisplayRequest` API, but the generated default-interface
  wrapper is not callable.
- **compose-winui workaround:** `WinUIDisplayRequestController.winui.kt` still
  activates the generated `DisplayRequest`, then invokes
  `IDisplayRequest.RequestActive` and `RequestRelease` through the default
  interface ABI slots. Search for `KWINRT-020`.
- **Latest retest note:** On `56c9267c`, the generated call path compiles, but
  `:compose:ui:ui:winui-samples:runWinUIViewSample` fails as soon as
  `Modifier.keepScreenOn()` calls `DisplayRequest.requestActive()` with:
  `Generated interface projection factory for 'windows.system.display.IDisplayRequest' is not registered.`
  The sample process exits with `NTSTATUS 0xC000027B`; no `hs_err_pid*.log` or
  `replay_pid*.log` was produced in the repository tree for this run. The
  managed stack reaches `DisplayRequest.requestActive(DisplayRequest.kt:63)` via
  `WinUIDisplayRequestController.setKeepScreenOn`, `WinUIOwner.incrementKeepScreenOnCount`,
  and `KeepScreenOnNode.onAttach`.
- **Resolution target:** Ensure compose-winui's generated support output
  includes and loads the `IDisplayRequest` interface projection registry, or
  emit runtime-class methods that can invoke the default-interface ABI without
  requiring a registered generated interface wrapper. Before changing runtime
  code, inspect the compose generated artifacts for duplicate FQNs,
  `type-index.tsv`, `WinRTInterfaceProjectionRegistry.class`, and whether the
  interface descriptor comes from the expected module in the exact compose-winui
  graph.

## KWINRT-021: Protected WinUI cursor API is not usable from compose-winui

- **Status:** Open
- **Observed in:** `Microsoft.UI.Xaml.UIElement.ProtectedCursor`
- **Symptom:** WinUI exposes cursor selection through the protected
  `UIElement.ProtectedCursor` property. The generated `IUIElementProtected`
  projection is internal and compose-winui cannot author a projected XAML
  subclass that sets the property through the natural protected API surface.
- **Impact on compose-winui:** `Modifier.pointerHoverIcon(...)` needs to update
  the cursor on the WinUI root element when Compose hover state changes.
- **compose-winui workaround:** `WinUIPointerCursorAdapter.winui.kt` creates a
  `CoreCursor` / `InputCursor`, then invokes the `IUIElementProtected`
  `ProtectedCursor` setter slot directly. Search for `KWINRT-021`.
- **Resolution target:** Provide a supported public projection path for
  projected XAML subclasses or otherwise expose a safe cursor-setting helper
  that does not require consumer code to call protected-interface ABI slots.

## KWINRT-022: WinRT flags project as enum values instead of bitmasks

- **Status:** Fixed upstream. Verified after updating `external/kotlin-winrt`
  to `56c9267c`; generated `VirtualKeyModifiers` is now a value class with
  bitmask operations and `fromAbi(UInt)` preserves combined flag values.
- **Observed in:** `Windows.System.VirtualKeyModifiers`, read from
  `Microsoft.UI.Xaml.Input.PointerRoutedEventArgs.KeyModifiers`
- **Symptom:** WinRT metadata marks `VirtualKeyModifiers` as a flags enum, but
  kotlin-winrt projects it as a regular Kotlin `enum class`. Single values such
  as `Control` can be read, but combined native values such as
  `Control | Shift` are not entries in the generated enum and throw from
  `VirtualKeyModifiers.Metadata.fromAbi(...)`.
- **Impact on compose-winui:** Pointer events delivered to Compose can lose
  keyboard modifier state whenever more than one WinUI modifier key is pressed.
- **compose-winui workaround:** Removed. `WinUIPointerInputAdapter.winui.kt`
  now reads `PointerRoutedEventArgs.keyModifiers` through the generated
  projection and maps the projected `VirtualKeyModifiers` value.
- **Resolution target:** Keep the combined and unknown-bit modifier cases in
  `WinUIPointerKeyboardModifiersTest`.
