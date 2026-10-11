# Compose WinUI 1.12.0-alpha01

This fork uses `major.minor.0-alphaNN` for its own alpha releases. The fork
version is not the upstream CMP version. Each library family follows its own
CMP version line; Jetpack versions are listed separately below.

## Fork and upstream version mapping

| Library family | WinUI fork version | CMP base | CMP's Jetpack base |
| --- | --- | --- | --- |
| Runtime | 1.12.0-alpha01 | 1.12.1 | Runtime 1.12.1 |
| UI | 1.12.0-alpha01 | 1.12.1 | UI 1.12.1 |
| Foundation | 1.12.0-alpha01 | 1.12.1 | Foundation 1.12.1 |
| Material | 1.12.0-alpha01 | 1.12.1 | Material 1.12.1 |
| Material3 | 1.12.0-alpha01 | 1.12.0-alpha03 | Material3 1.5.0-alpha22 |
| Material3 Adaptive | 1.3.0-alpha01 | 1.3.0-rc01 | Material3 Adaptive 1.3.0 |
| Lifecycle | 2.11.0-alpha01 | 2.11.0 | Lifecycle 2.11.0 |
| Navigation 2 | 2.10.0-alpha01 | 2.10.0-beta01 | Navigation 2.10.0 |
| Navigation 3 | 1.1.0-alpha01 | 1.1.2 | Navigation 3 1.1.7 |
| Navigation Event | 1.1.0-alpha01 | 1.1.0 | Navigation Event 1.1.1 |
| Savedstate | 1.4.0-alpha01 | 1.4.0 | Savedstate 1.4.0 |
| WindowManager Core | 1.5.0-alpha01 | 1.5.1 | WindowManager 1.5.1 |

For example, Navigation 3 `1.1.0-alpha01` forks CMP Navigation 3 `1.1.2`,
whose Jetpack base is `1.1.7`. It does not fork a CMP `1.1.7` release.
This table specifies version identity; the publication manifest must identify
which individual modules and variants are shipped, including upstream redirects.

## Source provenance

- Source baseline: [CMP v1.12.1](https://github.com/JetBrains/compose-multiplatform-core/tree/v1.12.1),
  commit `a1a7f3533363aa93849541cf451af2363fc2070a`.
- WinUI changes ported from `winui_dev` commit
  `7ba45b1468d7688d050f9ea2256848383a4ea7f4` onto that baseline.
  Later CMP 1.13 upstream changes are not the base of this release.
- Rendering dependency: [Skiko WinUI 0.150.1-alpha1](https://github.com/compose-fluent/skiko/releases/tag/skiko-winui/v0.150.1-alpha1),
  based on upstream Skiko `0.150.1`, commit
  `3956e988e6e93eaf1ee985d725049443e7845807`.
  The exact published dependency suffix is `alpha1`, not `alpha01`.
- Windows toolkit: stable kotlin-winrt `0.1.0`.

## WinUI support

The port includes JVM and MinGW WinUI backends, Compose UI, Foundation,
Material, Material3 and Adaptive integration, and Navigation 2 / Navigation 3
adaptations. The released Skiko dependency includes the accessibility peer
and pattern-provider reuse fix.

Only required JVM and MinGW implementations are built and published by this
fork. Other supported targets remain available through metadata redirects to
the matching CMP release. Modules that CMP fully redirects to Jetpack are
redirected directly to Jetpack here too when Jetpack provides MinGW. When it
does not, this fork supplies only the missing MinGW implementation. JVM modules
whose Desktop implementation uses AWT/Swing, or relies on a Desktop implementation
that must be replaced for WinUI, publish a WinUI JVM implementation as well.

Modules already implemented by Jetpack, including Runtime, Lifecycle,
Savedstate, Navigation Event, and Window Core, retain versioned redirects to
their upstream implementation where applicable. A fork coordinate does not
imply that its implementation has been rebuilt. UI, Foundation, Material,
and Navigation WinUI variants carry the fork's platform implementations.

Build/runtime tooling uses Kotlin 2.4.0 and JDK 25 for the Windows toolkit.
These tooling versions are separate from the CMP and Skiko source baselines.

## Release preparation status

This file is the release-note draft. Publication and consumer validation are
pending; no successful Maven Central publication is claimed by this draft.
