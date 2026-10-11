# Releasing Compose WinUI

The release identity is recorded in `compose-winui-release.properties`.
Each library family uses its own `major.minor.0-alphaNN` version, with separate
CMP and Jetpack base versions. The aggregate release/tag uses the Compose version.

`compose-winui-modules.json` records the publication policy:

- `winui`: build JVM and MinGW implementations, redirect other targets to CMP.
- `mingw`: build only MinGW, redirect other targets to CMP.
- `jetpack`: redirect directly to Jetpack. If its published metadata lacks MinGW,
  build the missing MinGW implementation and redirect the remaining targets.

JVM libraries that depend on AWT/Swing or require the WinUI replacement of Desktop
implementations belong in `winui`. The release validator inspects all local JVM
binaries; the independent consumer additionally checks the resolved runtime graph.

Preparation downloads the exact official module metadata and records its URL and
SHA-256 in `upstream-redirects.json`. Native/JVM redirects use the actual published
target coordinates; no target names or support are inferred from version numbers.
Other platform binaries are neither built nor uploaded by this fork.

## Local validation

Use JDK 25, Kotlin 2.4.0, Windows SDK 10.0.26100.0, MSVC and NuGet. The Gradle build
uses stable kotlin-winrt 0.1.0 and Skiko WinUI 0.150.1-alpha02 from Maven Central.
Release mode excludes Maven Local and `COMPOSE_WINUI_MAVEN_REPO` overrides.

```powershell
python -B -m unittest discover -s development -p test_compose_winui_release.py
python development/compose_winui_release.py verify
python development/compose_winui_release.py prepare
.\gradlew.bat stageComposeWinUiRelease '-PcomposeWinUi.release=true' '-PcomposeWinUi.enableJvmTarget=true' '-PcomposeWinUi.enableMingwTarget=true' '-Pandroidx.enabled.kmp.target.platforms=-mac,-linux,-android_native,-js,-wasm' --no-configuration-cache
python development/compose_winui_release.py bundle
.\gradlew.bat -p development/compose-winui-consumer compileKotlinWinuiJvm compileKotlinWinuiMingw verifyWinUiJvmClasspath verifyUpstreamRedirects --no-configuration-cache
```

The staged Maven repository and rehearsal bundle live under
`out/compose-multiplatform-core/build/compose-winui-release/`. An unsigned bundle
is explicitly nonpublishable. When changing the publication policy, start with
an empty generated repository so removed targets cannot leak into the bundle.

## CI and publication

The `Publish Compose WinUI` workflow validates the integration branch before
release. A matching `compose-winui/v<release.version>` tag builds and validates
the artifacts, then signs them and uploads a single bundle to Central Portal.
GitHub Release is created only after Central reports `PUBLISHED`. Alpha releases
are marked as GitHub prereleases, and use release notes from `docs/releases/`.

The workflow uses existing organization secrets:

- `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD`
- `MAVEN_GPG_KEY_ARMOR`, `MAVEN_GPG_KEY_ID`, `MAVEN_GPG_KEY_PASSWORD`

Signing verifies the pinned upstream commit and matching release tag, and rejects
tracked source changes. The bundle validates POM identities, source/documentation
artifacts, local dependency closure, variant redirects, and artifact checksums.
The manifest records every artifact, upstream mapping and the final fork commit.

An accepted Central deployment is recorded in `central-deployment.json`. To resume
a pending deployment, preserve the signed bundle, manifest and receipt, then run
`python development/compose_winui_release.py upload --stage <release-directory>`.
The uploader reuses the recorded deployment rather than uploading another copy.
