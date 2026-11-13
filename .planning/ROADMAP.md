# Roadmap: Repasumi

## Overview

Repasumi ya funciona como prototipo (v0.5.0). A la 1.0 le falta la infraestructura de lanzamiento, no funciones nuevas. El camino crítico lo marca la revisión de Google Play para apps familiares más que el código. El primer AAB firmado entra en la pista interna hacia el 2026-11-17, la pista cerrada con 12 o más familias hacia el 2026-11-19 y el envío a producción hacia el 2026-11-24, para salir antes del 2026-12-03. Por eso la fase 1 (canal de release) y la fase 6 (cumplimiento y tienda) empiezan el primer día. El trabajo va en tres pistas paralelas: la pista nativa y de build (fases 1 y 3), la pista JS sobre `web/index.html` (fases 2, 4 y 5, siempre en serie) y la pista de cumplimiento (fase 6). Dos reglas no se rompen nunca: el niño no ve nada que el padre no haya aprobado, y ningún dato sale del móvil.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Base de release y canal de Google Play** - AAB firmado de `com.repasumi` con el modelo dentro, sin permisos de red, alineado a 16 KB y en la pista interna (objetivo 2026-11-17)
- [ ] **Phase 2: Base comprobable y datos v2** - Esquema v2 con migraciones, recuperación e identificadores estables, y pruebas de dominio JS en CI (objetivo 2026-11-17)
- [ ] **Phase 3: Runtime nativo robusto** - Un solo modelo y una sola generación por proceso, OCR estable, prompt sin inyección, comprobación del modelo y PIN en Java (objetivo 2026-11-19)
- [ ] **Phase 4: Control parental y revisión del padre** - PIN en todo lo que es del padre y revisión obligatoria: el niño solo ve preguntas aprobadas (primer corte 2026-11-19, objetivo 2026-11-21)
- [ ] **Phase 5: Generación por fragmentos y fuentes validadas** - Todo el texto de las fotos, «Fuente» fiable, progreso por etapas, cancelar y reanudar (objetivo 2026-11-23)
- [ ] **Phase 6: Cumplimiento, aceptación E2E y envío a la tienda** - Política, Play Console, ficha, prueba E2E, aceptación en dispositivo, pista cerrada y producción (objetivo 2026-11-24)

## Calendario (camino crítico)

| Fecha (2026) | Hito | Qué tiene que estar listo |
|---|---|---|
| 11-13 (vie) | Confirmar en Play Console que no aplica la regla de 12 testers durante 14 días; firmar la lista de decisiones irreversibles de la primera subida | Nada |
| ~11-15 (dom) | Veredicto del spike: el modelo se carga sin copia o con una sola copia | Spike de la fase 1 |
| **~11-17 (mar)** | Primer AAB firmado en la **pista interna**: firma, `com.repasumi`, asset pack, 16 KB, sin INTERNET y R8 | Fase 1 |
| **~11-19 (jue)** | **Pista cerrada** con 12 o más familias y App content completo; sirve de ensayo de la revisión de políticas | Fase 1, primer corte de la fase 4 (PIN y `studyableQuestions()`), borradores de la fase 6 |
| **~11-24 (mar)** | **Envío a producción** con publicación gestionada; es la 1.0 real | Fases 1 a 5 |
| 11-24 → 12-03 | Solo correcciones de política, sin funciones nuevas (margen para un rechazo) | Nada |
| **12-03 (jue)** | Fecha límite | Nada |

## Pistas de trabajo y orden de ejecución

- **Pista nativa y de build:** fase 1 → fase 3. La fase 3 arranca en cuanto la fase 1 integra `ModelLocator` y los cambios del manifiesto y de Gradle; el contrato `docs/BRIDGE.md` v2 puede empezar antes.
- **Pista JS sobre `web/index.html`:** fase 2 → fase 4 → fase 5, siempre una detrás de otra. Dos fases nunca editan ese archivo a la vez. La única excepción es el primer PR de la fase 1 (quitar rutas de depuración), que se integra antes del reformateo de la fase 2.
- **Pista de cumplimiento:** fase 6, desde el día 1 hasta el envío a producción. No edita `web/index.html`.
- Cada fase o PR se trabaja en su propio `git worktree`; nunca se cambia de rama en el working tree principal, porque hay sesiones paralelas sobre el mismo repo.
- Si no se trabaja en paralelo, el orden numérico (1 → 6) es una serialización válida.
- **No se recorta nunca:** las comprobaciones de INTERNET y 16 KB (fase 1), las migraciones y la recuperación (fase 2), y la puerta del PIN con `studyableQuestions()` (fase 4).

