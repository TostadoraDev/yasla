# YASLA refinement: changes and validation

## Six completed areas

1. Bottom navigation: removed its Liquid Glass container and selected pill. Kept all
   five tabs, shared press/color motion and system insets, with a small moving underline.
2. Karaoke glow: added a bounded text-only pass on the measured lit glyph region,
   with a maximum 2.5 dp shadow radius. No full-screen blur or container gradient.
   Off/Reduced modes disable this glow; original timestamps and motion curves remain.
3. Pending ink: its current-row alpha is 55% of the former pending alpha, interpolated
   across the existing feather. Completed ink retains full active brightness. The
   line-synced branch remains uniform and does not gain word timing.
4. Seek/offset: a 4 dp resting rounded glass track grows to 5.5 dp during drag;
   the 6 dp thumb grows to 9 dp. The 48 dp touch region is preserved. A local scrub
   position owns the UI during drag; only release commits seek. Elapsed/duration stay
   aligned. Separate ±25 ms lyric-offset controls and a quiet current value replace
   the former ±250 presentation.
5. Widget configuration: reused Signal surfaces, icon buttons, typography, spacing,
   radii and motion. It follows the app theme/artwork palette, has a sticky live example,
   grouped choices, labeled sliders and whole-row switches. All existing modes, presets,
   colors, toggles and ranges are preserved. Current Word uses complete, centered
   example text and a transparent inner background. No synchronization/renderer/storage
   implementation was replaced.
6. Open-source preparation: English README with real labeled demo captures; build and
   contribution guidance, conduct/security policies, issue/PR templates, dependency
   credits, license-risk assessment, private-file exclusions and a publication scan.
   No repository, release, push or visibility change was performed; no final license
   was assigned.

## Production files

- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/designsystem/SignalComponents.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/components/AmllKaraokeText.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/components/AmllKaraokeLighting.kt` (new)
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/screens/NowPlayingScreen.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/screens/StudioScreens.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/widget/WidgetConfigurationActivity.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/widget/WidgetConfigurationEditor.kt` (new)

Validation additions: `KaraokeLightingTest.kt`, debug-only `RefinementPreviewActivity.kt`
and its manifest entry, and `RefinementVisualTest.kt`. The fixture uses mock media actions
and synthetic lyrics, not real playback commands. It is excluded from release builds.

## Evidence

- Android Studio MCP: builds succeeded and fresh file diagnostics found no errors.
- App unit tests: **180 passed, 0 failures/errors**.
- Domain report: **72 passed**, unchanged source/task retained; not presented as a fresh
  domain rerun when Gradle reports it up to date.
- Debug, local unsigned release and Android-test APKs: **BUILD SUCCESSFUL**.
- Lint: **0 errors, 39 warnings**; warnings remain documented rather than hidden.
- Physical-device refinement instrumentation: **4 tests passed, 16.433 seconds**.
  Tested finger ownership despite a playback-position update, a single seek commit,
  independent offset, saving all loaded widget fields, dark/light UI, and complete
  Current Word text. The harness reads the actual Compose semantics tree and dispatches
  MotionEvents to the preview hierarchy without new testing dependencies.
- Physical-device continuous-motion instrumentation: **1 test passed, 39.376 seconds**,
  including 20 seconds without interruption, measured samples, pause, seek, wrapping,
  future-row overdraw checks and RTL.
- Two existing widget instances accepted updates; their saved configurations remained
  equal before/after. This is an update/persistence smoke check, not every launcher size
  or screen-off scenario.
- SHA-256 comparison: **38 protected source files unchanged**, including procedural
  background, playback/sync ownership, parser, media, overlay and widget render/update
  implementation.
- Publication-candidate scan: no matches for configured credential/private-path rules.
  Generated output, local configuration and the personal TTML import are excluded.

Actual captures are in [screenshots](screenshots/README.md). Local detailed test logs,
captures and source snapshots are kept under ignored `.artifacts/refinement-20261012/`.

Visual iteration found that the first Current Word preview incorrectly fell back to
a line because the synthetic snapshot lacked word/line IDs. The IDs were added and
an explicit full-word assertion plus a new capture verified the correction.
External accessibility-window automation was unreliable; the final passing harness
uses Compose's existing test-root API. Emulator/System UI failures were not counted
as passing product evidence.

No sustained physical 60/120 Hz benchmark or exhaustive launcher/vendor certification
is claimed. The final license, AMLL provenance decision, transitive notice bundle,
asset rights, reporting contacts and release signing remain publication prerequisites;
see [LICENSE_STATUS.md](LICENSE_STATUS.md).
