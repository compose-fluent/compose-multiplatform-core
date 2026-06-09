# kotlin-winrt issues found by compose-winui

This file tracks kotlin-winrt gaps that affect compose-winui. Use stable
`KWINRT-###` ids from compose-winui workaround comments so the workaround can be
removed when the upstream behavior is fixed.

Keep closed issues short. They should state the final outcome and validation
baseline, not every retest attempt.

## Current upstream triage

- **Open upstream/runtime:** none known for the current compose-winui
  validation path.
- **Open upstream/plugin:** `KWINRT-025`, `KWINRT-030`, `KWINRT-031`,
  `KWINRT-032`, and `KWINRT-033`.
- **Open compose-side workarounds:** `KWINRT-025` and `KWINRT-030`.
- **Compose/application policy, not kotlin-winrt helpers:** `KWINRT-012`
  clipboard synchronization and `KWINRT-019` focus timing.
- **Closed/fixed or superseded:** `KWINRT-001`, `KWINRT-002`, `KWINRT-003`,
  `KWINRT-005`, `KWINRT-006`, `KWINRT-007`, `KWINRT-009`,
  `KWINRT-010`, `KWINRT-011`, `KWINRT-013`, `KWINRT-014`, `KWINRT-015`,
  `KWINRT-016`, `KWINRT-017`, `KWINRT-018`, `KWINRT-020`, `KWINRT-021`,
  `KWINRT-022`, `KWINRT-023`, `KWINRT-026`, `KWINRT-004`, `KWINRT-008`,
  `KWINRT-028`, `KWINRT-029`, `KWINRT-034`, `KWINRT-035`, and
  `KWINRT-036`.

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

- **Status:** Open in the published kotlin-winrt Maven snapshots consumed by
  compose-winui; fixed locally in `kotlin-winrt` but not yet validated as fixed
  from Maven artifacts.
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
  `0.1.0-20260603.042831-23`. Direct Sonatype snapshot metadata reads confirm
  these are still the latest published `io.github.compose-fluent` snapshots as
  of this retest; Gradle `--refresh-dependencies` remains blocked by external
  Maven/Google repository TLS handshake failures while resolving buildSrc
  dependencies. `:compose:ui:ui:compileKotlinWinuiJvm` and
  `WinUISkikoRenderHostTest` pass. The repository-local WinUI sample reaches
  `compose-winui-sample: skiko render diagnostics`,
  `compose-winui-sample: saveable state restored`,
  `compose-winui-sample: retained value restored`, and the final
  `compose-winui-sample: text input session cancellation` log before the same
  `NTSTATUS 0xC0000005` process exit.
- **2026-06-03 focused Skiko sample split:** added a focused
  `:compose:ui:ui:winui-samples:runWinUISkikoSample` task that runs only the
  unattached Skiko scheduler deferral and attached render diagnostics smokes.
  This focused task reaches both Skiko smoke logs and exits successfully without
  producing a new WER dump. The default full `runWinUIViewSample` entrypoint
  still reaches `compose-winui-sample: text input session cancellation` and then
  exits with the same `NTSTATUS 0xC0000005`; no newer dump was produced, so the
  latest native evidence remains `java.exe.51408.dmp`.
- **2026-06-03 kt-winrt fix-claim retest:** direct Sonatype snapshot metadata
  and Gradle dependency insight still resolve `winrt-runtime` /
  `winrt-runtime-jvm` to `0.1.0-20260603.042831-23`,
  `winrt-gradle-plugin` to `0.1.0-20260603.043142-4`, and
  `winrt-compiler-plugin` to `0.1.0-20260603.042831-23`.
  `:compose:ui:ui:winui-samples:runWinUIViewSample` still reaches the final
  `compose-winui-sample: text input session cancellation` log and fails with
  process exit `NTSTATUS 0xC0000005`. This run did not produce a newer WER dump,
  so no fresh native bucket was available to analyze.
- **2026-06-03 skiko-winui typed-diagnostics retest:** after resolving
  `skiko-winui` / `skiko-winui-windows` to
  `0.0.0-20260603.075139-3` and removing the compose-side reflection bridge for
  render diagnostics, `:compose:ui:ui:winui-samples:runWinUISkikoSample` passes.
  The full `:compose:ui:ui:winui-samples:runWinUIViewSample` still reaches
  `compose-winui-sample: text input session cancellation` and exits with
  `NTSTATUS 0xC0000005`. No newer `java*.dmp` appeared in
  `%LOCALAPPDATA%\CrashDumps`, so there was no new native dump to analyze.
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
- **2026-06-03 kotlin-winrt fix-claim retest after cache clear:** stopped
  Gradle/Kotlin daemons, cleared the targeted kotlin-winrt `0.1.0-SNAPSHOT`
  Gradle artifact and descriptor cache directories, and reran the full sample.
  Direct Sonatype snapshot metadata still reports `winrt-runtime` and
  `winrt-compiler-plugin` `0.1.0-20260603.042831-23`, with
  `winrt-gradle-plugin` `0.1.0-20260603.043142-4`; Gradle downloaded fresh
  kotlin-winrt artifacts at 2026-06-03 16:26-16:27 local time. The full
  `:compose:ui:ui:winui-samples:runWinUIViewSample` again reached
  `compose-winui-sample: text input session cancellation` and exited with
  `NTSTATUS 0xC0000005`.