## Phase Details

### Phase 1: Base de release y canal de Google Play
**Goal**: Un AAB firmado de `com.repasumi`, con el modelo dentro, sin permisos de red y con las librerías nativas alineadas a 16 KB, se instala desde la pista interna de Google Play y genera preguntas de principio a fin en un móvil real.
**Depends on**: Nothing (primera fase)
**Paralelismo**: En paralelo con las fases 2 y 6. Su único cambio en `web/index.html` (quitar las rutas de depuración y la IA del navegador) es su primer PR y se integra antes de que la fase 2 empiece el reformateo.
**Fecha objetivo**: 2026-11-13 → 2026-11-17 (veredicto del spike ~2026-11-15; primer AAB en la pista interna ~2026-11-17)
**Issues**: #16, #13 (comprobaciones de CI)
**Requirements**: REL-01, REL-02, REL-03, REL-04, REL-05, REL-06, REL-07, REL-08, MOD-01, TEST-04
**Success Criteria** (what must be TRUE):
  1. Un tester instala `com.repasumi` desde la pista interna de Google Play en un móvil Android 10+ ARM64 y, sin descargar ni importar nada, genera preguntas de principio a fin con el build de release (R8 activado, puente JS↔Java y JNI funcionando).
  2. Cada PR pasa por CI, que comprueba la paridad de `index.html` y falla si el AAB de release declara `INTERNET`, `ACCESS_NETWORK_STATE`, `AD_ID` o ubicación, si las librerías nativas no están alineadas a 16 KB o si quedan rutas de depuración (enlace a Hugging Face, importación manual del modelo, `globalThis.sample`, «copiar prompt»).
  3. Sin permisos de red, el OCR de fotos sigue funcionando en un dispositivo real, y los datos de la app no salen del móvil ni por copia de seguridad ni por transferencia entre dispositivos.
  4. CI genera el AAB firmado con la upload key desde un entorno protegido de GitHub, con versionCode automático y Play App Signing; el keystore no está en el repo y tiene una copia fuera de línea.
  5. Un clon limpio compila con el wrapper de Gradle, `.gitattributes` (`eol=lf`) y con llama.cpp y las GitHub Actions fijados por SHA.
**Plans**: TBD

**Notas**:
- Hoy (2026-11-13): confirmar en Play Console que no aplica la regla de 12 testers durante 14 días y firmar la lista de decisiones irreversibles de la primera subida: paquete, clave de firma generada por Google, app gratuita y versionCode siempre por encima de 7.
- `.gitignore` antes de crear el keystore. Solo cambia `applicationId`; `namespace`, el paquete Java (símbolos JNI) y la constante `ORIGIN` del WebView no se tocan.
- Spike de un día (~2026-11-15): cargar el GGUF del asset pack *install-time* sin copia (`openFd` → `FILE*` → `llama_model_load_from_file_ptr`) o, si la memoria no da, con una sola copia a `filesDir`. Todo queda detrás de `ModelLocator`.
- NDK r28c; si rompe la compilación de llama.cpp, r27 con `-Wl,-z,max-page-size=16384`.
- La copia `app/src/main/assets/index.html` pasa a generarse desde `web/index.html`.
- Investigar al planificar: carga sin copia (alineación de 32 bytes y memoria con `LOAD_MODE_NONE`) y comportamiento real del asset pack y del manifiesto fusionado en Play.

