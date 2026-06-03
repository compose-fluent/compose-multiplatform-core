# kotlin-winrt issues found by compose-winui

This file tracks kotlin-winrt gaps that affect compose-winui. Use stable
`KWINRT-###` ids from compose-winui workaround comments so the workaround can be
removed when the upstream behavior is fixed.

Keep closed issues short. They should state the final outcome and validation
baseline, not every retest attempt.

## Current upstream triage

- **Open upstream/runtime:** none treated as blocking for current compose-winui
  work.
- **Open upstream/plugin:** `KWINRT-025`.
- **Open compose-side workarounds:** `KWINRT-025`.
- **Compose/application policy, not kotlin-winrt helpers:** `KWINRT-012`
  clipboard synchronization and `KWINRT-019` focus timing.
- **Closed/fixed or superseded:** `KWINRT-001`, `KWINRT-002`, `KWINRT-003`,
  `KWINRT-005`, `KWINRT-006`, `KWINRT-007`, `KWINRT-009`,
  `KWINRT-010`, `KWINRT-011`, `KWINRT-013`, `KWINRT-014`, `KWINRT-015`,
  `KWINRT-016`, `KWINRT-017`, `KWINRT-018`, `KWINRT-020`, `KWINRT-021`,
  `KWINRT-022`, `KWINRT-023`, `KWINRT-004`, and `KWINRT-008`.

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

- **Status:** Fixed upstream in kotlin-winrt Maven snapshot
  `0.1.0-SNAPSHOT` as of 2026-06-02.
- **Observed in:** `Window.systemBackdrop`.
- **Resolution:** compose-winui removed the manual
  `IWindow2.SYSTEMBACKDROP_SETTER_SLOT` ABI path and now uses the generated
  nullable `Window.systemBackdrop` setter for both setting and clearing.
- **Validation:** `compileKotlinWinuiJvm` passes with the Maven snapshot.

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

- **Status:** Fixed for the compose-winui integration surface in kotlin-winrt
  Maven snapshot `0.1.0-SNAPSHOT` as of 2026-06-02.
- **Observed in:** the real compose-winui `Application { Window { ... } }` path
  after authored `Application` CCW registration and resource staging.
- **Earlier finding:** This was deeper than the earlier
  `XamlControlsResources`/PRI symptom. Previous kotlin-winrt builds could stage
  enough WinUI resources for the sample and no longer needed a repository-local
  `App.xaml`, but the JVM artifact runtime rejected many generated public
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
- **Resolution:** compose-winui removed the local interface alias registration
  and the ABI slot fallbacks for `Window.Content`, `Window.Compositor`,
  `Window.AppWindow`, `Window.SystemBackdrop`, and `Panel.Children`. These paths
  now use generated projection members.
- **Validation:** `compileKotlinWinuiJvm` passes with the Maven snapshot.

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

- **Status:** Deferred / non-blocking for current compose-winui work.
- **Observed in:** `:compose:ui:ui:winui-samples:runWinUIViewSample` with
  `external/kotlin-winrt` `1bd45755`, still reproduced after syncing
  `6b1ce387` (`Remove stale interface support merging`). The sample passes
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
- **Latest native evidence:** after `6b1ce387`, the latest dump is
  `%LOCALAPPDATA%\CrashDumps\java.exe.43136.dmp`. WER reports
  `APPCRASH java.exe`, faulting module `Microsoft.UI.Xaml.dll` 3.1.8.0,
  exception `0xc0000005`, offset `0x2a62a0`, report id
  `fae61182-415c-435c-9310-65d39e767f6b`. Local minidump parsing maps the
  exception address to the staged XAML DLL at `+0x2a62a0`; the failing access is
  a read from `0xffffffffffffffff`. The candidate native stack is now a
  XAML/CoreMessaging/UI-thread path rather than the earlier unloaded-DLL
  teardown stack. This matches the post-`402eb4d8` and post-`32f6af88` dumps
  except for process/report ids.
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
- **Maven snapshot validation:** with kotlin-winrt `0.1.0-SNAPSHOT` from Maven
  snapshots on 2026-06-02, `runWinUIViewSample` again reaches
  `compose-winui-sample: text input session cancellation` and exits with
  `NTSTATUS 0xC0000005`. No newer `java.exe*.dmp` was present in
  `%LOCALAPPDATA%\CrashDumps` after this run.
