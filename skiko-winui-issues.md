# skiko-winui issues found by compose-winui

This file tracks Skiko / skiko-winui gaps that affect compose-winui. Use stable
`SKIKO-###` ids from compose-winui workaround comments so the workaround can be
removed when the upstream behavior is fixed.

Keep closed issues short. They should state the final outcome and validation
baseline, not every retest attempt.

## Current upstream triage

- **Open upstream/publication:** none.
- **Open compose-side workarounds:** none.
- **Closed/fixed or superseded:** `SKIKO-001`.

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
  smoke path before the known `KWINRT-024` native teardown crash.
