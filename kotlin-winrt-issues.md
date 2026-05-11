# kotlin-winrt issues found by compose-winui

This file tracks kotlin-winrt gaps that currently affect compose-winui. Each
issue has a stable id so compose-winui workarounds can reference it directly.

## KWINRT-001: Generated event source registry ABI mismatch

- **Status:** Open
- **Observed in:** `Window.closed`, `AppWindow.changed`, and generated WinRT
  event accessors
- **Symptom:** Accessing generated event properties can fail during
  `WinRTEventProjectionHelpers.installEventSources(...)` with:
  `IncompatibleClassChangeError: Expecting non-static method ... createEventSourceFactory(...)`.
- **Impact on compose-winui:** Declarative window lifecycle and `WindowInfo`
  updates need WinUI close/changed events, but using generated event accessors
  currently crashes the sample.
- **compose-winui workaround:** `Window.winui.kt` manually registers
  `Window.Closed` through the `IWindow` vtable and `AppWindow.Changed` through
  the `IAppWindow` vtable, keeping each delegate handle alive until removal.
  Search for `KWINRT-001`.
- **Resolution target:** Fix generated event source registry/runtime ABI
  compatibility so generated `WinRtEvent` properties can be used directly.

## KWINRT-002: Interface projection registry is not reliably initialized

- **Status:** Open
- **Observed in:** `DispatcherQueue.hasThreadAccess`
- **Symptom:** Even after declaring `type("Microsoft.UI.Dispatching.IDispatcherQueue2")`,
  using `dispatcherQueue.hasThreadAccess` can fail at runtime with:
  `Generated interface projection factory for 'microsoft.ui.dispatching.IDispatcherQueue2' is not registered.`
- **Impact on compose-winui:** WinUI coroutine dispatch should avoid redundant
  `DispatcherQueue.tryEnqueue(...)` calls when already on the UI thread.
- **compose-winui workaround:** `WinUIComposeView.winui.kt` explicitly triggers
  the generated `WinRTInterfaceProjectionRegistry.register()` method, then
  treats `DispatcherQueue.hasThreadAccess` as best-effort. If the generated
  interface projection is still unavailable, the WinUI coroutine dispatcher
  returns `true` from `isDispatchNeeded` and uses `DispatcherQueue.tryEnqueue`.
  Search for `KWINRT-002`.
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
- **Impact on compose-winui:** `WindowBackdrop.None` can represent the initial
  no-backdrop state, but changing from `Mica`, `DesktopAcrylic`, or `Custom`
  back to `None` is currently a no-op.
- **compose-winui workaround:** `Window.winui.kt` skips the native setter when
  `WindowBackdrop.None` produces `null`. Search for `KWINRT-004`.
- **Resolution target:** Generate nullable Kotlin setters for WinRT
  runtime-class properties whose metadata permits null, including
  `Microsoft.UI.Xaml.Window.SystemBackdrop`.

## KWINRT-005: Windows.System.Launcher projection pulls invalid Package wrappers

- **Status:** Open
- **Observed in:** `Windows.System.Launcher`
- **Symptom:** Declaring `type("Windows.System.Launcher")` can generate
  invalid dependent projections under `Windows.ApplicationModel`, including
  `AppInfo.package` and `PackageContentGroup.package` return type mismatches.
- **Impact on compose-winui:** WinUI `UriHandler` should use the Windows
  Runtime launcher API, but the generated `Launcher` projection currently
  prevents `compileKotlinWinuiJvm` from succeeding.
- **compose-winui workaround:** `WinUIUriHandler.winui.kt` does not declare
  or use the generated `Windows.System.Launcher` projection. It activates
  `Windows.System.Launcher` for `ILauncherStatics` and invokes
  `LaunchUriAsync` through the vtable directly. Search for `KWINRT-005`.
- **Resolution target:** Generate valid dependent projections for
  `Windows.System.Launcher`, or avoid generating invalid
  `Windows.ApplicationModel.Package` wrappers for this API surface.