- **2026-06-03 cache-clear dump evidence:** Store CDB
  `10.0.29547.1002` analyzed the new dumps
  `%LOCALAPPDATA%\CrashDumps\java.exe.41020.dmp` and
  `%LOCALAPPDATA%\CrashDumps\java.exe(1).41020.dmp`; logs are
  `out/compose-multiplatform-core/windbg-java-41020.log` and
  `out/compose-multiplatform-core/windbg-java-41020-1.log`. The first dump is
  back in the XAML dynamic metadata teardown bucket
  `INVALID_POINTER_READ_c0000005_Microsoft.UI.Xaml.dll!ctl::ComPtr_ABI::Microsoft::UI::Xaml::IFrameworkElement_::InternalRelease`,
  through
  `Microsoft_UI_Xaml!CCustomDependencyProperty::~CCustomDependencyProperty`,
  `Microsoft_UI_Xaml!DirectUI::DynamicMetadataStorage::~DynamicMetadataStorage`,
  `Microsoft_UI_Xaml!DeinitializeDll`, `ntdll!LdrUnloadDll`, and
  `combase!CoUninitialize`. The second dump is the paired unloaded-XAML execute
  bucket
  `SOFTWARE_NX_FAULT_INVALID_POINTER_EXECUTE_c0000005_Microsoft.UI.Xaml.dll!Unloaded`
  through `ntdll!RtlpFlsDataCleanup` / `ntdll!LdrShutdownThread`. This confirms
  the latest reproduced failure is still teardown/lifetime related and not a
  managed exception or a Skiko render failure.
- **2026-06-03 Skiko cache-clear retest:** after clearing the targeted
  `io.github.compose-fluent` Skiko snapshot Gradle cache, Gradle attempted to
  redownload `skiko-winui` `0.0.0-20260603.075139-3`; external Gradle TLS
  handshakes blocked that direct download, so the same timestamped
  `skiko-winui` / `skiko-winui-windows` jars were fetched with PowerShell and
  installed into Maven local. The full sample still reached
  `compose-winui-sample: text input session cancellation` and exited with
  `NTSTATUS 0xC0000005`. No newer WER dump was produced after this run, so the
  latest native evidence remains the paired `java.exe.41020.dmp` dumps above.
- **2026-06-03 17:06 +08 kt-winrt fix-claim retest:** direct Sonatype snapshot
  metadata still reports `winrt-runtime`, `winrt-runtime-jvm`,
  `winrt-authoring`, and `winrt-compiler-plugin`
  `0.1.0-20260603.042831-23`, with `winrt-gradle-plugin`
  `0.1.0-20260603.043142-4`. Gradle dependency insight resolves the sample
  runtime classpath to the same kotlin-winrt coordinates and `skiko-winui`
  `0.0.0-20260603.075139-3`. A normal full sample run and a forced
  `--rerun-tasks` run both reach
  `compose-winui-sample: text input session cancellation` and exit with
  `NTSTATUS 0xC0000005`. No newer WER dump was produced; the latest native
  evidence remains `%LOCALAPPDATA%\CrashDumps\java.exe.41020.dmp` /
  `%LOCALAPPDATA%\CrashDumps\java.exe(1).41020.dmp`.
- **2026-06-03 18:49 +08 kt-winrt fix-claim retest:** direct Sonatype snapshot
  metadata still reports `winrt-runtime` and `winrt-compiler-plugin`
  `0.1.0-20260603.042831-23`; transient SSL handshake failures prevented
  reading every metadata file directly, and Gradle `--refresh-dependencies`
  remains blocked by external Maven/Google TLS handshake failures while
  resolving settings/buildSrc dependencies. A cached Gradle dependency insight
  still resolves the sample runtime classpath to `winrt-runtime`,
  `winrt-runtime-jvm`, and `winrt-authoring` `0.1.0-20260603.042831-23`,
  with `skiko-winui` `0.0.0-20260603.075139-3`. The full
  `:compose:ui:ui:winui-samples:runWinUIViewSample` again reaches the final
  `compose-winui-sample: text input session cancellation` log and exits with
  `NTSTATUS 0xC0000005`. No newer WER dump was produced; the latest native
  evidence remains `%LOCALAPPDATA%\CrashDumps\java.exe.41020.dmp` /
  `%LOCALAPPDATA%\CrashDumps\java.exe(1).41020.dmp`.
