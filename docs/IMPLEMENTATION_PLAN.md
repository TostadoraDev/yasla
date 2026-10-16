# Plan de implementación

Fecha: 12 de septiembre de 2026.
Este plan parte de una plantilla Android sin Activity ni código de producción. Las fases son gates: la siguiente no se declara terminada hasta que la anterior compile y cumpla sus pruebas.

## Principios de entrega

- Un recorrido vertical útil por fase; evitar crear todos los módulos/capas vacíos.
- Dominio de letras y tiempo sin Android ni Compose.
- Una fuente de verdad para reproducción/sincronización; UI rápida consume progreso especializado.
- Local-first y offline verificable; red nunca bloquea lyrics locales adecuadas.
- Permisos solicitados cuando una función los necesita, con degradación útil al revocarlos.
- Compatibilidad declarada por evidencia en versiones concretas de players/OEM/launchers.
- No copiar código de AirLyrics, CarLyrics o AMLL sin registrar licencia y procedencia.
- Tests focales antes de expandir UI; build, lint y diagnostics después de cambios relevantes.

## Fase 0 — Base compilable

**Estado: completada el 12 de septiembre de 2026.** Se creó el módulo de dominio, el motor de sincronización, el contrato de providers, la Activity launcher y el shell Compose. Verificación: 9 tests de dominio y 1 test local de app, APK debug, lint con 0 errores/2 avisos y análisis IDE completo de 25 archivos con 0 errores.

Objetivo: una app lanzable con identidad inicial y contratos de dominio suficientes para construir sin reescribir.

Entregables:

- `:core:domain` Kotlin/JVM.
- `LyricsDocument`, `LyricsLine`, `LyricsWord`, capacidades, idiomas, source y timing quality.
- `PlaybackSnapshot`, reloj monotónico abstracto, `SyncState` y primer `LyricsSyncEngine` con `StateFlow`.
- `:app` con MainActivity Compose, Material 3, tema claro/oscuro/dynamic-color preparado y estado vacío honesto.
- Documentos de arquitectura/análisis/plan en `docs/`.
- Tests unitarios de intervalos, word progress, pausa, offset, seek/discontinuidad y cambio de documento.
- Build debug, tests unitarios, lint e IDE diagnostics.

Fuera de alcance: permisos, NLS, MediaController, HTTP, Room, overlay, widget, notificación, editor, reproducción simulada.

Gate: APK instalable, Activity launcher, 0 errores de compilación, tests del dominio verdes. Validación visual queda pendiente si no hay emulador/dispositivo.

## Fase 1 — Formatos y selección de letras

Objetivo: importar archivos y obtener un `LyricsDocument` validado.

- Parser LRC/Enhanced LRC: metadata/offset, múltiples timestamps, word markers, traducciones.
- Parser TTML namespace-aware: seguridad XML, begin/end/dur, herencia de tiempo, spans, idiomas/agentes, extensiones AMLL acotadas.
- Diagnósticos estructurados con línea/columna; no reparar silenciosamente.
- Corpus legal/sintético de Unicode, bidi, sílabas, overlaps, empty/zero duration y malformed/unsafe XML.
- `LyricsProvider`, `LyricsRequest`, `ProviderResult`, scoring y policies, todavía con provider import/memoria.
- SAF import/export; export LRC advierte pérdidas.

Gate: corpus focal, propiedades de monotonicidad/round-trip donde aplique, límite de tamaño/profundidad, fixtures sin contenido con copyright dudoso.

## Fase 2 — Biblioteca local, Room y LRCLIB

Objetivo: consulta local-first y offline real.

- `:core:data`, Room schema de tracks/assets/revisiones/links/preferencias/presets/cache.
- Migraciones versionadas desde schema 1; pruebas de migración y consistencia.
- DataStore para ajustes globales y orden de providers.
- LRCLIB client con User-Agent, timeout, cancelación, rate limit/retry y matching por duración/version.
- Cache policy: servir stale offline, refresh explícito/background, deduplicación, negative cache acotada, pins.
- Pantallas mínimas de búsqueda, resultado, descarga, biblioteca e importación.

Gate: modo avión con letras descargadas/importadas; no request al tener versión local adecuada; process-death y migration tests.

## Fase 3 — Detección y control de reproducción

Objetivo: conectar el motor a reproducción real mediante APIs Android.

- NotificationListenerService como frontera de acceso a active sessions.
- Registry de MediaController callbacks, snapshot mapper y capabilities.
- Arbitraje multi-player con selección manual, prioridad e hysteresis.
- Posición anclada en elapsedRealtime, speed y reported update time; generation/sequence.
- Commands seek/play/pause/next/previous sólo con capability y sesión vigente.
- Onboarding/revocación de Notification Access; sin whitelist obligatoria de apps.
- Matriz de Spotify, YouTube Music, Apple Music, Samsung Music, Poweramp, VLC, cast/BT y estados incompletos.

Gate: track/play/pause/seek/resume/cambio rápido probados en dispositivo; no presentar estimación como tiempo exacto; degradación sin permisos.

## Fase 4 — Now Playing y renderer karaoke

Objetivo: experiencia principal usable.

