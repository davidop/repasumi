# Requirements: Repasumi

**Defined:** 2026-11-13
**Core Value:** Una familia hace fotos del libro y obtiene, sin que nada salga del móvil, preguntas fiables y revisadas por el padre para que el niño repase.

## v1 Requirements

Alcance de la 1.0 (milestone «1.0 · Google Play», fecha límite 2026-12-03). Cada requisito corresponde a una fase del roadmap.

### Release y build (REL)

- [ ] **REL-01**: La app se publica en Google Play como `com.repasumi`, para Android 10+ (minSdk 29) y solo ARM64. El paquete Java y el `namespace` no cambian, y el versionCode lo genera CI.
- [ ] **REL-02**: CI genera un AAB firmado con la upload key, que está en GitHub Secrets dentro de un entorno protegido, y se usa Play App Signing. El keystore nunca entra en el repo (`.gitignore`) y tiene una copia de seguridad fuera de línea.
- [ ] **REL-03**: El AAB de release no declara los permisos `INTERNET`, `ACCESS_NETWORK_STATE`, `AD_ID` ni de ubicación. CI falla si aparece alguno, y el OCR sigue funcionando en el dispositivo sin esos permisos.
- [ ] **REL-04**: Las librerías nativas están alineadas a páginas de 16 KB (NDK r28c), y CI lo comprueba.
- [ ] **REL-05**: El build de release tiene R8 activado con reglas *keep*. Instalado desde Google Play, funciona de principio a fin (el puente JS↔Java y la JNI).
- [ ] **REL-06**: El build de release no contiene rutas de depuración: ni el enlace a Hugging Face, ni la importación manual del modelo, ni `globalThis.sample`, ni «copiar prompt». CI lo comprueba.
- [ ] **REL-07**: El repo es reproducible: wrapper de Gradle, `.gitattributes` (`eol=lf`), y llama.cpp y las GitHub Actions fijados por SHA.
- [ ] **REL-08**: Los datos del niño no salen del móvil por copia de seguridad ni por transferencia entre dispositivos (`allowBackup=false` + `dataExtractionRules`).

### Modelo en el dispositivo (MOD)

- [ ] **MOD-01**: El modelo GGUF se instala con la app desde Google Play (asset pack *install-time*). La familia no tiene que descargar ni importar nada.
- [ ] **MOD-02**: El modelo se verifica por SHA-256 una vez por versión, antes de usarlo por primera vez. Si la verificación falla, la generación se desactiva con un mensaje claro.
- [ ] **MOD-03**: Si el dispositivo no llega al mínimo (RAM, ABI o espacio libre), la app lo explica y solo desactiva «Generar preguntas»; estudiar sigue funcionando. Google Play excluye los dispositivos con poca RAM.

### Generación de preguntas (GEN)

- [ ] **GEN-01**: El padre puede cancelar una generación en curso y conserva las preguntas de los fragmentos que ya han terminado.
- [ ] **GEN-02**: El padre ve el progreso por etapas: texto de las fotos, fragmento N de M y validación.
- [ ] **GEN-03**: No se puede lanzar una segunda generación mientras hay otra en marcha.
- [ ] **GEN-04**: Girar la pantalla, cambiar de tema o salir de la app no pierde la generación ni carga el modelo dos veces. Si Android cierra la app, el padre puede continuar con las fotos que faltan.
- [ ] **GEN-05**: La generación usa todo el texto de las fotos, repartido en fragmentos, y no solo los primeros 1200 caracteres.
- [ ] **GEN-06**: La «Fuente» de cada pregunta se asigna a partir del fragmento del que sale. Se rechaza cualquier cita a una foto que no pertenezca a ese fragmento.
- [ ] **GEN-07**: El texto extraído de las fotos no puede inyectar tokens especiales ni instrucciones en el prompt.
- [ ] **GEN-08**: El OCR de 1 a 30 fotos no se queda bloqueado después de un error y reduce la resolución de las fotos para no agotar la memoria.