### Phase 2: Base comprobable y datos v2
**Goal**: Los datos de la familia sobreviven a actualizaciones, fallos de guardado y corrupción sin perder nada, sobre un esquema v2 que ya incluye todos los campos de la 1.0, y la lógica de dominio en JS tiene pruebas en CI.
**Depends on**: Nothing (empieza en la semana 1; ver Paralelismo)
**Paralelismo**: Abre la pista JS: es la única fase que edita `web/index.html` hasta que termina, y después vienen la 4 y la 5. Su reformateo empieza cuando está integrado el primer PR de la fase 1. En paralelo con las fases 1, 3 y 6.
**Fecha objetivo**: 2026-11-13 → 2026-11-17
**Issues**: #6, #13
**Requirements**: DATA-01, DATA-02, DATA-03, DATA-04, DATA-05, DATA-06, TEST-01, STORE-06
**Success Criteria** (what must be TRUE):
  1. Al abrir la app con datos de las versiones 0.3, 0.4 o 0.5, se migran en orden al esquema v2 después de guardar una copia del estado anterior, y el progreso y el repaso de cada pregunta se conservan aunque luego se editen o borren preguntas.
  2. Con datos dañados, la app muestra una pantalla de recuperación tranquila que no borra nada y deja seguir usándola; con datos de una versión más nueva, los abre en modo de solo lectura.
  3. Si un guardado falla, el padre ve un aviso: nunca se pierden cambios en silencio.
  4. Un tema de ejemplo borrado no vuelve a aparecer, y los temas de ejemplo son contenido original, no copiado de libros de texto.
  5. CI ejecuta con `node:test` las pruebas de dominio en JS (calificación, repaso espaciado, migraciones y validación) sobre fixtures de 0.3, 0.4 y 0.5, un almacén corrupto y uno cerca de la cuota.
**Plans**: TBD
**UI hint**: yes

**Notas**:
- Primero, un reformateo sin cambios de comportamiento: una instrucción por línea y un bloque `<script id="repasumi-core">` con funciones puras que `node:test` + `vm` cargan sin paso de build. También acaba con los conflictos entre worktrees.
- El esquema v2 lleva de una vez `uid`, `status`, `review`, `origin`, `evidence`, `seeds` y `source`. `com.repasumi` empieza con un origen vacío, así que v2 es el primer esquema de producción y su forma importa más que la migración.
- TEST-01 se asigna aquí: esta fase monta el arnés en CI y cubre calificación, repaso, migraciones y validación. Las fases 4 y 5 añaden al mismo arnés las pruebas de «ninguna pregunta sin aprobar llega al niño» y de fuentes, como criterio de éxito propio.
- Los temas de ejemplo se revisan (y, si hace falta, se reescriben) aquí porque esta fase ya los reescribe al esquema v2 con lápidas.
- No se recorta: migraciones y recuperación.

### Phase 3: Runtime nativo robusto
**Goal**: La parte nativa genera de forma estable en móviles de 4 GB: un solo modelo y una sola generación por proceso, un OCR que no se bloquea, un prompt que el texto de las fotos no puede manipular, la comprobación del modelo y del dispositivo, y el PIN guardado en Java.
**Depends on**: Phase 1 (`ModelLocator` y cambios del manifiesto y de Gradle)
**Paralelismo**: Solo toca Java y C++; va en paralelo con las fases 2 y 6 y puede solaparse con el inicio de la 4. Empieza por `docs/BRIDGE.md` v2 y la API nativa del PIN (~2026-11-17) para que la fase 4 trabaje contra `FakeAndroidBridge`. El puente sigue siendo compatible con el JS actual hasta que las fases 4 y 5 adopten el v2.
**Fecha objetivo**: 2026-11-14 → 2026-11-19 (contrato del puente v2 y API del PIN ~2026-11-17)
**Issues**: #2, #1, #16 (verificación del modelo), #13 (JUnit)
**Requirements**: MOD-02, MOD-03, GEN-03, GEN-07, GEN-08, GATE-08, TEST-03
**Success Criteria** (what must be TRUE):
  1. La primera vez que se usa cada versión, la app verifica el modelo por SHA-256 una sola vez; si falla, o si el móvil no llega al mínimo de RAM, ABI o espacio libre, la app explica el motivo y solo deja de funcionar «Generar preguntas», mientras que estudiar sigue igual.
  2. Google Play excluye del catálogo los dispositivos por debajo del umbral de RAM medido en el móvil de 4 GB.
  3. No se puede lanzar una segunda generación mientras otra está en marcha, y girar el móvil o salir de la app y volver no la interrumpe ni carga el modelo dos veces.
  4. El OCR de 1 a 30 fotos, también de fotos grandes, termina sin agotar la memoria, y después de un error se puede lanzar otra tanda sin reiniciar la app.
  5. Un texto de OCR con tokens especiales (`<|…|>`) o instrucciones no altera el prompt, y CI lo demuestra con pruebas JUnit de la construcción del prompt, la extracción del JSON y el PIN (hash PBKDF2 con sal guardado en Java, fuera de `localStorage`).
