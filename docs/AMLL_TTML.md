# Compatibilidad TTML de AMLL

## Contrato de presentación

- `LyricsLine` define el texto y el intervalo de la línea.
- `LyricsWord` define la unidad textual visible para Now Playing y Current Word widget.
- `LyricsWordSegment` conserva los snaps AMLL internos y sólo controla el avance del highlight.
- `SegmentedWordProgress` pondera cada snap por su tramo visible; sin segmentos cae al intervalo de la palabra.
- La microanimación recibe el progreso compuesto y lo interpola como una única palabra; cruzar un snap no reinicia el transform.
- El widget persiste `LyricsWord.id` y `LyricsWord.text`. Un cambio de segmento no genera un texto ni una firma de render nuevos.
- La división visual del widget usa `SyllableBreaker` o grafemas de `LyricsWord.text`; no usa límites AMLL y no descarta caracteres con ellipsis.

El importador y el exportador usan las convenciones documentadas por AMLL:
https://amll.dev/en/guides/lyric/ttml

## Compatibilidad implementada

- Tiempos absolutos `MM:SS.mmm`, `HH:MM:SS.mmm` y segundos sin sufijo. En documentos con `itunes:timing`, las expresiones con unidades también se interpretan en la línea temporal de la canción. El TTML genérico conserva los offsets relativos.
- Modos `itunes:timing="Word"` y `"Line"`.
- Espacios entre spans conservados en las palabras, sin insertar espacios entre sílabas contiguas.
- Traducciones `x-translation` y romanización `x-roman` separadas de la letra cantada.
- Traducciones/transliteraciones de `iTunesMetadata` asociadas mediante `for` / `itunes:key`.
- Coros `x-bg` como líneas con tiempos propios, vinculadas a su línea principal y guardadas en Room.
- Voces `ttm:agent`, con progreso simultáneo para líneas que se solapan.
- Exportación con auxiliares dentro de su párrafo, coros anidados y agentes válidos.
- Migración de biblioteca 2 → 3 que conserva las líneas existentes.

La implementación es Kotlin nativa. No incorpora el reproductor web ni código de la biblioteca AMLL.

## Límites

Las anotaciones Ruby se omiten de la letra principal para no duplicar el texto; no se dibujan como furigana. Los metadatos editoriales `amll:meta`, las secciones de canción y las extensiones de estilo no forman parte del modelo persistido ni se conservan en una exportación.

Un TTML importado con una versión anterior debe importarse de nuevo para reconstruir los datos que aquella versión no guardó.

## Verificación

`TtmlLyricsParserTest` cubre tiempos AMLL, separación de voces/auxiliares, espacios, sidecars y XML seguro. `LyricsDocumentExporterTest` cubre estructura AMLL y exportación/importación. `LyricsSyncEngineTest` cubre solapamientos.

`TtmlImportInstrumentedTest` usa el ejemplo original `app/src/androidTest/assets/amll-compatibility.ttml` para verificar parser Android y almacenamiento. `LyricsDatabaseMigrationTest` comprueba la migración de datos existentes.