### Datos y recuperación (DATA)

- [ ] **DATA-01**: Los datos llevan versión de esquema y se migran en orden, guardando antes una copia del estado anterior.
- [ ] **DATA-02**: Si los datos están dañados, la app muestra una pantalla de recuperación tranquila que no borra nada y permite seguir usándola.
- [ ] **DATA-03**: Si falla un guardado, el padre lo ve. Nunca se pierden cambios sin aviso.
- [ ] **DATA-04**: El progreso y el repaso van ligados a un identificador estable de cada pregunta. Editar o borrar preguntas no estropea las estadísticas.
- [ ] **DATA-05**: Un tema de ejemplo que se ha borrado no vuelve a aparecer.
- [ ] **DATA-06**: Si los datos vienen de una versión más nueva de la app, se abren en modo de solo lectura en vez de sobrescribirse.

### Revisión y aprobación del padre (REV)

- [ ] **REV-01**: Un tema recién generado queda como borrador. El niño nunca ve borradores ni preguntas sin aprobar en ningún modo de estudio.
- [ ] **REV-02**: El padre puede aprobar, editar o descartar cada pregunta, con un editor validado para cada uno de los cinco tipos.
- [ ] **REV-03**: Las ediciones del padre se guardan al instante y no se pierden aunque se redibuje la pantalla, se gire el móvil o se cierre la app.
- [ ] **REV-04**: Cada pregunta muestra su foto de origen y el fragmento de texto del que sale. Si el dato no aparece en esa foto, lleva un ⚠; las preguntas con ⚠ salen las primeras y un toque abre la foto.
- [ ] **REV-05**: Un tema solo se puede publicar cuando no queda ninguna pregunta pendiente y hay al menos una aprobada. No existe «Aprobar todo».
- [ ] **REV-06**: El padre puede editar un tema ya publicado. Si cambia la respuesta correcta de una pregunta, el repaso de esa pregunta vuelve a empezar.
- [ ] **REV-07**: El padre puede dejar un borrador a medias y retomarlo más tarde.
- [ ] **REV-08**: «Reto nuevo» lo lanza el padre después del PIN, y sus preguntas entran en la cola de revisión. Padres muestra un aviso de «N preguntas nuevas por revisar».
- [ ] **REV-09**: Las preguntas que el niño marca con 🚩 llegan a una cola en Padres. Desde ahí, el padre puede corregirlas, descartarlas o enviar por email un informe al desarrollador. El envío lo inicia siempre el padre y solo incluye el texto de la pregunta.

### Control parental (GATE)

- [ ] **GATE-01**: En el primer arranque, el padre crea un PIN de 4 dígitos, dentro de una bienvenida de 4 pantallas como máximo.
- [ ] **GATE-02**: Piden el PIN «Padres», «Añadir» (fotos, generar y revisar), borrar, exportar o copiar el informe, los enlaces externos, los perfiles y el cambio de PIN. Estudiar y la 🚩 no lo piden.
- [ ] **GATE-03**: El área del padre se vuelve a bloquear sola a los 5 minutos o al salir de la app.
- [ ] **GATE-04**: Tras varios intentos fallidos, la espera para volver a probar va creciendo, sin borrar nunca los datos.
- [ ] **GATE-05**: Si el padre olvida el PIN, lo recupera confirmando el bloqueo de pantalla del móvil. Si el móvil no tiene bloqueo, puede restablecerlo pasadas 24 horas. En los dos casos queda un aviso visible en Padres.
- [ ] **GATE-06**: Padres muestra un registro con el último acceso, los intentos fallidos y los restablecimientos del PIN.
- [ ] **GATE-07**: El padre puede cambiar el PIN.
- [ ] **GATE-08**: El hash del PIN se guarda en Java (PBKDF2 con sal), fuera de `localStorage`.

### Pruebas automatizadas (TEST)

