# skiko-winui issues found by compose-winui

This file tracks Skiko / skiko-winui gaps that affect compose-winui. Use stable
`SKIKO-###` ids from compose-winui workaround comments so the workaround can be
removed when the upstream behavior is fixed.

Keep closed issues short. They should state the final outcome and validation
baseline, not every retest attempt.

## Current upstream triage

- **Open upstream/publication:** none.
- **Open compose-side integration:** `SKIKO-004`.
- **Open compose-side workarounds:** none.
- **Closed/fixed or superseded:** `SKIKO-001`, `SKIKO-002`, `SKIKO-003`.

## SKIKO-004: unattached WinUI Skiko surface can hang in flushAndSubmit

- **Status:** Open compose-side integration / upstream triage.
- **Observed in:** `io.github.compose-fluent:skiko-winui:0.0.0-SNAPSHOT`
  while adding render diagnostics to the repository-local
  `runWinUIViewSample` smoke path on 2026-06-03.
- **Failure:** A standalone `WinUIComposeView` created by the sample, sized with
  the test hook, and rendered without first attaching it to a WinUI `Window`
  left the sample JVM alive indefinitely. A `jcmd Thread.print` showed the
  WinUI UI thread inside
  `org.jetbrains.skia.DirectContext.flushAndSubmit`, called through
  `WinUISkiaLayerPlatformInterop.drawAndPresent`,
  `WinUISkiaLayer.renderNow`, and the `WinUIFrameScheduler` timer callback.
- **Why skiko's own sample does not show it:** the skiko sample exercises a
  Skia layer after it is hosted by a real WinUI window/surface. The compose
  smoke that exposed this issue rendered an unattached compose view, which is
  not the same lifetime or presentation path.
- **Current compose-winui action:** do not validate rendered frames from an
  unattached `WinUIComposeView`. `microsoft.ui.xaml.Window.setContent` now
  installs the compose root into the WinUI `Window` before starting composition,
  so that direct window entry point does not briefly start the Skiko frame
  scheduler on an unattached root. `WinUIComposeView` also defers starting the
  Skiko frame scheduler until its root is loaded, and removes the pending
  `Loaded` token on composition/view disposal. Keep render-host diagnostics
  covered by `WinUISkikoRenderHostTest`; the repository-local sample validates
  both that an unattached `WinUIComposeView` does not start the Skiko frame
  scheduler and that an attached-window render diagnostics frame succeeds with
  a positive platform render size. A focused `runWinUISkikoSample` task now
  runs just those Skiko diagnostics and exits successfully without the full
  sample's known `KWINRT-024` teardown crash. The current upstream diagnostics
  expose render state/result metadata but no pixel-read or surface snapshot API,
  so nonblank frame validation remains blocked on a stable attached-window
  pixel-read path.

## SKIKO-001: skiko-winui artifact coordinates were not obvious

- **Status:** Closed as local integration discovery on 2026-06-02.
- **Observed in:** attempting to replace the current WinUI JVM compile-source
  bridge with a Skiko WinUI dependency for compose-winui.
- **Initial finding:** `org.jetbrains.skiko:skiko-winui` and
  `org.jetbrains.skiko:skiko-winui-runtime-windows-*` do not exist in Maven
  Central. `org.jetbrains.skiko:skiko:0.148.1` also does not publish a WinUI
  variant.
- **Resolution:** the published snapshot is
  `io.github.compose-fluent:skiko-winui:0.0.0-SNAPSHOT`, with runtime artifact
  `io.github.compose-fluent:skiko-winui-windows:0.0.0-SNAPSHOT`, from the
  `compose-fluent/skiko` `winui_dev` branch.
- **Snapshot baseline:** Maven snapshot metadata reports timestamped build
  `0.0.0-20260602.094020-1` for both artifacts. The branch head inspected
  locally was `35bda1c61f856f06e6ee012d8609ae13ce1a5fd7`.
