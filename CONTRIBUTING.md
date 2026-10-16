# Contributing to YASLA

Original YASLA code and contributions use [GPL-3.0](LICENSE). Keep third-party notices intact.
The source-guided AMLL motion provenance remains under review; see [license status](docs/LICENSE_STATUS.md).

## Development

Follow [BUILDING.md](docs/BUILDING.md). Use Kotlin/Compose and existing design tokens;
do not introduce dependencies for functionality already covered by the project.
Keep changes small and preserve unrelated local work.

For bug fixes, identify the responsible layer and add meaningful regression coverage.
Timed segments are temporal units; a LyricsWord remains the semantic visual unit.
Keep a single playback timeline. Do not rewrite widgets, overlays or the background
as a side effect of a Now Playing change.

## Validation

Run relevant unit tests, lint and a debug build. For visual changes, compare actual
captures, check dark/light themes, Off/Reduced motion, large fonts and insets.
Describe untested device or provider behavior plainly. Do not report compilation as
proof of visual correctness.

## Pull requests

Explain the concrete problem, final behavior, tests and remaining limitations.
Use demo data in screenshots and remove account details, real tokens and private paths.
Do not add copyrighted song lyrics or artwork without suitable permission.

Commit messages should lead with why the change was needed. Add decision trailers
such as `Constraint:`, `Rejected:`, `Tested:` and `Not-tested:` when useful.

## Reporting

Use the prepared issue templates in [TostadoraDev/yasla](https://github.com/TostadoraDev/yasla). Security concerns
belong in a private channel; see [SECURITY.md](SECURITY.md).
Be respectful and follow [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