- Artwork/metadata/progress y LazyColumn estable; auto-follow centrado por evento.
- Renderer word fill continuo con ranges, graphemes, ligaduras, wraps y bidi.
- Gesto manual pausa auto-scroll; acción para volver; tap-to-seek; long press seguro.
- PaletteEngine y cache por artwork hash; contraste y transiciones.
- Motion OFF/REDUCED/NORMAL/ENHANCED, ajuste del sistema y tests de accesibilidad.
- Estados loading/not found/offline/permission/player unsupported.

Gate: perfil macrobenchmark/recomposition, 60/90/120 Hz en hardware, font scale 1.3+, TalkBack, RTL, dark/light, rotación/tablet.

## Fase 5 — Floating Lyrics y notificación

Objetivo: lyrics fuera de la Activity sin romper restricciones modernas.

- Prototipo de lifecycle background/FGS antes de consolidar arquitectura.
- Overlay WindowManager API 24–25/26+, move/resize, lock y click-through recuperable.
- Modos de layout y `OverlayConfig` versionada con presets.
- Filtro primario por package de la sesión; foreground-app opcional/best effort sólo si se justifica Usage Access.
- Blur feature-detected con fallback; bounds/insets/multi-window.
- Notificación por cambio de línea con show/hide/lock/offset/open; PendingIntents únicos.
- Revisión de Play Console para cualquier foregroundServiceType elegido.

Gate: pruebas API 24/26/31/33/34/35/36/37, revocación de permiso, apps que ocultan overlays, pantalla bloqueada, batería/GPU.

## Fase 6 — Widgets y editor por instancia

Objetivo: widgets principales configurables y honestos con RemoteViews.

- `:feature:widgets`, snapshot persistible y actualizador event-driven.
- Minimal, Two Lines y Karaoke Card primero; luego Transparent, Large, Compact Player y Word Focus.
- Entity por appWidgetId, presets clonados, resize/delete/restore/remapping.
- Config Activity + preview Compose y preview RemoteViews real.
- Capability matrix: fuentes del sistema, drawables/imágenes acotadas, sin blur garantizado.
- Coalescing: default por línea, máximo de producto inicialmente 1 update/s, medido y ajustado.
- Límite de bitmap/icon de target 37; pruebas de launchers y proceso muerto.

Gate: Pixel Launcher + al menos un launcher OEM, tamaños compact/expanded, RTL/font scale, batería; sin loop ni 60 fps.

## Fase 7 — Editor avanzado

Objetivo: editar sin corromper la biblioteca ni acoplar UI al motor.

- Borradores y comandos revisionados, undo/redo.
- Texto, global/line offset, timestamps de línea/palabra, traducción/romanización y preview.
- Sincronización manual sobre reloj real.
- Import/export TTML del perfil soportado y avisos de pérdida.
- Conflictos: nunca sobrescribir importación/edición con refresh remoto.

Gate: recuperación de borrador, validación antes de commit, revisiones restaurables, property tests y archivos grandes.

## Fase 8 — Endurecimiento y release

- Métricas locales opt-in o profiling manual para cache hit, lookup latency, drift, jank y widget frequency; sin registrar letras.
- Macrobenchmark, Baseline Profile, leak/process-death/battery tests.
- R8/optimización release, reglas backup/data extraction, export/import de biblioteca.
- Auditoría de permisos, XML/URI/HTTP/logging, dependency licenses y privacy.
- `LICENSE` propio, `THIRD_PARTY_NOTICES.md`, SBOM/OSS notices y procedencia de cualquier código adaptado.
- Accessibility/i18n, screenshots de teléfono/tablet, store policy y pruebas de upgrade.

Gate: release reproducible firmado por flujo seguro, sin keystores en repo, no blockers conocidos y límites documentados.

## Backlog explícitamente posterior

Android Auto/Automotive, providers adicionales, compartir comunidad, traducción automática, sincronización cloud y plugins de terceros. No deben ensanchar las primeras fases.

## Riesgos principales y respuesta

| Riesgo | Impacto | Gate de reducción |
|---|---|---|
| Calidad desigual de MediaSession | Sync/control incorrectos por player | Fase 3: capability + quality + matriz real |
| Background/FGS/Play policy | Overlay puede no sobrevivir o ser rechazado | Prototipo policy/hardware antes de Fase 5 |
| TTML no es un único perfil | Parseo incorrecto/pérdida de datos | Perfil explícito, extensiones, corpus y diagnostics |
| Word fill con bidi/ligaduras | Resaltado visual incorrecto | Text layout/ranges y pruebas multiescritura |
| Widgets limitados por RemoteViews | WYSIWYG falso o consumo alto | Capability matrix, real-host preview, eventos |
| Matching de track | Letra de versión equivocada | Candidatos/score/duración y selección manual |
| Derechos de letras | Riesgo legal/distribución | Términos por provider, provenance y retención |
| Migraciones/editor | Pérdida de trabajo del usuario | Revisiones, backups, migration/round-trip tests |
| Scope demasiado grande | Producto nunca usable | Vertical slices y gates medibles |

## Comandos mínimos de verificación

```powershell
.\gradlew.bat :core:domain:test :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
```

Después: `ide_sync_files`, `ide_project_diagnostics`, `ide_list_tests` y `ide_build_project`. Tests instrumentados/visuales requieren emulador o dispositivo y se reportan por separado.