## KWINRT-006: Nullable UIElement.Clip setter is generated as non-null

- **Status:** Open
- **Observed in:** `Microsoft.UI.Xaml.UIElement.clip`
- **Symptom:** `UIElement.clip` is generated as a non-null `RectangleGeometry`
  property, so compose-winui cannot call the projected setter with `null` to
  clear an existing clip.
- **Impact on compose-winui:** `WinUIInteropProperties.clipToBounds` needs to
  support true-to-false updates without leaving a stale clipping rectangle on
  the native wrapper.
- **compose-winui workaround:** `WinUIView.winui.kt` uses the generated setter
  when installing a `RectangleGeometry`, then invokes the `IUIElement.Clip`
  setter directly with a null ABI pointer when clearing clip. Search for
  `KWINRT-006`.
- **Resolution target:** Generate nullable Kotlin setters for WinRT runtime
  class properties whose metadata permits null, including
  `Microsoft.UI.Xaml.UIElement.Clip`.

## KWINRT-007: Canvas attached property setters can crash the WinUI runtime

- **Status:** Open
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
- **Resolution target:** Make generated static attached-property setters and
  boxed `DependencyObject.setValue(...)` calls safe for Canvas positioning
  properties, or document a required XAML attachment precondition that
  compose-winui can detect before calling them.

## KWINRT-008: XamlControlsResources cannot be installed from compose-winui Application

- **Status:** Open
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
- **compose-winui workaround:** `WinUIViewSample.kt` keeps `TextBox` covered by
  the offscreen control-variety smoke, but the live `Application { Window { ... } }`
  sample only embeds `Button` and `ToggleSwitch` until this resource setup path
  is fixed. Search for `KWINRT-008`.
- **Resolution target:** Make the unpackaged JVM resource setup path support
  `XamlControlsResources` without requiring a C/C++ authoring host toolchain in
  compose-ui, or expose a stable runtime helper that can install WinUI control
  resources for base `Application` instances.

## KWINRT-009: Collection-returned XAML base wrappers cannot be rewrapped publicly

- **Status:** Open
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
- **compose-winui workaround:** `WinUIView.winui.kt` keeps a strongly typed
  `Canvas` wrapper inside `WinUIViewHolder` when applying size, margin, and
  clipping. `WinUIViewSample.kt` avoids derived-wrapper reads from
  `Canvas.children` and validates relayout through `UIElement.clip`, stable COM
  identity, and the user `Button` dimensions instead. Search for `KWINRT-009`.
- **Resolution target:** Expose a stable public projection API that can wrap an
  `IInspectable` / `IUnknown` as a requested generated runtime class or
  interface, or make collection projections preserve/recover concrete
  runtime-class wrappers so normal Kotlin type checks work for projected XAML
  inheritance.

## KWINRT-010: Static runtime class shells do not expose callable static members

- **Status:** Open
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
- **compose-winui workaround:** `PlatformClipboard.winui.kt` gets the generated
  static interface references through `StaticInterfaces` and invokes the needed
  vtable slots with runtime interop helpers. Search for `KWINRT-010`.
- **Resolution target:** Generate public static forwarding properties/functions
  on static runtime class shells so consumers can call
  `Clipboard.getContent()`, `Clipboard.setContent(...)`, `Clipboard.clear()`,
  and `StandardDataFormats.text` directly.

## KWINRT-011: Repeated projection generation creates incompatible internal wrappers

- **Status:** Open
- **Observed in:** `Windows.ApplicationModel.DataTransfer.DataPackageView`
  generated by both `:compose:ui:ui` and `:compose:ui:ui:winui-samples`
- **Symptom:** Calling an `internal` generated wrapper such as
  `DataPackageView.Metadata.wrap(...)` from one Gradle module can fail at
  runtime with `NoSuchMethodError` when another module contributes a generated
  class with the same FQN but a different Kotlin module-name mangling. Trying to
  avoid the internal wrapper through `ComWrappersSupport.createRcwForComObject`
  can return `SingleInterfaceOptimizedObject` instead of a public generated
  `DataPackageView` wrapper.
