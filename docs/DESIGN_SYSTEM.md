# Signal Noir Design System

## Principios

1. **La letra manda.** En Sonando, ningún control compite con la línea activa.
2. **La portada ilumina.** El color de artwork ambienta; nunca reduce el contraste del contenido.
3. **Menos cajas, más ritmo.** Espacio, tipografía y divisores agrupan antes que una card.
4. **Información honesta.** Badges y estados describen el nivel real de sincronización y acceso.
5. **Una sola voz.** Todas las pantallas comparten tokens, componentes y movimiento.

## Color

La paleta base oscura usa grafito, no negro puro:

- `canvas`: `#111315`
- `surfaceLow`: `#171A1D`
- `surface`: `#1D2024`
- `surfaceHigh`: `#262A2F`
- `glass`: superficie alta con alpha controlado
- `textPrimary`: `#F4F6F7`
- `textMuted`: `#AEB5BC`
- `signal`: color de portada o acento del sistema

El tema claro usa blanco roto y grises fríos. Material You es opcional. Cuando hay artwork, `PaletteEngine` extrae acentos, ajusta contraste y tiñe el fondo. La transición de paleta es interpolada; nunca cambia de golpe.

## Tipografía

- Display: sans serif, peso semibold, tracking cerrado.
- Headers: sans serif, semibold o medium.
- Body: sans serif, normal.
- Microetiquetas y badges: monospace, medium, mayúsculas y tracking amplio.
- Lyrics: estilos display/headline existentes según layout; se evitan pesos extra.

La escala completa vive en `LyricsTheme` y se expone mediante `MaterialTheme.typography`. Los textos deben respetar font scaling y no fijar alturas que corten contenido.

## Espaciado

| Token | Valor | Uso |
| --- | ---: | --- |
| `micro` | 4 dp | Separación interna mínima |
| `tiny` | 8 dp | Icono/label y filas densas |
| `small` | 12 dp | Controles relacionados |
| `medium` | 16 dp | Padding estándar |
| `large` | 24 dp | Separación de secciones |
| `xLarge` | 32 dp | Márgenes editoriales |
| `huge` | 48 dp | Ritmo entre regiones principales |

## Radios

- `small`: 10 dp
- `medium`: 16 dp
- `large`: 24 dp
- `hero`: 32 dp
- `pill`: 999 dp

## Superficies

- **Base:** fondo sin contenedor.
- **Raised:** regiones de control o preview.
- **Interactive:** fila seleccionable con feedback táctil.
- **Glass:** sólo navegación y controles flotantes. Usa transparencia simple como fallback; no depende de blur.

## Elevación, opacidad y contraste

La profundidad se crea primero con color y borde. `SignalElevation` define `flat`, `floating` y `navigation`; las sombras son cortas y de baja opacidad. `SignalBlur` documenta los radios permitidos para casos donde la plataforma pueda sostenerlos, aunque el fallback siempre es una superficie translúcida. Texto principal debe conservar contraste AA; texto secundario nunca baja de la opacidad definida por `SignalOpacity.muted`.

## Componentes

- Touch targets mínimos de 48 dp.
- Chips compactos y con borde sutil.
- Badges de calidad usan texto, no sólo color.
- Sliders con etiqueta y valor visibles.
- Empty states incluyen icono de identidad, título, explicación y acción sólo cuando existe una acción real.

## Rendimiento

- Gradientes se crean en `drawWithCache` o se recuerdan por paleta.
- Extracción de artwork ocurre fuera del hilo principal y se cachea por canción.
- No se usan loops manuales ni retardos por frame.
- Blur es opcional; la base visual funciona sin él.
- Los modelos de UI permanecen inmutables y los estados derivados se recuerdan.