- **Dependency shape:** `skiko-winui` depends on
  `org.jetbrains.skiko:skiko:0.0.0-SNAPSHOT`,
  `io.github.compose-fluent:winrt-runtime-jvm:0.1.0-SNAPSHOT`, and runtime
  `io.github.compose-fluent:skiko-winui-windows:0.0.0-SNAPSHOT`. It does not
  pull `skiko-awt`.
- **Current compose-winui action:** `compose/ui/ui` now depends on
  `skiko-winui` in `winuiMain`, adds `skiko-winui-windows` as `winuiJvmMain`
  runtime, and locks WinUI configurations to the matching Skiko snapshot through
  `composeWinUi.skikoVersion`.
- **Validation:** `:compose:ui:ui:compileKotlinWinuiJvm` passes with
  `-PcomposeWinUi.enableJvmTarget=true --no-configuration-cache
  --no-configure-on-demand`. `runWinUIViewSample` reaches the full current
  smoke path. The known `KWINRT-024` native teardown crash is deferred as
  non-blocking and is not treated as a skiko-winui integration failure.

## SKIKO-002: skiko-winui generic WinRT support is not usable transitively

- **Status:** Fixed upstream in the 2026-06-03 Maven snapshots.
- **Observed in:** `io.github.compose-fluent:skiko-winui:0.0.0-SNAPSHOT`
  while constructing `org.jetbrains.skiko.winui.WinUISkiaLayer` from
  compose-winui.
- **Failure:** Older snapshots let `runWinUIViewSample` start the WinUI
  application, then fail during `WinUISkiaHostPanel` construction with:
  `NoSuchMethodError: androidx.compose.ui.winui.samples.WinUIViewSampleKt.kotlinWinRtGenericTypeInstantiationInitializeBySourceType(String)`.
- **Resolution:** After clearing the Gradle snapshot cache, compose-winui
  resolved `skiko-winui` timestamped build `0.0.0-20260603.023842-2`,
  `winrt-gradle-plugin` `0.1.0-20260603.021843-3`, and
  `winrt-compiler-plugin` `0.1.0-20260603.021535-22`. The sample no longer
  throws the generic-support `NoSuchMethodError` and reaches Compose content.
- **Validation baseline:** `:compose:ui:ui:compileKotlinWinuiJvm` passes with
  `-PcomposeWinUi.enableJvmTarget=true --no-configuration-cache
  --no-configure-on-demand`; `:compose:ui:ui:winui-samples:runWinUIViewSample`
  now fails later in compose-winui's local interop root smoke path, tracked as
  `SKIKO-003`.

## SKIKO-003: compose-winui interop root smoke does not install wrapper after Skiko host hookup

- **Status:** Fixed compose-side on 2026-06-03.
- **Observed in:** `runWinUIViewSample` after `WinUISkikoRenderHost` is backed
  by `io.github.compose-fluent:skiko-winui:0.0.0-SNAPSHOT`.
- **Failure:** The sample enters Compose content and then fails in
  `ComposeWinUiSmokeApp.runWinUIViewLifecycleSmoke` with:
  `IllegalStateException: WinUIView lifecycle smoke did not install a wrapper into the root host.`
- **Evidence:** `rootHost.content` is already the expected interop `Canvas`,
  but `rootCanvas.requiredChildren.singleOrNull()` is null. The previous
  `SKIKO-002` generic-support exception is gone.
- **Resolution:** `WinUIComposeView.updateRootContent` now flushes pending
  root-content transactions even when the interop overlay identity has not
  changed, so the Skiko render base child cannot leave queued WinUI child
  updates unapplied. The sample smoke now treats the Skiko render surface as a
  root `Canvas` base child and validates only interop overlay children when
  checking wrapper installation, ordering, and removal.
- **Validation:** With `JAVA_HOME=C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot`,
  `:compose:ui:ui:compileKotlinWinuiJvm` passes using
  `-PcomposeWinUi.enableJvmTarget=true --no-configuration-cache
  --no-configure-on-demand`. `:compose:ui:ui:winui-samples:runWinUIViewSample`
  reaches the full current smoke path, including `text input session
  cancellation`. The later known `KWINRT-024` native process-exit crash is
  tracked separately as non-blocking.