- **Impact on compose-winui:** `LocalClipboard` needs to consume
  `Clipboard.getContent()` results inside `compose-ui`, while the sample also
  runs with WinUI projections on the classpath.
- **compose-winui workaround:** `PlatformClipboard.winui.kt` wraps clipboard
  `DataPackageView` results in a small compose-ui-local ABI adapter and invokes
  `IDataPackageView.Contains`, `AvailableFormats`, and `GetTextAsync` directly.
  Search for `KWINRT-011`.
- **Resolution target:** Avoid duplicate generated classes with the same FQN
  across dependent Gradle modules, or expose public, non-mangled runtime-class
  wrapping APIs that can reliably rewrap `IInspectable` values as the desired
  generated runtime class.

## KWINRT-012: Clipboard async and locking need a dispatcher-safe helper

- **Status:** Open
- **Observed in:** `Windows.ApplicationModel.DataTransfer.Clipboard` and
  `DataPackageView.GetTextAsync`
- **Symptom:** `DataPackageView.GetTextAsync().join()` can block the WinUI UI
  thread while waiting for an async completion that needs dispatcher progress.
  Repeated `Clipboard.SetContent(...)` calls during offscreen composition smokes
  can also fail with `CLIPBRD_E_CANT_OPEN` / `OpenClipboard failed`
  (`0x800401D0`) when the system clipboard is transiently locked.
- **Impact on compose-winui:** The deprecated synchronous `ClipboardManager`
  API cannot safely read arbitrary WinRT clipboard text on the UI thread, and a
  native clipboard write failure should not crash Compose-only local
  composition validation.
- **compose-winui workaround:** `PlatformClipboard.winui.kt` keeps a small
  in-process plain-text cache for synchronous `ClipboardManager` round-trips,
  uses suspend/`await()` for `LocalClipboard` text reads, and treats native
  `SetContent` as best-effort when Compose already has the plain-text payload.
  Search for `KWINRT-012`.
- **Resolution target:** Provide a dispatcher-safe WinRT async bridge and a
  retrying clipboard helper for transient `OpenClipboard` failures so
  compose-winui can remove the best-effort write/cache workaround.

## KWINRT-013: JVM FFM upcall can crash while WinRT callbacks race shutdown

- **Status:** Open
- **Observed in:** repository-local `runWinUIViewSample` on Microsoft OpenJDK
  25.0.3 with Windows App SDK callbacks
- **Symptom:** After several WinUI callback/upcall paths have run, the JVM can
  abort with `Internal Error (upcallLinker.cpp:66)` and
  `Could not attach thread for upcall. JNI error code: -1`. The generated
  `hs_err_pid*.log` reports the current thread as a native thread and the last
  pc as an FFM upcall stub.
- **Impact on compose-winui:** The sample now compiles and runs through the
  clipboard and early WinUIView smoke checks, but full process validation can be
  interrupted by a VM fatal outside Kotlin exception handling.
- **compose-winui workaround:** `WinUIFrameClock` ignores frame callbacks after
  disposal, but this does not eliminate the fatal. Keep sample failures with
  this signature classified as a kotlin-winrt/JDK upcall lifecycle blocker, not
  as a compose-ui compile failure. Search for `KWINRT-013`.
- **Resolution target:** Audit kotlin-winrt delegate/upcall lifetime and WinRT
  callback removal, then either retain/close callback stubs in a shutdown-safe
  order or avoid callbacks from native threads that the JVM cannot attach during
  shutdown.

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

- **Status:** Open
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
- **Resolution target:** Determine whether the crash is a Windows App SDK
  detached-element restriction or a kotlin-winrt projection/lifetime issue, then
  provide a safe application point or runtime guard for attached automation
  properties.
