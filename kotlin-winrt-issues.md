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
