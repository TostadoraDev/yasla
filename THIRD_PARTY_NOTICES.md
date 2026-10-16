# Third-party dependency inventory

This is a verified **direct-dependency inventory**, not yet a complete distribution
notice bundle or transitive SBOM. Original YASLA code uses GPL-3.0. Review
[LICENSE_STATUS.md](docs/LICENSE_STATUS.md) before public source or APK distribution.

Versions were checked against the catalog, resolved build metadata and available
cached POM/JAR license entries on 2026-10-12.

| Component | Declared/resolved version | Scope | License |
| --- | --- | --- | --- |
| AndroidX Core KTX / SplashScreen | 1.19.0 / 1.0.1 | Runtime | Apache-2.0 |
| AndroidX Activity Compose | 1.13.0 | Runtime | Apache-2.0 |
| AndroidX Lifecycle Compose | 2.11.0 | Runtime | Apache-2.0 |
| AndroidX Navigation Compose | 2.10.1 | Runtime | Apache-2.0 |
| Compose BOM | 2026.09.00 | Version metadata | Apache-2.0 |
| Compose Material 3 | 1.4.0 resolved | Runtime | Apache-2.0 |
| Compose extended icons | 1.7.8 resolved | Runtime | Apache-2.0 |
| Compose tooling preview / tooling | 1.12.1 resolved | Runtime / Debug | Apache-2.0 |
| Kotlin Coroutines | 1.11.0 | Runtime / Test | Apache-2.0 |
| Room runtime / KTX / compiler / testing | 2.8.5 | Runtime / Build / Test | Apache-2.0 |
| DataStore Preferences | 1.2.1 | Runtime | Apache-2.0 |
| Glance AppWidget / Material 3 | 1.2.0 | Runtime | Apache-2.0 |
| AndroidX Palette KTX | 1.0.0 | Runtime | Apache-2.0 |
| AndroidX Test JUnit / Espresso | 1.3.0 / 3.7.0 | Test | Apache-2.0 |
| JUnit 4 | 4.13.2 | Test | EPL-1.0 |
| Kotlin / Compose compiler plugin | 2.2.10 | Build | Apache-2.0 |
| Android Gradle Plugin | 9.4.0 | Build | Apache-2.0 |
| Kotlin Symbol Processing | 2.3.10 | Build | Apache-2.0 |
| Gradle wrapper | 9.6.0 | Build | Apache-2.0 |
| Foojay toolchain resolver | 1.0.0 | Build | Apache-2.0 |

`core:domain` is original first-party code covered by the selected GPL-3.0 project license.
Build/debug/test components do not necessarily enter the release APK.
The BOM selects versions; it is not a shipped renderer.

Most licenses were verified in retained POM metadata. SplashScreen, Palette, Espresso
and AndroidX JUnit were verified against the official AndroidX source license because
their POMs were not retained locally. Foojay's cached POM has an empty license entry;
its upstream repository declares Apache-2.0. JUnit's JAR includes its EPL-1.0 license.

## Primary license sources

- [AndroidX license](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt)
- [Kotlin licensing](https://github.com/JetBrains/kotlin/blob/master/license/README.md)
- [Coroutines license](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt)
- [KSP](https://github.com/google/ksp)
- [Gradle license](https://github.com/gradle/gradle/blob/master/LICENSE)
- [Foojay toolchains](https://github.com/gradle/foojay-toolchains)
- [JUnit 4 license](https://github.com/junit-team/junit4/blob/main/LICENSE-junit.txt)
- [Apache-2.0 text](https://www.apache.org/licenses/LICENSE-2.0.txt)

## Source-guided motion reference

`@applemusic-like-lyrics/core@0.5.2` was studied outside this Android tree. Its package
declares **AGPL-3.0-only**. No AMLL package, WebView or production JavaScript dependency
was added. That fact does **not** establish that the Kotlin motion code is independent:
see [the adaptation evidence and publication blockers](docs/LICENSE_STATUS.md).

Credit: AMLL contributors, [upstream project and license](https://github.com/amll-dev/applemusic-like-lyrics/blob/main/LICENSE).

## Content and remaining work

LRCLIB is a network lyric provider, not a bundled Gradle dependency. Lyric and artwork
rights are separate from these software licenses. Synthetic debug fixtures and
screenshots should remain distinguishable from real music content.

Before publication, generate the complete resolved transitive inventory, inspect
packaged AAR/JAR copyright and NOTICE files, retain required license texts, and review
assets and provider-derived content. This direct inventory alone is not a clearance
certificate or the final notice bundle for an APK.
