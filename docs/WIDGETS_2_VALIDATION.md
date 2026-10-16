# Widgets 2.0 and bilingual interface validation

Work performed locally on 2026-10-12–13. The Now Playing AMLL renderer and procedural background were preserved.

## Idle image and states

The configurator uses Android Photo Picker. Selected images are decoded with a bounded target,
resized to at most 512 pixels on the longest side, copied into the app's private `noBackupFilesDir`,
and recorded in per-widget Glance preferences. The app never sends the picture to a server.
No temporary gallery URI is needed after import. The copied file is excluded from Android cloud backup.
Cover/Fit, the YASLA default vector image, replacement and removal are available. Old copies are
removed after a successful save; deleting a widget also removes its owned image.

`NO_PLAYBACK` displays only the configured image. `SEARCHING_LYRICS`, `LYRICS_AVAILABLE`,
`LYRICS_UNAVAILABLE` and `ERROR` are distinct. Pausing a selected song does not turn it into an
idle image. An active song waiting for lyrics keeps its loading state. App Widgets replace the
RemoteViews content atomically; there is no unsupported per-frame image/text crossfade.

## Balanced words and the actual renderer defect

There were two separate causes: the old fitter accepted a syllable partition before considering
better cuts, and launcher TextViews then wrapped already-fitted fragments again using different
font metrics. Actual device captures showed `QUIER/E` and isolated letters in long words even
when text-content tests passed.