- [ ] **TEST-01**: CI ejecuta pruebas de la lógica de dominio en JS: calificación, repaso espaciado, migraciones, validación, fuentes y que ninguna pregunta sin aprobar llegue al niño.
- [ ] **TEST-02**: CI ejecuta una prueba de humo de principio a fin (PIN → generar → revisar → publicar → estudiar) con un puente Android simulado.
- [ ] **TEST-03**: CI ejecuta pruebas JUnit de la construcción del prompt, la extracción del JSON y el PIN.
- [ ] **TEST-04**: Cada PR pasa por CI, que incluye la paridad de `index.html` y las comprobaciones de release.

### Cumplimiento y tienda (STORE)

- [ ] **STORE-01**: El repo tiene una LICENSE con todos los derechos reservados, y la app muestra los avisos de terceros: llama.cpp (MIT), Qwen (Apache-2.0) y ML Kit.
- [ ] **STORE-02**: La política de privacidad está publicada en GitHub Pages en una URL fija y también dentro de la app, sin enlace externo.
- [ ] **STORE-03**: GitHub Pages publica solo una portada y la política de privacidad. La web-app y la IA del navegador (`web/local-ai.js`) dejan de publicarse.
- [ ] **STORE-04**: La Play Console queda completa: seguridad de los datos «no se recogen datos», público de 6 a 16 años (sin 18+), clasificación IARC y una nota en «Acceso a la app» para los revisores.
- [ ] **STORE-05**: La ficha está en español de España, con capturas de contenido original sin logos de editoriales, publicada solo en España y gratis.
- [ ] **STORE-06**: Los temas de ejemplo de la app son contenido original, no copiado de libros de texto.
- [ ] **STORE-07**: Una pantalla «Funciona sin Internet» comprueba en tiempo de ejecución que la app no tiene permiso de red.
- [ ] **STORE-08**: Hay una prueba cerrada con 12 o más familias en la pista cerrada de Google Play, y la 1.0 se envía a producción a tiempo para salir antes del 2026-12-03.
- [ ] **STORE-09**: La generación completa 10 veces seguidas sin fallos en el Xiaomi 12T y en un dispositivo de 4 GB de RAM, incluidos el giro de pantalla y la reanudación después de que Android cierre la app.

## v2 Requirements

Se dejan para la 1.1. Están registrados, pero no entran en el roadmap actual.

### Estudio y motivación