**Plans**: TBD

**Notas**:
- `ModelRuntime` vive en `RepasumiApp` (el `Application`): una sola tarea a la vez, cancelación por el callback de cada token, eventos `gen_stage` / `gen_chunk` / `gen_done` / `model_state`, `snapshot()` síncrono, lista completa de `configChanges`, pantalla encendida durante la generación y descarga del modelo en `onTrimMemory` (recortable). Sin servicio en primer plano.
- La cancelación nativa y `snapshot()` son la base de GEN-01 y GEN-04, que la fase 5 cierra de principio a fin.
- La API nativa del PIN incluye la espera creciente y la recuperación con `KeyguardManager`; su uso desde la interfaz (GATE-04, GATE-05) se verifica en la fase 4.
- OCR: fotos reducidas a unos 2400 px, salida estructurada `pages[]` y `busy` liberado en `try/finally`. Se corrige el `\\n` que hoy se envía literal.
- Medir 2 frente a 4 hilos, memoria y calor en el móvil de 4 GB; nunca compilar con `+dotprod` (SIGILL en núcleos A53/A73).
- Investigar al planificar: tokens por segundo, memoria máxima y calor en 4 GB; comportamiento del callback; si el llama.cpp fijado expone el sampler de gramática; umbral de RAM del catálogo de dispositivos.

### Phase 4: Control parental y revisión del padre
**Goal**: El niño solo estudia preguntas que el padre ha aprobado, y todo lo que es del padre (crear, revisar, publicar, borrar, exportar, enlaces externos) está detrás de un PIN.
**Depends on**: Phase 2 (esquema v2 e identificadores estables), Phase 3 (API nativa del PIN y contrato del puente v2)
**Paralelismo**: Segunda fase de la pista JS: edita `web/index.html` sola, después de la 2 y antes de la 5. Puede solaparse con el final de la fase 3. También añade en Padres los enlaces a los textos legales que prepara la fase 6.
**Fecha objetivo**: 2026-11-17 → 2026-11-21. Primer corte (PIN en `dispatch()` y `studyableQuestions()`) en el build de la pista cerrada del ~2026-11-19.
**Issues**: #5 (mínimo); control parental (decisión de la 1.0)
**Requirements**: GATE-01, GATE-02, GATE-03, GATE-04, GATE-05, GATE-06, GATE-07, REV-01, REV-02, REV-03, REV-05, REV-06, REV-07, REV-08, REV-09, STORE-07
**Success Criteria** (what must be TRUE):
  1. En el primer arranque el padre crea un PIN de 4 dígitos en una bienvenida de 4 pantallas como máximo, que incluye «Funciona sin Internet» comprobado en tiempo de ejecución; desde entonces Padres, Añadir, borrar, exportar o copiar el informe, los enlaces externos, los perfiles y el cambio de PIN piden el PIN, y estudiar y la 🚩 no lo piden.
  2. El área del padre se vuelve a bloquear sola a los 5 minutos o al salir de la app; tras varios intentos fallidos la espera crece sin borrar datos; el padre puede cambiar el PIN o recuperarlo con el bloqueo de pantalla del móvil (o pasadas 24 horas si no lo hay), y Padres muestra el registro de último acceso, intentos fallidos y restablecimientos.
  3. Un tema recién generado, y las preguntas de un «Reto nuevo» que el padre lanza tras el PIN, quedan pendientes de revisión: el niño no ve ningún borrador ni pregunta sin aprobar en ningún modo de estudio, Padres avisa de «N preguntas nuevas por revisar» y una prueba de dominio en CI lo demuestra.
  4. El padre aprueba, edita o descarta cada pregunta con un editor validado para los cinco tipos; sus ediciones no se pierden al redibujar, girar o cerrar la app; puede dejar un borrador a medias y retomarlo, y solo puede publicar cuando no queda ninguna pendiente y hay al menos una aprobada (no existe «Aprobar todo»).
  5. El padre puede editar un tema publicado (si cambia la respuesta correcta, el repaso de esa pregunta vuelve a empezar) y atender la cola de preguntas marcadas con 🚩: corregirlas, descartarlas o enviar él mismo por email un informe con solo el texto de la pregunta.