- **2026-06-03 19:21 +08 kt-winrt fix-claim cache-clear retest:** direct
  Sonatype snapshot metadata still reports `winrt-runtime`,
  `winrt-runtime-jvm`, `winrt-authoring`, and `winrt-compiler-plugin`
  `0.1.0-20260603.042831-23`, with `winrt-gradle-plugin`
  `0.1.0-20260603.043142-4`. After stopping Gradle daemons and clearing the
  targeted `io.github.compose-fluent` snapshot artifact and descriptor caches
  under `GRADLE_USER_HOME=F:\Dependencies\gradle`, Gradle dependency insight
  re-resolved the sample runtime classpath to the same kotlin-winrt coordinates
  and `skiko-winui` `0.0.0-20260603.075139-3`. The full
  `:compose:ui:ui:winui-samples:runWinUIViewSample` still reaches the final
  `compose-winui-sample: text input session cancellation` log and exits with
  `NTSTATUS 0xC0000005`. Store CDB analyzed the fresh dumps
  `%LOCALAPPDATA%\CrashDumps\java.exe.58444.dmp` and
  `%LOCALAPPDATA%\CrashDumps\java.exe(1).58444.dmp`; logs are
  `out/compose-multiplatform-core/windbg-java-58444.log` and
  `out/compose-multiplatform-core/windbg-java-58444-1.log`. The first dump is
  again
  `INVALID_POINTER_READ_c0000005_Microsoft.UI.Xaml.dll!ctl::ComPtr_ABI::Microsoft::UI::Xaml::IFrameworkElement_::InternalRelease`
  through `Microsoft_UI_Xaml!CCustomDependencyProperty::~CCustomDependencyProperty`,
  `Microsoft_UI_Xaml!DirectUI::DynamicMetadataStorage::~DynamicMetadataStorage`,
  `Microsoft_UI_Xaml!DeinitializeDll`, and `combase!CoUninitialize`. The paired
  dump is again
  `SOFTWARE_NX_FAULT_INVALID_POINTER_EXECUTE_c0000005_Microsoft.UI.Xaml.dll!Unloaded`
  through `ntdll!RtlpFlsDataCleanup` / `ntdll!LdrShutdownThread`. This retest
  does not resolve `KWINRT-024`; it confirms the currently published Maven
  snapshot is still in the same XAML dynamic-metadata / unloaded-XAML teardown
  bucket.
- **2026-06-03 23:00 +08 skiko snapshot retest:** after clearing targeted
  Skiko Gradle snapshot caches, Gradle resolved `skiko-winui` and
  `skiko-winui-windows` to `0.0.0-20260603.150039-4` while kotlin-winrt
  remained at `0.1.0-20260603.042831-23`. The focused Skiko sample passes, but
  the full `:compose:ui:ui:winui-samples:runWinUIViewSample` still reaches the
  final `compose-winui-sample: text input session cancellation` log and exits
  with `NTSTATUS 0xC0000005`. No newer WER dump was produced, so the latest
  native evidence remains `%LOCALAPPDATA%\CrashDumps\java.exe.58444.dmp` /
  `%LOCALAPPDATA%\CrashDumps\java.exe(1).58444.dmp`.
  - **Current compose-winui policy:** do not block skiko-winui integration or
    follow-on compose-winui work on this teardown crash for now. Treat the sample
    reaching `compose-winui-sample: text input session cancellation` as successful
    validation of the current skiko-winui integration path, and revisit this issue
    only when teardown correctness becomes the active focus again.
  - **2026-06-04 local fix validation:** CDB confirmed the final crash was XAML
    worker-thread FLS cleanup entering `Microsoft.UI.Xaml.dll` dynamic metadata
    teardown and releasing a `Controls::IPanel` / `IFrameworkElement` vtable from
    `Microsoft.UI.Xaml.Controls.dll` after that module was already on an unload
    path. The local `kotlin-winrt` fix keeps real `CoUninitialize`, clears
    JVM-held XAML metadata/activation/composable caches before COM uninitialization,
    wraps generated `Application.Start` in a runtime-owned native module lifetime
    guard, and uses the generic `NativeModulePins` adapter to keep
    `Microsoft.UI.Xaml.dll` plus `Microsoft.UI.Xaml.Controls.dll` mapped for the
    XAML application lifetime. With the fixed local Maven artifacts,
    `:compose:ui:ui:winui-samples:runWinUIViewSample` reaches
    `compose-winui-sample: text input session cancellation` and exits successfully.
- **2026-06-04 Maven snapshot retest:** direct Sonatype metadata and Gradle
  dependency insight resolve `winrt-runtime`, `winrt-runtime-jvm`,
  `winrt-authoring`, and `winrt-compiler-plugin` to
  `0.1.0-20260604.125452-24`, with `winrt-gradle-plugin`
  `0.1.0-20260604.125740-5`. compose-winui also updated its custom sample
  `JavaExec` tasks to follow the README guidance by depending on both
  `stageWinRtRuntimeAssets` and `buildWinRtAuthoringHost`.
- **2026-06-04 Kotlin 2.4 retest:** after upgrading this repository to Kotlin
  `2.4.0`, `:compose:ui:ui:compileKotlinWinuiJvm`, the focused WinUI JVM tests,
  and `:compose:ui:ui:winui-samples:runWinUISkikoSample` pass with the
  published kotlin-winrt `0.1.0-20260604.125452-24` runtime/compiler artifacts.
  The full `:compose:ui:ui:winui-samples:runWinUIViewSample` still reaches
  `compose-winui-sample: text input session cancellation` and exits with
  `NTSTATUS 0xC0000005`. Fresh WER dumps were produced at
  `%LOCALAPPDATA%\CrashDumps\java.exe.5868.dmp` and
  `%LOCALAPPDATA%\CrashDumps\java.exe(1).5868.dmp`; CDB confirmed the dump
  stores an access violation but timed out before producing a useful stack.

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

## KWINRT-026: Core text input focus registration fail-fast after full smoke

- **Status:** Fixed for the current compose-winui validation path with Maven
  snapshots on 2026-06-08.
- **Observed in:** `:compose:ui:ui:winui-samples:runWinUIViewSample` after the
  latest 2026-06-03 Maven snapshot retest. The full sample reaches
  `compose-winui-sample: text input session cancellation` before the process
  exits with `NTSTATUS 0xC0000005`. The focused
  `:compose:ui:ui:winui-samples:runWinUISkikoSample` task reaches
  `compose-winui-sample: skiko unattached scheduler deferred` and
  `compose-winui-sample: skiko render diagnostics`, then exits successfully
  without producing a new WER dump.
