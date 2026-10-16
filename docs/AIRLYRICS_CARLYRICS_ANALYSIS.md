# AirLyrics y CarLyrics: análisis para una implementación nueva

Fecha: 12 de septiembre de 2026. Alcance: snapshots clonados sólo como referencia bajo `work/references`; no se mezclaron sus fuentes con la app.

- AirLyrics: commit [89ebd0edfc6ce93c45db5fc9a1cb62fed48f50c3](https://github.com/AirLyrics/AirLyrics/tree/89ebd0edfc6ce93c45db5fc9a1cb62fed48f50c3), 11 de septiembre de 2026.
- CarLyrics: commit [072ecb0e6fd118df2e64efaa86e3a81fe9024cfa](https://github.com/mliem2k/carlyrics/tree/072ecb0e6fd118df2e64efaa86e3a81fe9024cfa), 2 de julio de 2026.

Las decisiones `REUSE`, `ADAPT`, `REWRITE` y `DISCARD` describen la relación futura con esos snapshots. En la base implementada no se copia código de ninguno.

## Lectura ejecutiva

AirLyrics es la referencia más madura para overlay, importación TTML/Enhanced LRC, seguridad del parser, control de snapshots antiguos y personalización. Su código actual sigue dividiendo letras plain y word-by-word, usa TextView/Spannable y concentra dos loops de Handler en el foreground service; sirve como evidencia de problemas resueltos, pero nuestro dominio común y nuestro reloj compartido requieren reescritura.

CarLyrics aporta una ruta visible LRCLIB → Room → StateFlow → notificación/Glance y una base Android Auto. Su estructura contiene piezas útiles, pero su seguimiento temporal reinicia canciones en cero, usa `System.currentTimeMillis()` y un loop infinito cada 500 ms, y sus controles mutan estado local sin enviar comandos al reproductor. Esa parte no es base segura para sincronización real. El repositorio también contiene Flutter/backup, zips de herramientas, dos keystores y numerosos scripts de build: no deben copiarse ni usarse como arquitectura.

## Matriz de decisión

| Componente | AirLyrics | CarLyrics | Nuestra implementación |
|---|---|---|---|
| Modelo común de letras | Modelos separados LRC y `WordByWordLine` | `Lyrics` centrado en plain + lista LRC | **REWRITE**: `LyricsDocument/Line/Word`, idiomas múltiples, capacidades y timing quality |
| Parser LRC | Compatible con varias etiquetas y word timing | Parser pequeño usado al importar | **ADAPT**: corpus y casos; parser propio hacia modelo común |
| Parser TTML | SAX seguro, namespace-aware a nivel parser, límites y soporte de traducción/romanización | No TTML encontrado | **ADAPT/REWRITE**: conservar defensas; añadir timing heredado, namespaces por URI, extensiones y diagnósticos |
| Word-by-word | Progreso continuo, Unicode code points y smoothstep | No modelo word timing | **ADAPT** la idea matemática; renderer propio por rangos/graphemes y bidi |
| Renderer de letras | TextView/Spannable con matching heurístico plain↔word | Compose line-only | **REWRITE**: Compose/draw layer sobre un solo documento, sin reconciliar dos fuentes |
| Overlay | WindowManager, servicio, lock/touch-through, estilos y quick controls | No overlay | **ADAPT** lifecycle/config/recuperación; renderer y sync propios |
| Detección media | NLS + sesiones, callbacks, selección por package, sequence gate | NLS con whitelist y heurística de notificación; manager no controla sesiones | **ADAPT** sequence gate/arbitraje de AirLyrics; **DISCARD** whitelist/action-title de CarLyrics |
| Reloj de playback | Estima posición y actualiza overlay con Handler; refresco periódico adicional | Loop infinito 500 ms con wall clock y reset a cero | **REWRITE** con `elapsedRealtime`, anchor real, generation y consumidores |
| LyricsProvider | Interfaz para plain; mapa de provider seleccionado | Repositorio fijo LRCLIB y fallbacks | **ADAPT** extensibilidad; contrato suspendido con capacidades/resultados explícitos |
| LRCLIB | No es su fortaleza principal | Cliente y mapeo plain/synced, cache-first | **ADAPT** endpoints/matching; cliente propio, errores/ratelimit/cancelación explícitos |
| Caché local | Archivos/SAF + SharedPreferences | Room con TTL y búsqueda | **ADAPT** local-first y Room; **REWRITE** schema para revisiones, formatos, idiomas, provenance y pins |
| Biblioteca/import/export | Buen flujo de importación y avisos | Room, búsqueda, import/export LRC | **ADAPT** flujos; modelo/editor revisionado propio |
| Widgets | No Glance | Widget Glance y estado compartido | **ADAPT** actualización por cambio de línea; config por instancia y capability fallbacks propios |
| Notificación | FGS con comandos de overlay | Línea actual desde orchestrator | **ADAPT** contenido/acciones; separar notificación opcional de cualquier FGS obligatorio |
| StateFlow/orquestación | Principalmente broadcasts, stores y service state | Orchestrator con flows | **REWRITE** alrededor de una única fuente de verdad y snapshots persistibles |
| Personalización | Amplia, stores separados | Ajustes más limitados | **ADAPT** opciones de AirLyrics; configuración versionada/presets y DataStore |
| DI | Wiring explícito por capas | Hilt ampliamente aplicado | **DISCARD inicialmente** Hilt; constructor injection + AppContainer hasta que scopes reales lo justifiquen |
| Tests | Cobertura amplia de parser, almacenamiento, media, overlay y políticas | 2 clases de tests (12 métodos observados) | **REUSE/ADAPT** casos conceptuales; tests propios, sin copiar fixtures con derechos dudosos |
| Android Auto | No foco del snapshot | Car App Library y screens | **DEFER**: fuera de la primera experiencia; evaluar tras media/sync estable |
| Rust/JNI providers | Core Rust para proveedores online | Kotlin/HTTP | **DISCARD inicialmente**: coste operacional injustificado para LRCLIB/import local |
| Artefactos y secretos repo | Sin hallazgo equivalente en esta revisión | Keystores, zips SDK/build, múltiples scripts y árbol Flutter backup | **DISCARD**; nunca copiar. Auditar si se contribuye upstream |

## AirLyrics

### Evidencia útil

- [Arquitectura documentada](https://github.com/AirLyrics/AirLyrics/blob/89ebd0edfc6ce93c45db5fc9a1cb62fed48f50c3/docs/ARCHITECTURE.md) separa media, lyrics y floating, tiene protocolos de broadcast tipados y un guard test de strings.
- [TtmlParser.kt](https://github.com/AirLyrics/AirLyrics/blob/89ebd0edfc6ce93c45db5fc9a1cb62fed48f50c3/app/src/main/java/com/andsi/airlyrics/lyrics/parser/TtmlParser.kt#L37) usa SAX, secure processing, bloquea DTD y entidades externas, limita profundidad y ofrece errores con localización.
- El parser exige timing de línea y admite timing de spans, traducciones/romanización y `dur`; su propia [guía](https://github.com/AirLyrics/AirLyrics/blob/89ebd0edfc6ce93c45db5fc9a1cb62fed48f50c3/docs/LYRICS_FORMAT.md) dice que convierte TTML a forma editable y no conserva estilos ni datos multi-voice complejos.
- [WordByWordHighlightProgress.kt](https://github.com/AirLyrics/AirLyrics/blob/89ebd0edfc6ce93c45db5fc9a1cb62fed48f50c3/app/src/main/java/com/andsi/airlyrics/floating/WordByWordHighlightProgress.kt#L20) calcula progreso continuo y evita cortar pares surrogate mediante code points.
- [FloatingLyricsRenderer.kt](https://github.com/AirLyrics/AirLyrics/blob/89ebd0edfc6ce93c45db5fc9a1cb62fed48f50c3/app/src/main/java/com/andsi/airlyrics/floating/FloatingLyricsRenderer.kt#L122) selecciona LRC y después busca una línea word-by-word compatible por texto/ventana temporal; esa reconciliación es innecesaria con un modelo único.
- [FloatingLyricsService.kt](https://github.com/AirLyrics/AirLyrics/blob/89ebd0edfc6ce93c45db5fc9a1cb62fed48f50c3/app/src/main/java/com/andsi/airlyrics/floating/FloatingLyricsService.kt#L100) programa sincronización y refresh de media con callbacks separados.
- [MediaSessionObserver.kt](https://github.com/AirLyrics/AirLyrics/blob/89ebd0edfc6ce93c45db5fc9a1cb62fed48f50c3/app/src/main/java/com/andsi/airlyrics/media/MediaSessionObserver.kt) registra controllers/callbacks; su sequence gate y selección por package son patrones valiosos.
- [LyricsRepository.kt](https://github.com/AirLyrics/AirLyrics/blob/89ebd0edfc6ce93c45db5fc9a1cb62fed48f50c3/app/src/main/java/com/andsi/airlyrics/lyrics/LyricsRepository.kt#L18) sigue local → remoto autorizado → persistencia opcional, pero la interfaz [PlainLyricsProvider](https://github.com/AirLyrics/AirLyrics/blob/89ebd0edfc6ce93c45db5fc9a1cb62fed48f50c3/app/src/main/java/com/andsi/airlyrics/lyrics/PlainLyricsProvider.kt) es síncrona y sólo representa plain.
- Tiene tests focales de parser, almacenamiento SAF, media, overlay, latest-wins y architecture boundaries. Es una buena lista de regresiones a recrear.

### Qué sí tomaría

- Reutilización selectiva futura de ideas y quizás pequeñas utilidades/tests si reduce riesgo y se conserva el aviso MIT.
- Adaptar: seguridad XML, sequence/generation gates, recuperación del overlay, últimos resultados ganan, opciones de estilo y pruebas de lifecycle.
- Reescribir: dominio, provider API, TTML completo del perfil elegido, motor temporal, renderer Compose y persistencia.

### Qué no conservaría

- Dos representaciones paralelas de lyrics y matching heurístico entre ellas.
- Un timer ligado al renderer/servicio como fuente de verdad.
- Arquitectura de providers plain-only.
- JNI/Rust antes de demostrar que una integración concreta lo necesita.
- SharedPreferences dispersas como almacenamiento definitivo de configuración compleja.

## CarLyrics

### Evidencia útil

- [README](https://github.com/mliem2k/carlyrics/blob/072ecb0e6fd118df2e64efaa86e3a81fe9024cfa/README.md) declara LRCLIB, Room, Glance, Compose, Media3, Hilt y Android Auto.
- [LyricsRepositoryImpl.kt](https://github.com/mliem2k/carlyrics/blob/072ecb0e6fd118df2e64efaa86e3a81fe9024cfa/app/src/main/java/com/spotifylyrics/data/repository/LyricsRepositoryImpl.kt#L38) consulta caché vigente, prioriza LRCLIB, prueba fallbacks y permite importar/exportar LRC.
- [LrclibApiService.kt](https://github.com/mliem2k/carlyrics/blob/072ecb0e6fd118df2e64efaa86e3a81fe9024cfa/app/src/main/java/com/spotifylyrics/data/remote/api/LrclibApiService.kt) mapea `plainLyrics` y `syncedLyrics`; sirve para validar forma de respuesta, no para copiar sin revisar API actual.
- Room tiene entidades de lyrics/track y schema exportado; es una prueba de viabilidad, pero no modela revisiones, TTML, word timing, idiomas múltiples ni configuración widget por instancia.
- [LyricsOrchestrator.kt](https://github.com/mliem2k/carlyrics/blob/072ecb0e6fd118df2e64efaa86e3a81fe9024cfa/app/src/main/java/com/spotifylyrics/service/LyricsOrchestrator.kt#L55) actualiza el widget sólo si cambia la línea. Esa granularidad es apropiada.
- [MusicNotificationListener.kt](https://github.com/mliem2k/carlyrics/blob/072ecb0e6fd118df2e64efaa86e3a81fe9024cfa/app/src/main/java/com/spotifylyrics/service/notification/MusicNotificationListener.kt#L27) usa whitelist y decide “playing” por action title/template; falla para idiomas, players no listados y notificaciones no multimedia.
- [MediaSessionManager.kt](https://github.com/mliem2k/carlyrics/blob/072ecb0e6fd118df2e64efaa86e3a81fe9024cfa/app/src/main/java/com/spotifylyrics/service/media/MediaSessionManager.kt#L58) ejecuta un loop permanente cada 500 ms con wall clock; `startMonitoring/stopMonitoring` están vacíos y los controles sólo cambian estado local. Debe descartarse.
- [LyricsForegroundService.kt](https://github.com/mliem2k/carlyrics/blob/072ecb0e6fd118df2e64efaa86e3a81fe9024cfa/app/src/main/java/com/spotifylyrics/service/foreground/LyricsForegroundService.kt#L54) muestra una notificación persistente y observa línea/track, pero no prueba que `mediaPlayback` sea un FGS válido para observar audio de terceros.
- El árbol contiene un APK-oriented build surface desordenado: `build-tools.zip`, `platform-35.zip`, keystores versionados, numerosos scripts duplicados, Flutter y backup. Es deuda de repo, no material reutilizable.
- Tests encontrados: 6 de LRCLIB con MockWebServer y 6 de CompanionLyricsViewModel; no se observó cobertura focal de Room migrations, widget host, media real, foreground restrictions o notification heuristics.

### Qué sí tomaría

- Adaptar el flujo LRCLIB y casos de MockWebServer.
- Adaptar Room como autoridad local, expiración y búsqueda.
- Adaptar el evento de cambio de línea para widget/notificación.
- Adaptar el patrón de pantallas Compose/Glance sólo dentro de sus límites reales.

### Qué reescribiría o descartaría

- Reescribir toda detección/control media y reloj.
- Reescribir cache schema y matching de tracks.
- Reescribir widget state como entidad por instancia.
- No copiar Hilt por inercia, fallbacks no verificados, build scripts, Flutter backup, archivos SDK ni keystores.
- Posponer Android Auto hasta tener una fuente de reproducción y sincronización validada.

## Licencias y obligaciones

Ambos snapshots contienen un archivo `LICENSE` MIT:

| Proyecto | Copyright observado | Obligación al reutilizar |
|---|---|---|
| AirLyrics | Copyright (c) 2026 AndSi-327 | Incluir aviso de copyright y texto de permiso en todas las copias o porciones sustanciales |
| CarLyrics | Copyright (c) 2025 Michael Liem | La misma obligación MIT |

MIT permite usar, copiar, modificar, fusionar, publicar, distribuir, sublicenciar y vender, sin garantía. Si sólo estudiamos comportamiento y escribimos una implementación independiente desde APIs/especificaciones, no estamos incorporando una copia sustancial; aun así se documentará la procedencia conceptual. Si se copia o adapta código más adelante:

1. Conservar el `LICENSE` propio del producto.
2. Añadir el texto MIT y copyrights exactos en `THIRD_PARTY_NOTICES.md`.
3. Marcar archivos/porciones adaptadas y conservar historial de origen/commit.
4. Revisar las licencias transitivas y de assets por separado.
5. No asumir que MIT sobre el programa otorga derechos sobre letras, artwork, fuentes, marcas o contenido obtenido de providers.

El README de CarLyrics dice “For personal use”, pero el archivo LICENSE observado es el texto MIT sin esa restricción. Se tratará LICENSE como licencia de código y se evitará copiar contenido/assets hasta aclarar cualquier contradicción upstream.

AMLL/`applemusic-like-lyrics` está bajo AGPL-3.0 según su [repositorio y licencia](https://github.com/amll-dev/applemusic-like-lyrics/blob/main/LICENSE). El plan inicial contemplaba el perfil TTML nativo sin integrar la librería. **Actualización de procedencia:** el trabajo posterior de motion sí estudió directamente el renderer original y reprodujo una combinación extensa de sus algoritmos y parámetros. No puede afirmarse independencia sólo por escribir Kotlin o no añadir la dependencia. La evaluación vigente y la decisión pendiente están en [LICENSE_STATUS.md](LICENSE_STATUS.md). El repositorio [amll-ttml-db](https://github.com/amll-dev/amll-ttml-db) declara CC0 para su contribución, pero eso no garantiza derechos de terceros sobre cada letra.

Fuentes de licencia: [AirLyrics LICENSE](https://github.com/AirLyrics/AirLyrics/blob/89ebd0edfc6ce93c45db5fc9a1cb62fed48f50c3/LICENSE), [CarLyrics LICENSE](https://github.com/mliem2k/carlyrics/blob/072ecb0e6fd118df2e64efaa86e3a81fe9024cfa/LICENSE), [MIT OSI](https://opensource.org/license/mit), [GNU AGPL](https://www.gnu.org/licenses/agpl-3.0.html).

## Conclusión

La combinación correcta es conceptual: AirLyrics aporta problemas reales de overlay/TTML/media y CarLyrics demuestra el recorrido cache/widget/notification. La arquitectura y el código de producción deben ser nuevos. La primera base debe contener únicamente el modelo común, motor de sincronización testeable y shell Compose; providers, Room, NLS, overlay y Glance entran después de tener contratos y pruebas.