The fitter now measures candidate fragments, uses Unicode extended grapheme boundaries, checks
width and height, prefers a complete single line with moderate shrinking, then searches balanced
multi-line partitions. It minimizes squared normalized slack, strongly penalizes one-grapheme
orphans and uses linguistic cuts only as a small preference. It adds no hyphens or ellipsis.
[Android Pattern documentation](https://developer.android.com/reference/java/util/regex/Pattern.html)
describes the extended-grapheme matcher used here.

The final glyphs are drawn using the same Paint/StaticLayout that measured them, into a bounded,
transparent raster of at most 512×512 pixels (1 MiB ARGB). Glance renders that glyph image with the
complete word as its accessibility description. The launcher cannot introduce another text wrap
or ellipsis. The preview uses this same word renderer. Rasters update only on semantic word,
configuration or size changes, not on timed segments or frame ticks.

Actual captures confirm whole `QUIERE` when it fits and `QUI/ERE` in the forced small rectangle.
All requested Spanish/English words were rendered at 96×88, 180×128 and 300×180 dp. The corner
alpha is zero. `LyricsWord` remains the visible unit; AMLL segments retain their timing role.
No parser or source lyric text was uppercased or rewritten.

## Shared live preview

`LyricsAppContainer.publish()` exposes the same immutable snapshot that it queues to the widget
publisher. The standalone configurator subscribes to that flow. The in-app widget preview consumes
the identical projection through app state. Both update with track, word/line, mode and appearance.
The fixed CONTIGO demo was removed from production. Idle previews display the configured image.
No new MediaSession detector, clock or production polling loop was introduced.

## English and Spanish

English is the base Android resource language; Spanish lives in `values-es`. Navigation labels,
Now Playing interface messages, Library, Search, Studio/editor, Settings, widget modes/configuration,
overlay interface labels, permissions, dialogs, notification channels/actions and accessibility
labels use resources. Format arguments and plural resources are checked for parity.

Settings offers System Default, Español and English. Android 13+ uses LocaleManager and respects
system App Languages as the source of truth. Android 7–12 uses persisted selection and localized
Configuration contexts. Language changes refresh existing widgets, service/notification strings,
library captions and current error messages. App Bundles package both languages together, allowing
offline switching without a new Play Core dependency. Song titles, artists, albums, metadata and
lyric content are not translated.

## Verification

- 190 app unit tests passed, with no failures or errors.
- 72 domain tests passed in a fresh forced run, including parsing and word reconstruction.
- Actual Glance RemoteViews captures tested full words, balanced cuts, three sizes, transparency,
  lack of ellipsis, idle/loading swaps and the private bounded photo copy.
- Spanish/English fixture screens, word/mode/state preview updates and seek regressions were exercised.
- Real-session preview validation compares against the current shared snapshot and the user's title
  fallback, including state changes/gaps; it waits for asynchronous observer startup.
- Android Studio MCP analyzed 13 relevant production files: complete coverage, zero errors.
- IDE build succeeded. The MCP test runner could not create a run configuration for the new Kotlin
  test class, so unit tests ran through Gradle and device tests through Android instrumentation.
- Debug APK, unsigned release APK and release App Bundle built successfully. Lint: zero errors,
  50 warnings (including existing warnings and platform/compatibility recommendations).
- Ten protected AMLL/background source files retained their exact SHA-256 hashes.

- Final complete physical-device suite: **11/11 passed in 41.266 seconds**, with no skipped cases. The real-session case also passed independently. The test harness now waits for observer/layout readiness; the phone display was awake. AOD/dozing had prevented backing surfaces in earlier capture attempts.

Final device run details are retained in the ignored `.artifacts/widgets2-20261012` directory.

## Validation boundaries

The physical device was a Samsung SM-S948U1 on API 37. API 24–32 compatibility was compiled and
lint-checked but not exercised on an old device. The bounded image import/storage path was tested
with a local URI, including deletion of the original and reopening the private copy; the system
Photo Picker gallery selection UI was not driven automatically. The phone was not rebooted.
These checks are not a sustained 60/120 Hz performance benchmark or a test of every launcher.
The three widget reference screenshots mentioned in the brief were not attached to that message;
visual verification used its explicit word/centering/transparency requirements.

## License and repository

The maintainer selected GPL-3.0 for original YASLA code. The unmodified GNU text is in [LICENSE](../LICENSE).
Project documents now link [TostadoraDev/yasla](https://github.com/TostadoraDev/yasla).
The earlier source-guided AMLL motion provenance remains separately documented in
[LICENSE_STATUS.md](LICENSE_STATUS.md); this choice does not remove upstream AGPL obligations.

## Actual glyph captures

These isolated RemoteViews captures use synthetic test words, with transparent backgrounds.

![Balanced QUIERE](screenshots/widget-balanced-word.png)
![Complete long word](screenshots/widget-long-word.png)

## Changed files

- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/LyricsAppContainer.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/LyricsApplication.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/MainActivity.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/data/importing/LyricsImportReader.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/localization/AppLanguageManager.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/notification/LyricsNotificationCoordinator.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/notification/LyricsSurfacePublisher.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/overlay/LyricsOverlayController.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/overlay/LyricsOverlayService.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/AdvancedLyricsApp.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/LyricsApp.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/LyricsUiModels.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/components/SongArtwork.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/screens/NowPlayingScreen.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/screens/LibrarySearchScreens.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/screens/SettingsScreen.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/screens/StudioScreens.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/widget/LyricsWidget.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/widget/LyricsWidgetState.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/widget/LyricsWidgetUpdater.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/widget/WidgetConfigurationActivity.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/widget/WidgetConfigurationEditor.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/widget/WidgetIdleImageStore.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/widget/WidgetModels.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/widget/WidgetWordBitmap.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/widget/WordLineBreaker.kt`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/drawable/widget_idle_default.xml`
- `app/src/main/res/xml/locales_config.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/studio_strings.xml`
- `app/src/main/res/values/system_strings.xml`
- `app/src/main/res/values/ui_strings.xml`
- `app/src/main/res/values/widget_strings.xml`
- `app/src/main/res/values-es/strings.xml`
- `app/src/main/res/values-es/studio_strings.xml`
- `app/src/main/res/values-es/system_strings.xml`
- `app/src/main/res/values-es/ui_strings.xml`
- `app/src/main/res/values-es/widget_strings.xml`
- `app/src/debug/AndroidManifest.xml`
- `app/src/debug/java/xyz/tosdev/yetasimplelyicsapp/RefinementPreviewActivity.kt`
- `app/src/debug/java/xyz/tosdev/yetasimplelyicsapp/Widgets2HostActivity.kt`
- `app/src/androidTest/java/xyz/tosdev/yetasimplelyicsapp/RefinementVisualTest.kt`
- `app/src/androidTest/java/xyz/tosdev/yetasimplelyicsapp/Widgets2InstrumentedTest.kt`
- `app/src/androidTest/java/xyz/tosdev/yetasimplelyicsapp/Widgets2PreviewTest.kt`
- `app/src/test/java/xyz/tosdev/yetasimplelyicsapp/LocalizationResourcesTest.kt`
- `app/src/test/java/xyz/tosdev/yetasimplelyicsapp/localization/LanguageSelectionTest.kt`
- `app/src/test/java/xyz/tosdev/yetasimplelyicsapp/widget/BalancedWordWrappingTest.kt`
- `app/src/test/java/xyz/tosdev/yetasimplelyicsapp/widget/LyricsWidgetModelsTest.kt`
- `app/src/test/java/xyz/tosdev/yetasimplelyicsapp/widget/Widgets2StateTest.kt`
- `LICENSE`
- `README.md`
- `CONTRIBUTING.md`
- `THIRD_PARTY_NOTICES.md`
- `docs/LICENSE_STATUS.md`
- `docs/WIDGETS_2_VALIDATION.md`
- `docs/screenshots/widget-balanced-word.png`
- `docs/screenshots/widget-long-word.png`