- **Snapshot baseline:** `skiko-winui` / `skiko-winui-windows`
  `0.0.0-20260603.023842-2`, `winrt-runtime` / `winrt-authoring`
  `0.1.0-20260603.042831-23`, `winrt-gradle-plugin`
  `0.1.0-20260603.043142-4`, and `winrt-compiler-plugin`
  `0.1.0-20260603.042831-23`.
- **Native evidence:** Store CDB `10.0.29547.1002` analyzed
  `%LOCALAPPDATA%\CrashDumps\java.exe.11048.dmp`; the log is
  `out/compose-multiplatform-core/windbg-java-11048.log`. The failure bucket is
  `APPLICATION_FAULT_675_textinputframework.dll!FailFastWithHR`; exception
  parameter 1 is `HRESULT 0x80004005`. The stack goes through
  `KERNELBASE!RaiseFailFastException`, `textinputframework!FailFastWithHR`,
  `textinputframework!TextboxRegistration::SelectionChanged`,
  `textinputframework!TextInputClient::NotifySelectionChanged`, `msctf`, and
  `Windows_UI_Core_TextInput!Windows::UI::Text::Core::CEditContext::NotifyFocusEnter`.
  The loaded `Microsoft.UI.Xaml.dll` is Windows App SDK `3.1.8.2604` from the
  staged kotlin-winrt/skiko WinUI application package.
- **Assessment:** this was a separate bucket from the `KWINRT-024`
  CoreMessaging/XAML teardown dumps. It pointed at WinUI core text input focus
  registration or lifetime during the full sample's text input path, not a
  Skiko render failure and not a Java/Kotlin managed exception.
- **2026-06-05 SkiaWinUISample dump:** Store WinDbg
  `Microsoft.WinDbg_1.2603.20001.0` was available but its `WinDbgX.exe`
  command-line launch did not produce a headless log; the same dump was then
  analyzed with local CDB and symbols. The latest
  `%LOCALAPPDATA%\CrashDumps\SkiaWinUISample.exe.13612.dmp` is the same
  fail-fast bucket: exception code `0x675` at
  `KERNELBASE!RaiseFailFastException`, stack through
  `textinputframework!FailFastWithHR` with `HRESULT 0x80004005`,
  `TextboxRegistration::SelectionChanged`,
  `TextInputClient::NotifySelectionChanged`,
  `TextInputClient::EditControlRegister`, `msctf`, and
  `Windows_UI_Core_TextInput!Windows::UI::Text::Core::CEditContext::NotifyFocusEnter`.
  The process was the upstream skiko sample host
  `compose-fluent-skiko\samples\SkiaWinUISample`, loaded Windows App SDK
  `Microsoft.UI.Xaml.dll` `3.1.8.2604`, and is evidence that this native
  text-input fail-fast is not specific to compose-winui's full smoke sample.
- **Resolution:** compose-winui now creates a CoreText edit context/session only
  when `compose.winui.textInput.coreText.enabled=true`; when enabled, the
  automatic attach path calls `CoreTextEditContext.notifyFocusEnter()` so the
  native focus registration path is exercised instead of merely creating an
  inert edit context.
- **Validation:** with JDK 25 and current Maven snapshots,
  `:compose:ui:ui:winui-samples:runWinUITextInputSample` and
  `:compose:mpp:demo-winui:runWinUIMppSample` pass without reproducing this
  fail-fast. Reopen only with fresh native crash evidence from the current
  snapshots.

## KWINRT-027: Maven compiler plugin snapshot requires Kotlin 2.4 compiler APIs

- **Status:** Resolved for compose-winui by upgrading this repository to Kotlin
  `2.4.0`.
- **Observed in:** `:compose:ui:ui:compileKotlinWinuiJvm` after clearing the
  targeted Gradle snapshot caches and resolving kotlin-winrt artifacts to
  `0.1.0-20260604.125452-24` / Gradle plugin `0.1.0-20260604.125740-5`.
- **Symptom:** the Kotlin compiler plugin fails to load before WinUI runtime
  validation can run:
  `NoClassDefFoundError: org/jetbrains/kotlin/extensions/ExtensionPointDescriptor`
  from
  `io.github.composefluent.winrt.compiler.KotlinWinRtCompilerPluginRegistrar.registerExtensions`.
- **Evidence:** the current repository uses Kotlin `2.3.20`; its
  `kotlin-compiler-embeddable-2.3.20.jar` does not contain
  `org/jetbrains/kotlin/extensions/ExtensionPointDescriptor.class`. The freshly
  resolved `winrt-compiler-plugin` POM declares `kotlin-stdlib` and
  `kotlin-compiler-embeddable` `2.4.0`, and local inspection confirms
  `kotlin-compiler-embeddable-2.4.0.jar` does contain the missing class.
- **Resolution:** compose-winui now uses Kotlin `2.4.0`, adds a `KOTLIN_2_4`
  build target, and applies the small Kotlin 2.4 compatibility fixes needed for
  `buildSrc`, `compose-ui`, and `ui-text`. `compileKotlinWinuiJvm` now passes and
  the runtime validation proceeds to the separate `KWINRT-024` full-sample
  native crash.

## KWINRT-028: Prebuilt WinUI projection Application.start callback hang

- **Status:** Fixed upstream in kotlin-winrt Maven snapshot `0.1.0-SNAPSHOT` as
  of 2026-06-07.