- **2026-06-03 skiko-winui snapshot validation:** after consuming
  `skiko-winui` `0.0.0-20260603.023842-2`,
  `winrt-gradle-plugin` `0.1.0-20260603.021843-3`, and
  `winrt-compiler-plugin` `0.1.0-20260603.021535-22`,
  `runWinUIViewSample` reaches the same final smoke log
  `compose-winui-sample: text input session cancellation` and exits with
  `NTSTATUS 0xC0000005`.
- **2026-06-03 WinDbg evidence:** Store WinDbg
  `10.0.29547.1002` analyzed
  `%LOCALAPPDATA%\CrashDumps\java.exe(1).39844.dmp`; the log is
  `out/compose-multiplatform-core/windbg-java-39844.log`. The failure bucket is
  `SOFTWARE_NX_FAULT_INVALID_POINTER_EXECUTE_c0000005_Microsoft.UI.Xaml.dll!Unloaded`,
  with `ExceptionAddress` `<Unloaded_Microsoft.UI.Xaml.dll>+0x287490` and
  `AV.Type=Execute`. The stack is in thread teardown through
  `ntdll!RtlpFlsDataCleanup`, `ntdll!LdrShutdownThread`,
  `ntdll!RtlExitUserThread`, `KERNELBASE!FreeLibraryAndExitThread`, and
  `ucrtbase!common_end_thread`. This keeps the latest evidence in the
  unloaded-XAML teardown bucket and still does not show a Java/Kotlin managed
  exception or FFM upcall frame.
- **2026-06-03 initial dump evidence:** Store WinDbg also analyzed the earlier
  same-PID dump `%LOCALAPPDATA%\CrashDumps\java.exe.39844.dmp`; the log is
  `out/compose-multiplatform-core/windbg-java-39844-initial.log`. This first
  dump has the more useful pre-unload bucket
  `INVALID_POINTER_READ_c0000005_Microsoft.UI.Xaml.dll!ctl::ComPtr_ABI::Microsoft::UI::Xaml::IFrameworkElement_::InternalRelease`.
  The exception address is
  `Microsoft_UI_Xaml!ctl::ComPtr<ABI::Microsoft::UI::Xaml::IFrameworkElement>::InternalRelease+0x1e`
  (`Microsoft.UI.Xaml.dll` `+0x5c5ce`), attempting to read from
  `0x00007ff9e06e57f8`. The native stack runs through
  `Microsoft_UI_Xaml!CCustomDependencyProperty::~CCustomDependencyProperty`,
  `Microsoft_UI_Xaml!DirectUI::DynamicMetadataStorage::~DynamicMetadataStorage`,
  `Microsoft_UI_Xaml!DirectUI::DynamicMetadataStorage::Destroy`,
  `Microsoft_UI_Xaml!DeinitializeDll`, then `ntdll!LdrpProcessDetachNode`,
  `KERNELBASE!FreeLibrary`, `combase!CClassCache::CleanUpDllsForProcess`,
  `combase!CoUninitialize`, and `combase!FlsThreadCleanupCallback`. This points
  specifically at XAML dynamic metadata / custom dependency property lifetime
  during COM thread cleanup.
- **2026-06-03 compose-side isolation:** a diagnostic build replaced the
  authored `WinUIRootContentControl : ContentControl` with a plain
  `ContentControl` typealias and disabled root cursor assignment. The WinUI JVM
  compile still passed and the sample still reached
  `compose-winui-sample: text input session cancellation` before exiting with
  `NTSTATUS 0xC0000005`. This rules out the compose-winui root content control
  authored subclass as the sole trigger; the remaining authored XAML type in
  that diagnostic path is `WinUIXamlApplication`.
- **2026-06-03 upstream control validation:** local `external/kotlin-winrt` at
  `6b1ce387` runs
  `:winrt-samples:winui-kmp-app:runWinuiKmpSample` to
  `winui-kmp-app: finished` and exits successfully. That sample covers authored
  controls, a custom dependency property, callbacks, unload, and WinUI teardown,
  so this crash is not reproduced by kotlin-winrt's own control sample.
- **2026-06-03 Maven snapshot retest:** Maven snapshot metadata and Gradle
  dependency insight still resolve compose-winui to `skiko-winui`
  `0.0.0-20260603.023842-2`, `skiko-winui-windows`
  `0.0.0-20260603.023842-2`, `winrt-runtime` /
  `winrt-runtime-jvm` / `winrt-authoring` `0.1.0-20260603.021535-22`,
  `winrt-gradle-plugin` `0.1.0-20260603.021843-3`, and
  `winrt-compiler-plugin` `0.1.0-20260603.021535-22`.
  `:compose:ui:ui:compileKotlinWinuiJvm` passes, and
  `:compose:ui:ui:winui-samples:runWinUIViewSample` reaches the full current
  smoke path before the same `NTSTATUS 0xC0000005` process exit.
