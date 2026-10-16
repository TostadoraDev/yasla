# License status and AMLL provenance

**Maintainer decision: GPL-3.0 for original YASLA code (2026-10-12).**
The full, unmodified GPLv3 text is in [LICENSE](../LICENSE), obtained from GNU.
Repository: [TostadoraDev/yasla](https://github.com/TostadoraDev/yasla).
This choice does not relicense third-party code or grant permission to remove AGPL terms.
The source-guided AMLL motion review below remains unresolved for distribution.
This engineering review identifies concrete provenance risks; it is not a final
legal determination. Preparing files locally does not authorize public distribution.

## Ordinary dependencies

The direct Android/Kotlin runtime and build dependencies are predominantly Apache-2.0.
JUnit 4 is EPL-1.0 and test-scoped. See [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).
Complete transitive and packaged notices still need review before a release.

## AMLL source access

The previous native motion pass inspected the source map of the existing local
`@applemusic-like-lyrics/core@0.5.2` installation and ran its demo. The package license
is **AGPL-3.0-only**. The inspected source map's SHA-256 is
`A2A5DC3B0B2449FF865998EDC7BCA24BD51D7657176633F3A54CA3A16CD35982`.

The implementation must **not** be described as clean-room. It was developed with
direct access to original code. No AMLL Gradle/npm runtime dependency or original
AMLL files were bundled in the APK, but dependency absence does not resolve derivation.

## Concrete adaptation risk

The native motion code reproduces a substantial constellation of source mechanisms:

- `AmllLyricsMotion.kt`: analytical spring branches and coefficient arrangement;
  interval-to-stiffness mapping with clamp 100–800, stiffness 170–220, exponent 0.2
  and damping `2.2 * sqrt(k)`; 32-sample character emphasis with matching Bézier
  curves, minimum duration, scaling expressions and multiple shared constants.
- `AmllVisualTimeline.kt`: the same overlap heuristics, 600/400 ms visual advance
  windows and percentage fallback.
- `AmllRowMotion.kt`: scale/opacity/blur coordination, spring parameters and mask
  alpha attack/release behavior.
- `AmllKaraokeText.kt`: half-line-height feather and first/last reveal additions.
- `AmllLyricsViewport.kt`: the source-derived row cascade and focus adaptation.

The latest glyph-only glow and pending-ink contrast are additional native visual
work. They do not erase the earlier source-guided implementation's provenance.

Individual formulas or parameters may be ideas or methods rather than protectable
expression. However, their extensive matching selection and arrangement plus
documented source access create **substantial adaptation risk**. Changing the language
to Kotlin is not, by itself, proof of independence. The
[GNU translation FAQ](https://www.gnu.org/licenses/gpl-faq.html#TranslateCode)
explicitly treats translation into another programming language as modification for
GPL purposes; that is authoritative licensor guidance, not a court ruling on YASLA.

## Potential obligations if the work is covered

The [AGPLv3 text](https://www.gnu.org/licenses/agpl-3.0-body.html) controls:

- Section 4: preserve notices and provide the license for verbatim source copies.
- Section 5: mark modifications and license a conveyed work based on the Program
  under the AGPL as required by its terms, including the work-as-a-whole condition.
- Section 6: APK/object-code distribution needs corresponding source through an
  allowed distribution mechanism.
- Section 13: a modified network-interaction scenario can add a source-offer
  obligation. That is fact-specific and is not the sole trigger for APK distribution.

`AGPL-3.0-only` does not provide an automatic “or later” choice.

## Decisions required before publication

The maintainer must choose a reviewed path, without silently treating the code as
permissively licensed:

1. Proceed on the assumption the implementation is covered and satisfy the applicable
   AGPL obligations with an approved compatible project license and notices.
2. Obtain a separate suitable permission/license from the relevant rightsholders.
3. Replace or quarantine the source-guided implementation through a documented
   independence/clean-room process and review the result.

GPL-3.0 was selected by the maintainer and LICENSE was added. No renderer was removed
or rewritten as a licensing workaround. Whether the source-guided AMLL portion requires
AGPL distribution, additional permission, or replacement remains a separate decision.

Also complete transitive notices and asset-rights review; define security/conduct
contacts, release signing and distribution; review the staged tree and any Git
history for secrets. The root personal TTML import, local APKs, recordings and build
caches are excluded from publication candidates. Existing research notes are
historical evidence, not proof that all current code is independent of AMLL.