- **Observed in:** `:compose:ui:ui:winui-samples:runWinRtApplicationHost` and
  `:compose:ui:ui:winui-samples:runWinUISkikoSample` after switching
  compose-winui from local Windows App SDK NuGet projection generation to
  prebuilt projection artifacts.
- **Snapshot baseline:** `skiko-winui` / `skiko-winui-windows`
  `0.0.0-20260606.031302-6`, `winrt-runtime-jvm`
  `0.1.0-20260605.202352-37`, `winrt-gradle-plugin`
  `0.1.0-20260605.202635-18`,
  `winrt-projections-windows-sdk`
  `10.0.26100.0-kotlin-winrt-0.1.0-20260605.210602-1`, and
  `winrt-projections-windows-app-sdk`
  `2.1.3-kotlin-winrt-0.1.0-20260605.211043-1`.
- **Symptom:** the process does not throw the former
  `InputSystemCursor cannot inherit from final InputCursor`
  `IncompatibleClassChangeError`; instead it remains alive until killed. The
  JavaExec thread dump for the focused Skiko sample shows the main thread
  running inside `microsoft.ui.xaml.Application$Metadata.start`
  (`microsoft_ui_xaml.kt:561`) through the FFM downcall, with no Compose
  sample progress beyond the call into `Application.start`.
- **Evidence:** direct inspection of the new `skiko-winui` jar reports zero
  `microsoft/**` or `windows/**` projection classes, so `SKIKO-007` projection
  ownership shadowing is fixed. The focused JavaExec classpath contains
  `skiko-winui` followed by
  `winrt-projections-windows-app-sdk` and
  `winrt-projections-windows-sdk`, and no overlapping projection classes from
  skiko. A temporary compose-winui change that retained the created
  `WinUIXamlApplication` in a module-level variable did not change the hang;
  the callback still did not reach the sample launch path.
- **Expected behavior:** `Application.start { ... }` should invoke the
  initialization callback, create the authored `Application` subclass, and then
  dispatch `onLaunched`, matching the current kotlin-winrt README and upstream
  samples.
- **Resolution:** compose-winui now validates the generated `Application.start`
  path with explicit `type(...)` projection declarations and no prebuilt full
  projection dependencies.
- **Validation:** `runWinUISkikoSample` logs
  `compose-winui-sample: application starting mode=skiko` and exits
  successfully. `runWinUIViewSample` logs
  `compose-winui-sample: application starting mode=full`,
  `compose-winui-sample: application created`, window creation, focus/input,
  generated event cleanup, and Skiko diagnostics before exiting successfully.

## KWINRT-029: IContentCoordinateConverter single-point overload renders array path

- **Status:** Fixed upstream in kotlin-winrt Maven snapshot `0.1.0-SNAPSHOT` as
  of 2026-06-07.
- **Observed in:** `:compose:ui:ui:generateWinRtProjections` and
  `:compose:ui:ui:compileKotlinWinuiJvm` after switching compose-winui away
  from prebuilt full projection artifacts and back to explicit `type(...)`
  declarations.
- **Snapshot baseline:** `skiko-winui` / `skiko-winui-windows`
  `0.0.0-20260606.141637-7`, `winrt-runtime-jvm`
  `0.1.0-SNAPSHOT`, and `winrt-gradle-plugin` `0.1.0-SNAPSHOT` resolved from
  Maven snapshots on 2026-06-06.
- **Symptom:** compose-winui declares explicit `type(...)` entries for the
  Windows SDK and Windows App SDK types it uses, including
  `Microsoft.UI.Xaml.Controls.Canvas`,
  `Microsoft.UI.Xaml.Controls.ContentControl`,
  `Microsoft.UI.Xaml.Controls.MenuFlyout`,
  `Microsoft.UI.Xaml.Input.FocusManager`,
  `Microsoft.UI.Input.InputSystemCursor`, and
  `Microsoft.UI.Xaml.Media.DesktopAcrylicBackdrop`. The generator emits the
  requested reduced projection surface, but the Windows App SDK dependency
  closure also includes `Microsoft.UI.Content.IContentCoordinateConverter`.
  The generated Kotlin for that interface does not compile.
- **Evidence:** generation with filtered Windows App SDK metadata emits the
  requested types such as `Windows.System.Launcher`,
  `Windows.UI.ViewManagement.UISettings`,
  `Microsoft.UI.Input.InputSystemCursor`,
  `Microsoft.UI.Xaml.Controls.Canvas`, `ContentControl`, and `MenuFlyout`.
  Compilation then fails in generated
  `microsoft/ui/content/microsoft_ui_content.kt` because
  `IContentCoordinateConverter.convertLocalToScreen(localPoint: Point)` is
  rendered with the unrelated `localPoints` array path and returns
  `Array<PointInt32>` where `PointInt32` is expected. The WinMD dependency
  chain appears legitimate; compose-winui should not exclude
  `IContentCoordinateConverter`, `ContentCoordinateConverter`,
  `ContentIsland`, `IXamlRoot3`, or `IXamlRoot4` to hide this bug.
- **Expected behavior:** the single-point
  `convertLocalToScreen(localPoint: Windows.Foundation.Point)` overload should
  use the `localPoint` parameter and return `Windows.Graphics.PointInt32`.
  The array overload should remain the only overload that uses `localPoints`
  and returns `Array<PointInt32>`.