- **2026-06-03 compose-side diagnostic:** deferring
  `WinUIApplicationRuntime.dispose()` until after `Application.start` returned
  did not change the result. The sample still reached the final smoke log and
  exited with `NTSTATUS 0xC0000005`, so the crash is not explained solely by
  compose-winui disposing its root before calling `Application.exit()`.
- **2026-06-03 latest Maven snapshot retest:** after clearing Gradle
  `files-2.1`, descriptor, and `resources-2.1` snapshot metadata for
  `io.github.compose-fluent`, Gradle resolved kotlin-winrt runtime/authoring
  metadata to `0.1.0-20260603.042831-23` and `winrt-gradle-plugin` to
  `0.1.0-20260603.043142-4`; `skiko-winui` remained
  `0.0.0-20260603.023842-2`. `:compose:ui:ui:compileKotlinWinuiJvm` passes,
  and `:compose:ui:ui:winui-samples:runWinUIViewSample` again reaches
  `compose-winui-sample: text input session cancellation` before failing with
  `NTSTATUS 0xC0000005`. No newer WER dump was produced in
  `%LOCALAPPDATA%\CrashDumps` after that run; Store WinDbg/CDB logs are kept at
  `out/compose-multiplatform-core/cdb-runWinUIViewSample-latest*.log`.
- **2026-06-03 attached Skiko diagnostics retest:** after adding an
  attached-window Skiko render diagnostics smoke, the sample reaches
  `compose-winui-sample: skiko render diagnostics` and still reaches the final
  `compose-winui-sample: text input session cancellation` log before the same
  `NTSTATUS 0xC0000005` process exit. No newer WER dump was produced in
  `%LOCALAPPDATA%\CrashDumps`.
- **2026-06-03 attach-aware Skiko scheduler retest:** after deferring
  `WinUIComposeView` Skiko frame-scheduler startup until root `Loaded`, the
  sample still reaches `compose-winui-sample: skiko render diagnostics` and
  `compose-winui-sample: text input session cancellation` before failing with
  `NTSTATUS 0xC0000005`. This run produced
  `%LOCALAPPDATA%\CrashDumps\java.exe.55780.dmp`; Store CDB/WinDbg
  `10.0.29547.1002` analysis is saved at
  `out/compose-multiplatform-core/windbg-java-55780.log`. The bucket is
  `STOWED_EXCEPTION_c000027b_CoreMessagingXP.dll!Microsoft::UI::Dispatching::DispatcherQueue::DeferInvokeCallback`;
  the stack goes through `KERNELBASE!RaiseFailFastException`,
  `combase!RoFailFastWithErrorContextInternal2`,
  `CoreMessagingXP!Microsoft::UI::Dispatching::DispatcherQueue::DeferInvokeCallback`,
  CoreMessaging dispatch/deferred-call frames, and
  `Microsoft_UI_Xaml!DirectUI::FrameworkApplication::StartDesktop`. This again
  points at WinUI/CoreMessaging application lifetime or dispatcher teardown
  after the full compose-winui smoke path, not a Skiko render failure.
- **2026-06-03 unattached scheduler smoke retest:** after adding explicit
  sample coverage for the deferred unattached Skiko scheduler path, the sample
  reaches `compose-winui-sample: skiko unattached scheduler deferred`,
  `compose-winui-sample: skiko render diagnostics`, and the final
  `compose-winui-sample: text input session cancellation` log before the same
  `NTSTATUS 0xC0000005` process exit. No newer WER dump was produced after this
  run; the latest dump remains `java.exe.55780.dmp`.
- **2026-06-03 render-size diagnostics retest:** tightening the sample to wait
  for Skiko diagnostics to report exactly the test-injected `160x96` render
  size timed out in the attached-window smoke. This was reverted to the stable
  check that the attached host renders, reports no Skiko failure, and reports a
  non-negative size when the upstream diagnostics expose one. The failed
  strict-size probe is not currently treated as a skiko-winui or kotlin-winrt
  bug because the reported Skiko platform size is not guaranteed to equal the
  Compose `WindowInfo` test size.