- **STUDY-01**: Notificaciones de repaso y agenda configurable (#7)
- **STUDY-02**: Pistas en las parejas, nota reproducible y dificultad por etapa en el examen (#8)
- **STUDY-03**: Perfiles familiares avanzados (#9) y gamificación (#10)
- **STUDY-04**: Las respuestas de «completar» aceptan diferencias de tildes, mayúsculas y artículos

### Datos

- **DATA-07**: Exportar, importar y copias de seguridad (#11), con almacenamiento nativo además de `localStorage`

### Generación

- **GEN-09**: Regenerar una sola pregunta
- **GEN-10**: Gramática GBNF por fragmento para forzar el formato JSON
- **GEN-11**: Contrato de lección y validación factual completos (#3), y OCR editable completo (#4)
- **GEN-12**: Hacer fotos directamente desde la app

### Revisión

- **REV-10**: El padre puede ver el tema tal como lo verá el niño
- **REV-11**: Tiempo estimado de la generación

### Accesibilidad

- **A11Y-01**: Auditoría completa de accesibilidad y voz sin conexión garantizada (#12)

## Out of Scope

| Feature | Reason |
|---------|--------|
| Web-app pública e IA en el navegador | Depende de jsDelivr y Hugging Face y rompe la promesa de que nada sale del móvil |
| Cuentas, nube, sincronización, analíticas, anuncios o SDKs de terceros (incluidos los de errores) | Contradicen el valor central y complican el programa de apps familiares; Play vitals cubre los errores |
| Descarga del modelo desde la app o con *fast-follow* | Pide permisos de red y de servicio en primer plano; se eligió *install-time* |
| Botón «Aprobar todo» | Anula la revisión del padre, que es el valor central |
| Puerta parental con una operación matemática o con huella | Un niño de 10 a 16 años resuelve la cuenta, y a menudo su huella está registrada en el móvil |
| Borrar los datos tras N intentos de PIN fallidos | Un niño podría borrar a propósito el trabajo de la familia |
| Recuperar el PIN por email o con una cuenta | No hay cuentas ni red |
| Porcentaje de confianza de la IA | Da una precisión falsa; la revisión del padre es la garantía |
| Chat con el modelo o generación iniciada por el niño | Contenido sin revisar delante del niño |
| Auto Backup de Android | Los datos saldrían del móvil |
| Selector de modelo | Una sola configuración probada en 4 GB de RAM |
| Migrar a AGP 9 o Gradle 9, o a frameworks nuevos | No da tiempo antes del 2026-12-03 |

## Traceability

Qué fase cubre cada requisito (ver `.planning/ROADMAP.md`).

| Requirement | Phase | Status |
|-------------|-------|--------|
| REL-01 | Phase 1 | Pending |
| REL-02 | Phase 1 | Pending |
| REL-03 | Phase 1 | Pending |
| REL-04 | Phase 1 | Pending |
| REL-05 | Phase 1 | Pending |
| REL-06 | Phase 1 | Pending |
| REL-07 | Phase 1 | Pending |
| REL-08 | Phase 1 | Pending |
| MOD-01 | Phase 1 | Pending |
| MOD-02 | Phase 3 | Pending |
| MOD-03 | Phase 3 | Pending |
| GEN-01 | Phase 5 | Pending |
| GEN-02 | Phase 5 | Pending |
| GEN-03 | Phase 3 | Pending |
| GEN-04 | Phase 5 | Pending |
| GEN-05 | Phase 5 | Pending |
| GEN-06 | Phase 5 | Pending |
| GEN-07 | Phase 3 | Pending |
| GEN-08 | Phase 3 | Pending |
| DATA-01 | Phase 2 | Pending |
| DATA-02 | Phase 2 | Pending |
| DATA-03 | Phase 2 | Pending |
| DATA-04 | Phase 2 | Pending |
| DATA-05 | Phase 2 | Pending |
| DATA-06 | Phase 2 | Pending |
| REV-01 | Phase 4 | Pending |
| REV-02 | Phase 4 | Pending |
| REV-03 | Phase 4 | Pending |
| REV-04 | Phase 5 | Pending |
| REV-05 | Phase 4 | Pending |
| REV-06 | Phase 4 | Pending |
| REV-07 | Phase 4 | Pending |
| REV-08 | Phase 4 | Pending |
| REV-09 | Phase 4 | Pending |
| GATE-01 | Phase 4 | Pending |
| GATE-02 | Phase 4 | Pending |
| GATE-03 | Phase 4 | Pending |
| GATE-04 | Phase 4 | Pending |
| GATE-05 | Phase 4 | Pending |
| GATE-06 | Phase 4 | Pending |
| GATE-07 | Phase 4 | Pending |
| GATE-08 | Phase 3 | Pending |
| TEST-01 | Phase 2 | Pending |
| TEST-02 | Phase 6 | Pending |
| TEST-03 | Phase 3 | Pending |
| TEST-04 | Phase 1 | Pending |
| STORE-01 | Phase 6 | Pending |
| STORE-02 | Phase 6 | Pending |
| STORE-03 | Phase 6 | Pending |
| STORE-04 | Phase 6 | Pending |
| STORE-05 | Phase 6 | Pending |
| STORE-06 | Phase 2 | Pending |
| STORE-07 | Phase 4 | Pending |
| STORE-08 | Phase 6 | Pending |
| STORE-09 | Phase 6 | Pending |

**Coverage:**
- v1 requirements: 55 total
- Mapped to phases: 55
- Unmapped: 0 ✓

---
*Requirements defined: 2026-11-13*
*Last updated: 2026-11-13 after roadmap creation (traceability)*
