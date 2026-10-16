# Security

YASLA has not established a public release-support policy or private reporting
address yet. Both must be configured before publication.

## Reporting a vulnerability

Do not post security vulnerabilities, credentials or personal media/session data
in public issues. Use an existing private contact with the maintainer while the
project is local. Before public launch, enable GitHub private vulnerability reporting
or publish a monitored private security contact here.

Include the affected build, Android version, reproduction steps, impact and a minimal
sanitized example. Avoid real tokens, private lyrics, account identifiers and keystores.

## Configuration boundaries

- Keep SDK paths in ignored `local.properties`.
- Keep signing material and passwords outside the repository.
- No Spotify Web API/App Remote integration is currently implemented.
- A future mobile OAuth flow must not ship a client secret in the APK.
- Imported lyrics, artwork and database files should not be attached to reports unless
  their privacy and redistribution rights have been checked.

The publication audit checks the intended source tree for common credential patterns
and private paths. It is not a substitute for a full security review or Git-history scan.