- **2026-06-03 Store CDB latest dump retest:** Store CDB
  `10.0.29547.1002` analyzed
  `%LOCALAPPDATA%\CrashDumps\java.exe.51408.dmp`; the log is
  `out/compose-multiplatform-core/windbg-java-51408.log`. The bucket is again
  `STOWED_EXCEPTION_c000027b_CoreMessagingXP.dll!Microsoft::UI::Dispatching::DispatcherQueue::DeferInvokeCallback`.
  The native stack goes through `KERNELBASE!RaiseFailFastException`,
  `combase!RoFailFastWithErrorContextInternal2`,
  `CoreMessagingXP!Microsoft::UI::Dispatching::DispatcherQueue::DeferInvokeCallback`,
  CoreMessaging deferred-call dispatch, and
  `Microsoft_UI_Xaml!DirectUI::FrameworkApplication::StartDesktop`. The loaded
  `Microsoft.UI.Xaml.dll` is Windows App SDK `3.1.8.2604` from the staged
  kotlin-winrt application package. This is the same application
  lifetime/dispatcher teardown failure as the previous Store CDB evidence.
- **2026-06-03 cached snapshot retest:** with the currently cached Maven
  snapshots, Gradle dependency insight resolves `skiko-winui` to
  `0.0.0-20260603.023842-2` and kotlin-winrt runtime/authoring artifacts to
  `0.1.0-20260603.042831-23`. `:compose:ui:ui:compileKotlinWinuiJvm` and
  `WinUISkikoRenderHostTest` pass. The repository-local WinUI sample reaches
  `compose-winui-sample: skiko render diagnostics`,
  `compose-winui-sample: saveable state restored`,
  `compose-winui-sample: retained value restored`, and the final
  `compose-winui-sample: text input session cancellation` log before the same
  `NTSTATUS 0xC0000005` process exit. A `--refresh-dependencies` retest was
  blocked by external Maven/Google repository TLS handshake failures while
  resolving buildSrc dependencies, so this run does not prove whether a newer
  upstream snapshot fixes KWINRT-024.
- **2026-06-03 Store WinDbg retest evidence:** Store WinDbg
  `10.0.29547.1002` analyzed
  `%LOCALAPPDATA%\CrashDumps\javaw.exe.55656.dmp`; the log is
  `out/compose-multiplatform-core/windbg-javaw-55656.log`. The bucket is
  `STOWED_EXCEPTION_c000027b_Microsoft.UI.Xaml.dll!FailFastWithStowedExceptions`;
  the stack goes through `KERNELBASE!RaiseFailFastException`,
  `combase!RoFailFastWithErrorContextInternal2`,
  `Microsoft_UI_Xaml!FailFastWithStowedExceptions`, and
  `Microsoft_UI_Xaml!DirectUI::FrameworkApplication::StartDesktop`. The stowed
  exception parameters include `0x8007000e`. This differs from the older
  unloaded-XAML AV dump but still points at WinUI/XAML runtime teardown or
  application lifetime, not a Java/Kotlin managed exception.
- **Current compose-winui policy:** do not block skiko-winui integration or
  follow-on compose-winui work on this teardown crash for now. Treat the sample
  reaching `compose-winui-sample: text input session cancellation` as successful
  validation of the current skiko-winui integration path, and revisit this issue
  only when teardown correctness becomes the active focus again.

## KWINRT-025: Authored TypeDetails validation compares formatting differences

- **Status:** Open upstream/plugin in kotlin-winrt Maven snapshot
  `0.1.0-SNAPSHOT` as of 2026-06-02.
- **Observed in:** `validateCompileKotlinWinuiJvmWinRtAuthoredCandidates` after
  clean regeneration with `--no-build-cache --rerun-tasks`.
- **Symptom:** scanner and compiler IR authored TypeDetails handoff files are
  reported as changed for
  `WinRT_WinUIRootContentControl_TypeDetails.kt` and
  `WinRT_WinUIXamlApplication_TypeDetails.kt`, even though the generated files
  differ only by KotlinPoet line wrapping/formatting.
- **compose-winui workaround:** `compose/ui/ui/build.gradle` normalizes this
  handoff immediately before validation by copying the scanner TypeDetails text
  to the matching compiler file only when the two files are equal after
  whitespace removal. Semantic mismatches still fail validation.
- **Validation:** reproduced after clearing `out/compose-multiplatform-core`
  module build directories and rerunning the sample with `--no-build-cache
  --rerun-tasks`.