- **Resolution:** the latest generated
  `microsoft/ui/content/microsoft_ui_content.kt` keeps
  `IContentCoordinateConverter`, `ContentCoordinateConverter`, and
  `ContentIsland` in the dependency closure. The single-point
  `convertLocalToScreen(localPoint: Point)` overload now has an independent
  `PointInt32` return path, while the array overloads remain separate.
- **Validation:** `:compose:ui:ui:compileKotlinWinuiJvm` and
  `:compose:ui:ui:winuiJvmTest` pass with 70 generated projection files in the
  reduced explicit-type surface.

## KWINRT-030: KMP partial dependency checker resolves kotlin-winrt identity early

- **Status:** Open upstream/plugin in kotlin-winrt Maven snapshot
  `0.1.0-SNAPSHOT` as of 2026-06-07.
- **Observed in:** `:compose:ui:ui:kmpPartiallyResolvedDependenciesChecker`
  when running `:compose:ui:ui:compileKotlinWinuiJvm` together with
  `:compose:ui:ui:winuiJvmTest` after switching compose-winui to explicit
  `type(...)` projection declarations.
- **Snapshot baseline:** `winrt-runtime-jvm`
  `0.1.0-20260607.100854-44`, `winrt-gradle-plugin`
  `0.1.0-20260607.101153-25`, and `skiko-winui`
  `0.0.0-20260607.101016-8`.
- **Symptom:** the checker fails before projection compilation with
  `Cannot mutate the dependencies of configuration
  ':compose:ui:ui:kotlinWinRtLibraryDependencyIdentity' after the
  configuration was resolved`. The stacktrace shows Kotlin's
  `KmpPartiallyResolvedDependenciesChecker` resolving the graph, then
  `addKotlinDomApiDependency` firing while Gradle is resolving dependencies.
- **Expected behavior:** kotlin-winrt should finish configuring
  `kotlinWinRtLibraryDependencyIdentity` before other Gradle/Kotlin validation
  tasks can observe or resolve it, or should use a lazy provider path that does
  not mutate the configuration after resolution begins.
- **compose-winui workaround:** disable
  `kmpPartiallyResolvedDependenciesChecker` only when the WinUI JVM target and
  kotlin-winrt Gradle plugin are enabled. This is a narrow validation unblocker;
  normal projection shape remains explicit `type(...)` declarations.
- **2026-06-08 navigation WinUI variant retest:** after adding repository-local
  WinUI JVM recompile variants for `navigation-compose` and `navigation3-ui`,
  `:compose:ui:ui:compileKotlinWinuiJvm` still passes, but
  `:compose:ui:ui:winui-samples:runWinUISkikoSample` can also trigger the same
  early-resolution failure while resolving the sample `runtimeClasspath` for its
  `project(:compose:ui:ui)` dependency. The symptom remains
  `Cannot mutate the dependencies of configuration
  ':compose:ui:ui:kotlinWinRtLibraryDependencyIdentity' after the configuration
  was resolved`; this extends `KWINRT-030` beyond the partially-resolved
  dependency checker into WinUI sample classpath resolution order.
- **2026-06-08 WinUI MPP PRI bootstrap retest:** adding app-owned PRI inputs to
  `:compose:mpp:demo-winui` made
  `:compose:mpp:demo-winui:stageWinRtRuntimeAssets` fail during task graph
  construction with the same mutation class, this time on
  `:compose:mpp:demo-winui:kotlinWinRtIdentity`. The trigger was Kotlin's
  late stdlib dependency addition while Gradle was already visiting task
  dependencies for the resolved identity file collection. The narrow
  compose-winui workaround is to declare `implementation(kotlin("stdlib"))`
  explicitly in the WinUI MPP sample so Kotlin does not add it during task
  dependency resolution. With that workaround, PRI staging, packaging
  validation, `runWinUIMppSample`, and `:compose:ui:ui:compileKotlinWinuiJvm`
  pass.

## KWINRT-031: Generated authoring TypeDetails use projection-unsafe runtime casts

- **Status:** Open upstream/compiler generator in kotlin-winrt Maven snapshot
  `0.1.0-SNAPSHOT` as of 2026-06-07.
- **Observed in:** `:compose:ui:ui:compileKotlinWinuiJvm` after removing
  compose-winui's handwritten projection-unsafe casts to WinRT runtime classes.
- **Snapshot baseline:** `winrt-runtime-jvm`
  `0.1.0-20260607.100854-44`, `winrt-gradle-plugin`
  `0.1.0-20260607.101153-25`, and `skiko-winui`
  `0.0.0-20260607.101016-8`.
- **Symptom:** compilation still reports warnings such as
  `WinRT runtime class cast to Microsoft.UI.Xaml.Controls.ContentControl is not
  projection-safe; use WinRT projection cast helpers instead`, plus equivalent
  warnings for `Control`, `FrameworkElement`, `UIElement`, and `Application`.
- **Evidence:** direct source search no longer finds handwritten
  `as` / `as?` casts to those WinRT runtime classes in the WinUI source or
  sample. The remaining casts are generated under
  `build/generated/kotlin-winrt-compiler-authoring/.../WinRT_*_TypeDetails.kt`,
  for example `(value as ContentControl)`,
  `(value as Control)`, `(value as FrameworkElement)`,
  `(value as UIElement)`, and `(value as Application)` before invoking
  generated `__winrtAuthoringInvoke...` methods.
- **Expected behavior:** generated authoring TypeDetails should use a
  projection-safe rewrap/query path or otherwise avoid emitting runtime-class
  Kotlin casts that kotlin-winrt's own compiler diagnostics flag as unsafe.