**Plans**: TBD
**UI hint**: yes

**Notas**:
- El PIN se decide en un único `dispatch()` con un conjunto `GATED`, y la acción pendiente se repite tras introducir el PIN. Nunca se protege ocultando botones.
- `studyableQuestions()` es el único acceso a preguntas desde las pantallas del niño.
- El editor escribe en el almacén en cada `input` (`data-draft="<uid>.<campo>"`) sin llamar a `render()`; el progreso se actualiza con `patchProgress()`. `confirm()` pasa a `modal()`.
- «¿Has olvidado el PIN?» solo aparece tras el primer fallo; cada restablecimiento deja «PIN restablecido el {fecha}» en el registro y un aviso en Padres.
- No se recorta: la puerta del PIN y `studyableQuestions()`. Si el calendario aprieta, el registro (GATE-06) y el pulido de «Funciona sin Internet» (STORE-07) serían lo primero en pasar a la 1.1, siempre con decisión explícita.

### Phase 5: Generación por fragmentos y fuentes validadas
**Goal**: La generación aprovecha todo el texto de las fotos, cada «Fuente» apunta a una foto real de su fragmento, y el padre ve el progreso, cancela y retoma la generación sin perder trabajo.
**Depends on**: Phase 3 (runtime y eventos `gen_*`), Phase 4 (modelo de revisión y editor)
**Paralelismo**: Última fase de la pista JS, después de la 4; incluye algo de Java para los fragmentos y la reanudación. En paralelo con la fase 6.
**Fecha objetivo**: 2026-11-20 → 2026-11-23, antes del envío a producción del ~2026-11-24
**Issues**: #2, #1, #5 (evidencia en la revisión)
**Requirements**: GEN-01, GEN-02, GEN-04, GEN-05, GEN-06, REV-04
**Success Criteria** (what must be TRUE):
  1. Con hasta 30 fotos, las preguntas salen de todo el texto, repartido en fragmentos, y no solo de los primeros 1200 caracteres; el padre ve el progreso por etapas: texto de las fotos, fragmento N de M y validación.
  2. La «Fuente» de cada pregunta se asigna desde su fragmento y se rechaza cualquier cita a una foto que no pertenezca a él; CI lo comprueba con pruebas de dominio de las fuentes.
  3. En la revisión, cada pregunta muestra su foto de origen y el fragmento de texto del que sale; las que llevan ⚠ porque el dato no aparece en esa foto salen las primeras, y un toque abre la foto.
  4. Girar el móvil, cambiar de tema o salir de la app durante la generación no la pierde, y si el padre cancela conserva las preguntas de los fragmentos que ya han terminado.
  5. Si Android cierra la app a mitad de una generación, al volver el padre puede continuar con las fotos que faltan sin repetir las que ya se procesaron.
**Plans**: TBD
**UI hint**: yes

**Notas**:
- `planChunks` (unos 2000 caracteres por fragmento, conservando las etiquetas «Foto N»), `startGeneration(job)` y `mergeChunkResult`, que se puede aplicar dos veces al mismo fragmento sin duplicar preguntas.
- El borrador guarda `source.pages` y un punto de control tras cada fragmento; al publicar se descarta `source.pages` para no agotar la cuota de `localStorage`.
- El botón «Generar preguntas» refleja el `model_state` de la fase 3.
- Línea de corte (parte del plan): se mantienen el `src` determinista y la regla dura del rango de fotos; se pueden dejar fuera la puntuación de solapamiento de palabras y la gramática GBNF. Si se recorta el solapamiento, el ⚠ de REV-04 pasa a la 1.1 con decisión explícita; la foto de origen y el fragmento se mantienen.
- Investigar al planificar: tamaño de fragmento y umbrales de solapamiento con OCR real de libros de texto en español.