- **compose-winui workaround:** none. Handwritten compose-winui casts were
  replaced with narrow QueryInterface + runtime-class wrapper helpers, but the
  generated authoring diagnostics remain until kotlin-winrt changes the
  compiler generator output.

## KWINRT-032: Generated WinUI projections are final when WinMD supports subclassing

- **Status:** Open upstream/generator in kotlin-winrt Maven snapshot
  `0.1.0-SNAPSHOT` as of 2026-06-08.
- **Observed in:** `:compose:mpp:demo-winui:runWinUIMppSample` after removing
  duplicate projection classes from the `skiko-winui` runtime jar, and
  `:compose:ui:ui:winui-samples:runWinRtApplicationHost`.
- **Symptom:** adding `type("Microsoft.UI.Xaml.Controls.Grid")` to the
  compose-ui explicit projection surface generates
  `public final class microsoft.ui.xaml.controls.Grid`. The skiko-winui authored
  class `org.jetbrains.skiko.winui.WinUISkiaHostPanel` extends `Grid`, so class
  loading fails with `IncompatibleClassChangeError: class
  org.jetbrains.skiko.winui.WinUISkiaHostPanel cannot inherit from final class
  microsoft.ui.xaml.controls.Grid`. The native host path also fails when
  generated `Microsoft.UI.Input.InputSystemCursor` tries to inherit from final
  generated `Microsoft.UI.Input.InputCursor`.
- **Evidence:** `javap` on the compose-ui generated `ui-winuijvm` jar shows
  `public final class microsoft.ui.xaml.controls.Grid`, while `javap` on the
  current published `skiko-winui` jar shows its generated `Grid` as non-final
  and implementing `WinRtComposableObject`. `Panel` generated by compose-ui is
  also non-final and composable, so the failure is specific to the generated
  `Grid` shape.
- **Expected behavior:** generated projections for WinUI classes that can be
  subclassed from authored Kotlin classes or by other WinRT runtime classes,
  including `Microsoft.UI.Xaml.Controls.Grid` and
  `Microsoft.UI.Input.InputCursor`, should be non-final and expose the
  composable/inheritable construction path expected by kotlin-winrt authoring
  and projection inheritance.
- **compose-winui workaround:** do not add `Grid` to the compose-ui projection
  surface yet. The MPP sample JavaExec task stages a filtered `skiko-winui` jar
  that removes projection classes already owned by other runtime jars while
  keeping skiko-unique support projection classes needed by
  `WinUISkiaHostPanel`. The broader bundled-projection publication issue is
  tracked by `SKIKO-007`.

## KWINRT-033: Groovy DSL cannot consume explicit WinUI type declarations without generateProjection

- **Status:** Open upstream/plugin in kotlin-winrt Maven snapshot
  `0.1.0-SNAPSHOT` as of 2026-06-08.
- **Observed in:** `compose/ui/ui/build.gradle` and
  `compose/ui/ui/winui-samples/build.gradle` after trying to follow the current
  `compose-fluent/kotlin-winrt` README WinUI setup, which uses
  `windowsSdk(...)`, `nugetPackage(...)`, and explicit `type(...)` declarations
  without `generateProjection = true`.
- **Symptom:** the Groovy DSL does not expose the two-argument
  `windowsSdk(version, includeExtensions)` overload used by the Kotlin DSL
  samples. Using the legacy three-argument form with
  `windowsSdk(version, false, false)` configures successfully, but
  `:compose:ui:ui:compileKotlinWinuiJvm` then fails because explicitly declared
  WinUI and Windows SDK types such as `FocusManager`, `Clipboard`, `Canvas`,
  `ContentControl`, `Launcher`, `UISettings`, and `InputSystemCursor` are not on
  the compile classpath.
- **Expected behavior:** Groovy builds should be able to use the same
  preprojection/explicit-type model as the README and Kotlin DSL samples:
  declare WinMD sources and `type(...)` entries without enabling full NuGet or
  Windows SDK projection generation, while still exposing the declared types to
  compilation.
- **compose-winui workaround:** keep the legacy
  `windowsSdk(version, false, true)` and `nugetPackage(...) {
  generateProjection = true }` configuration in Groovy build scripts until the
  plugin supports the README path for these modules. `compose/mpp/demo-winui`,
  which uses Kotlin DSL, already uses the no-`generateProjection` form.
- **2026-06-08 retest:** still open. The current Maven snapshot still does not
  expose the README's `windowsSdk(version, includeExtensions)` path to Groovy
  builds (`Could not find method windowsSdk() for arguments [10.0.26100.0,
  false]`). The legacy three-argument form with projection generation disabled
  configures, but `:compose:ui:ui:compileKotlinWinuiJvm` still fails with
  unresolved explicit types including `FocusManager`, `Clipboard`,
  `UISettings`, `InputSystemCursor`, `MenuFlyout`, `Launcher`, `Canvas`, and
  `ContentControl`. Keep the compose-ui and winui-samples full-projection
  workaround for now.

## KWINRT-034: WinUI direct WinMD inputs leave CompositionTarget unsupported

- **Status:** Closed/superseded on 2026-06-08. This failure was caused by
  compose-winui still passing `generateWindowsSdkProjection=true` to the
  snapshot three-argument `windowsSdk(...)` DSL while moving to the requested
  explicit `type(...)` surface.