### Phase 6: Cumplimiento, aceptación E2E y envío a la tienda
**Goal**: Repasumi 1.0 cumple las políticas de Google Play y del programa de apps familiares, supera la prueba cerrada con 12 o más familias y se envía a producción a tiempo para publicarse antes del 2026-12-03.
**Depends on**: Phase 1 (pista cerrada), Phase 4 (primer corte para la pista cerrada), Phase 5 (prueba E2E, aceptación en dispositivo y producción)
**Paralelismo**: Empieza el día 1 y va en paralelo con todo: la portada, la LICENSE, la política y los borradores de Play Console no esperan a nadie. No edita `web/index.html`: los textos legales son archivos aparte y la fase 4 añade los enlaces desde Padres.
**Fecha objetivo**: 2026-11-13 → 2026-11-24 (pista cerrada ~2026-11-19; envío a producción ~2026-11-24). Del 2026-11-24 al 2026-12-03, solo correcciones de política.
**Issues**: #14, #13 (prueba de humo de principio a fin)
**Requirements**: STORE-01, STORE-02, STORE-03, STORE-04, STORE-05, STORE-08, STORE-09, TEST-02
**Success Criteria** (what must be TRUE):
  1. La política de privacidad está publicada en una URL fija de GitHub Pages, que CI comprueba, y también dentro de la app sin enlace externo; Pages publica solo la portada y la política, sin la web-app ni `web/local-ai.js`.
  2. El repo tiene una LICENSE con todos los derechos reservados y la app muestra los avisos de llama.cpp (MIT), Qwen (Apache-2.0) y ML Kit.
  3. La Play Console está completa (seguridad de los datos «no se recogen datos», público de 6 a 16 años sin 18+, clasificación IARC y nota de «Acceso a la app» para los revisores) y la ficha está en español de España, con capturas originales sin logos de editoriales, publicada solo en España y gratis.
  4. CI ejecuta una prueba de humo de principio a fin (PIN → generar → revisar → publicar → estudiar) con un puente Android simulado, y la generación completa 10 veces seguidas sin fallos en el Xiaomi 12T y en un móvil de 4 GB, con giro de pantalla y reanudación tras `am kill`, usando el build instalado desde Google Play.
  5. Doce o más familias generan y revisan temas reales sin ayuda en la pista cerrada, y la 1.0 se envía a producción a tiempo para salir antes del 2026-12-03.
**Plans**: TBD
**UI hint**: yes

**Notas**:
- Una tabla de inventario de datos alimenta la política (alojada y empaquetada en la app) y las respuestas de seguridad de los datos. La URL de privacidad se fija una vez y no se mueve.
- La pista cerrada (~2026-11-19) sirve de ensayo de la revisión de políticas y ya debe llevar el PIN y la garantía de que nada sin revisar llega al niño. Hay que avisar a los testers de que sus datos de la 0.5 no pasan a `com.repasumi`.
- La revisión de apps familiares no tiene plazo publicado: asumir 7 días o más y no reenviar nunca mientras haya una revisión en curso.
- Investigar al planificar: si la política de contenido generado por IA aplica a la generación en el dispositivo revisada por el padre, y cómo describir la IA en el cuestionario IARC.

## Cobertura

| Fase | Requisitos | Total |
|---|---|---|
| 1 | REL-01…08, MOD-01, TEST-04 | 10 |
| 2 | DATA-01…06, TEST-01, STORE-06 | 8 |
| 3 | MOD-02, MOD-03, GEN-03, GEN-07, GEN-08, GATE-08, TEST-03 | 7 |
| 4 | GATE-01…07, REV-01…03, REV-05…09, STORE-07 | 16 |
| 5 | GEN-01, GEN-02, GEN-04, GEN-05, GEN-06, REV-04 | 6 |
| 6 | STORE-01…05, STORE-08, STORE-09, TEST-02 | 8 |

Requisitos v1 asignados: 55/55, sin huérfanos ni duplicados.

## Progress

**Execution Order:**
Pista nativa 1 → 3; pista JS 2 → 4 → 5 (en serie sobre `web/index.html`); fase 6 en paralelo de principio a fin. Sin paralelismo, el orden numérico 1 → 6 es válido.

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Base de release y canal de Google Play | 0/TBD | Not started | - |
| 2. Base comprobable y datos v2 | 0/TBD | Not started | - |
| 3. Runtime nativo robusto | 0/TBD | Not started | - |
| 4. Control parental y revisión del padre | 0/TBD | Not started | - |
| 5. Generación por fragmentos y fuentes validadas | 0/TBD | Not started | - |
| 6. Cumplimiento, aceptación E2E y envío a la tienda | 0/TBD | Not started | - |