- **Observed in:** `:compose:ui:ui:generateWinRtProjections` after removing the
  temporary `winrt-projections-windows-app-sdk` dependency and supplying the
  Windows App SDK WinMDs directly through `winmd(...)` metadata inputs while
  keeping compose-ui's explicit `type(...)` projection surface.
- **Symptom:** projection generation fails with
  `Generator requires runtime class Windows.UI.Composition.Compositor ABI
  binding CREATETARGETFORCURRENTVIEW_SLOT return to use supported ABI metadata
  before projection rendering; found
  Unsupported(Microsoft.UI.Composition.CompositionTarget)`. Adding
  `type("Microsoft.UI.Composition.CompositionTarget")` does not change the
  failure.
- **Expected behavior:** when `Microsoft.UI.winmd` is present and
  `Microsoft.UI.Composition.CompositionTarget` is explicitly declared, the
  generator should classify the WinUI composition return type with supported
  ABI metadata instead of leaving it unsupported through the
  `Windows.UI.Composition.Compositor` binding path.
- **compose-winui fix:** use `windowsSdk(version, false, false)` so Windows SDK
  metadata remains available for explicitly requested types without generating
  the broad Windows SDK projection surface.

## KWINRT-035: ItemCollection VectorChanged event binding is not planned

- **Status:** Fixed upstream in kotlin-winrt Maven snapshot
  `0.1.0-SNAPSHOT` as of 2026-06-09.
- **Observed in:** `:compose:ui:ui:generateWinRtProjections` with
  `windowsSdk(version, false, false)`, direct Windows App SDK WinMD inputs, and
  compose-ui's explicit `type(...)` projection surface.
- **Symptom:** projection generation fails with
  `Generator requires runtime class Microsoft.UI.Xaml.Controls.ItemCollection
  event VectorChanged add binding VECTORCHANGED_ADD_SLOT to be present before
  projection rendering.` Adding the Windows Foundation collection types and the
  WinUI XAML bindable collection/event types does not change the failure.
- **Expected behavior:** `ItemCollection` should either receive a valid
  `VectorChanged` event accessor binding from its WinUI collection interface
  metadata, or the generator should recognize the mapped collection runtime
  class shape and avoid requiring an event binding it cannot render.
- **Resolution:** latest Maven snapshot allows
  `:compose:ui:ui:generateWinRtProjections` to complete with the real WinUI
  `MenuFlyout` context-menu/text-toolbar path still projected.

## KWINRT-036: Windows.UI.Composition projections bind Microsoft.UI.Composition interfaces

- **Status:** Fixed upstream in kotlin-winrt Maven snapshot
  `0.1.0-SNAPSHOT` as of 2026-06-09.
- **Observed in:** `:compose:ui:ui:compileKotlinWinuiJvm` after KWINRT-035 was
  fixed and projection generation completed.
- **Symptom:** generated `windows.ui.composition` runtime classes import and
  implement `microsoft.ui.composition` interfaces. For example,
  `windows.ui.composition.CompositionObject` emits `override val dispatcher`
  while implementing a Microsoft composition interface that does not declare
  that property, and `dispatcherQueue` has a `windows.system.DispatcherQueue?`
  return type that does not match the Microsoft interface return type. Other
  generated files import unresolved Microsoft composition symbols such as
  `microsoft.ui.composition.CompositionTarget` for Windows composition APIs.
- **Expected behavior:** Windows SDK `Windows.UI.Composition` projections should
  consistently reference `windows.ui.composition` interfaces and runtime
  classes, while Windows App SDK `Microsoft.UI.Composition` projections should
  consistently reference `microsoft.ui.composition`.
- **Validation:** after refreshing Maven snapshots on 2026-06-09 and cleaning
  generated output, `:compose:ui:ui:compileKotlinWinuiJvm` completes with
  compose-ui's explicit projection surface and no generated-interface
  exclusions.
- **compose-winui workaround:** none. Compose-winui keeps the real
  CoreText/input functionality and does not exclude generated interfaces to hide
  the generator namespace mapping issue.

## KWINRT-037: Authored override parameters using Windows.Foundation.Size have no metadata

- **Status:** Open upstream/generator in kotlin-winrt Maven snapshot
  `0.1.0-SNAPSHOT` as of 2026-06-09.
- **Observed in:** `:compose:ui:ui:generateWinRtProjections` after refreshing to
  current kotlin-winrt runtime `0.1.0-SNAPSHOT:20260609.020911-47` and trying
  skiko-winui `0.0.0-20260609.030224-9`.
- **Symptom:** projection generation fails before Kotlin compilation with
  `Authored WinRT override parameter 'availableSize' of type
  'Windows.Foundation.Size' has no metadata.`
- **Evidence:** compose-ui explicitly declares `type("Windows.Foundation.Size")`
  together with `Windows.Foundation.Point` and `Windows.Foundation.Rect`, and
  supplies the Windows SDK metadata through `windowsSdk(version, false, false)`.
  The failure appears while the authoring scanner processes hosted WinUI
  classes with XAML measure/arrange override signatures, not because
  compose-winui omitted the type from the requested projection surface.
- **Expected behavior:** authored WinRT override validation should resolve
  metadata for explicitly requested Windows SDK struct types such as
  `Windows.Foundation.Size`, including when those types appear only in authored
  override parameters.
- **compose-winui workaround:** none. Do not exclude authored classes or replace
  real XAML measure/arrange participation with no-op wrappers to hide the
  generator error.
