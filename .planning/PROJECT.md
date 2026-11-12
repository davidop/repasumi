# Repasumi

## What This Is

Repasumi («Del libro al reto») es una app Android de estudio para alumnado de Primaria y ESO (6–16 años). La familia hace fotos del libro de texto; la app extrae el texto con OCR local y un modelo de lenguaje pequeño que se ejecuta en el propio móvil genera preguntas de cinco tipos (opción múltiple, verdadero/falso, completar, relacionar y ordenar), que el padre revisa antes de que el niño las use para repasar con práctica, repaso espaciado, tarjetas y modo examen. Nada sale del dispositivo.

## Core Value

Una familia hace fotos del libro y obtiene, sin que nada salga del móvil, preguntas fiables y revisadas por el padre para que el niño repase.

## Business Context

- **Customer**: familias con hijos en Primaria y ESO en España (usuario final: el niño; quien instala, crea contenido y aprueba: el padre o la madre).
- **Revenue model**: sin definir para la 1.0 (gratis, sin anuncios ni compras en la app).
- **Success metric**: 1.0 publicada en Google Play antes del 2026-12-03, con prueba cerrada en 12 o más familias en la que estas generan y revisan temas reales sin ayuda.
- **Strategy notes**: milestone de GitHub «1.0 · Google Play»; decisiones en los issues #2, #14 y #16.

## Requirements

### Validated

<!-- Inferido del código existente (v0.5.0-study-preview, commit 0938eec). -->

- ✓ App Android con WebView que sirve una SPA de un solo archivo (`web/index.html` = `app/src/main/assets/index.html`), sin permiso INTERNET — existing
- ✓ Cinco tipos de ejercicio (mc, tf, fill, match, order) con corrección y explicación — existing
- ✓ OCR local con ML Kit sobre 1–30 fotos — existing
- ✓ Generación local con llama.cpp (JNI) y Qwen2.5-0.5B-Instruct Q4_K_M, con la importación del GGUF verificada por SHA-256 — existing
- ✓ Generación de preguntas nuevas de práctica a partir de un tema guardado — existing
- ✓ Repaso espaciado (intervalos de 0/1/3/7/14/30/60 días; si se falla, vuelve a las 4 h), repaso rápido y tarjetas de memoria — existing
- ✓ Modo examen sin feedback y con revisión final de cada pregunta (PR #15) — existing
- ✓ Varios alumnos locales, estrellas, racha, métricas e informe de 7 días para padres — existing
- ✓ Marcar preguntas erróneas (🚩) — existing
- ✓ Lectura en voz alta (TTS es-ES), texto grande A+/A− y movimiento reducido — existing
- ✓ Temas de ejemplo integrados (Relieve de España, ESO 2.º Materia, Primaria 5.º Lengua) — existing
- ✓ CI: compilación del APK de debug y despliegue de `web/` en GitHub Pages — existing

### Active

<!-- Alcance de la 1.0 (milestone «1.0 · Google Play»). -->

- [ ] Build de producción `com.repasumi`: AAB firmado (upload key en GitHub Secrets, Play App Signing) generado por CI (#16)
- [ ] El modelo GGUF llega con Play Asset Delivery, se verifica por SHA-256 antes de usarlo y no hay que descargarlo ni importarlo a mano (#16)
- [ ] La generación es estable en Android 10+, ARM64 y 4 GB de RAM: cancelable, con progreso por etapas, sin doble ejecución, sobrevive a la rotación y avisa claramente si el dispositivo no llega al mínimo (#2, #1)
- [ ] La generación cubre todo el texto de las fotos (por fragmentos y no solo los primeros 1200 caracteres) y valida que cada «Fuente» cite una foto real que contenga el dato (#2)
- [ ] Persistencia con versión de esquema, migraciones ordenadas, recuperación si los datos se corrompen e identificadores estables por pregunta (#6)
- [ ] El padre revisa, edita, descarta y aprueba cada pregunta antes de que el tema esté disponible para el niño, sin perder correcciones (#5, mínimo)
- [ ] Control parental con PIN del padre en «Padres», «Añadir» (fotos, generar, revisar) y en las acciones delicadas (borrar, exportar, enlaces externos)
- [ ] Pruebas automatizadas mínimas en CI: lógica de dominio en JS (calificación, repaso, migraciones, validación) y un flujo de humo de principio a fin (#13, mínimo)
- [ ] Cumplimiento de Google Play y del programa de apps familiares: LICENSE (todos los derechos reservados), política de privacidad en GitHub Pages, formulario de seguridad de datos, público objetivo de 6–16 años y ficha de la tienda (#14)
- [ ] GitHub Pages publica solo una portada y la política de privacidad; la web-app y el prototipo de IA del navegador dejan de publicarse

### Out of Scope

- Notificaciones de repaso y agenda configurable (#7) — v1.1; no bloquea el lanzamiento
- Lo que queda del modo examen: pistas en las parejas, nota reproducible, dificultad por etapa (#8) — v1.1
- Perfiles familiares avanzados (#9), gamificación (#10), exportar/importar y copias (#11) — v1.1
- Auditoría completa de accesibilidad y voz offline garantizada (#12) — v1.1; se mantiene lo que ya hay
- Contrato de lección y validación factual completos (#3) y OCR editable completo (#4) — v1.1, salvo lo mínimo que necesiten la validación de fuentes y el editor
- Web-app pública e IA en el navegador — fuera de la 1.0: depende de jsDelivr y Hugging Face y rompe la promesa de que nada sale del móvil
- Cuentas, nube, sincronización, analíticas, anuncios o SDKs de terceros — contradicen el valor central y complican el programa de apps familiares
- Descarga del modelo desde la app (permiso INTERNET) — se descartó en favor de Play Asset Delivery

## Context

- **Estado del código:** v0.5.0-study-preview. Mapa completo en `.planning/codebase/` (STACK, ARCHITECTURE, STRUCTURE, CONVENTIONS, TESTING, INTEGRATIONS, CONCERNS).
- **Arquitectura:** shell híbrido. Una SPA vanilla JS de un solo archivo (renderizado imperativo con `innerHTML`, un único manejador `data-action`) habla con Java (`MainActivity.AndroidBridge`, `NativeLearningEngine` para OCR y `NativeGguf` para el modelo) mediante `window.repasumiNativeEvent`, y Java llama a llama.cpp por JNI (`local_llm.cpp`). Persistencia en `localStorage` con la clave `estudia-en-el-bus-v1`.
- **Restricción estructural:** `web/index.html` y `app/src/main/assets/index.html` deben ser idénticos byte a byte (`tools/verify_source.py`).
- **Problemas conocidos que bloquean la 1.0 (CONCERNS.md):** no hay control parental; la generación solo usa unos 1200 caracteres de OCR; las «Fuente» no se validan; la rotación destruye el trabajo en curso; `render()` borra las ediciones de revisión; un almacén corrupto deja de guardar sin avisar; las estadísticas van por índice de pregunta; enlace externo a Hugging Face sin control parental; inyección de prompt mediante tokens especiales en el OCR; no hay `.gitignore`, firma ni wrapper de Gradle; no hay pruebas.
- **Trabajo en paralelo:** el usuario a veces tiene varias sesiones de Claude sobre este repo. Cada issue o PR se trabaja en su propio `git worktree` y nunca se cambia de rama en el working tree principal.
- **Prueba física:** Xiaomi 12T (#1) y un dispositivo de 4 GB de RAM.

## Constraints

- **Timeline**: publicación en Google Play antes del 2026-12-03, contando con que la revisión de las apps familiares tarda varios días — por eso el alcance de la 1.0 está cerrado.
- **Privacy**: ningún dato (fotos, OCR, respuestas o nombres) sale del dispositivo; sin permiso INTERNET — es el valor central y la base de la política de privacidad.
- **Platform**: Android 10+ (API 29), solo ARM64, 4 GB de RAM como mínimo; la librería nativa es ARM64 y el modelo de 0,5B necesita memoria.
- **Store policy**: programa de apps familiares de Google Play (público de 6 a 16 años) — exige control parental para enlaces externos y compras, política de privacidad y cero SDKs no certificados.
- **Identity**: el `applicationId` de producción `com.repasumi` es permanente una vez subido.
- **Tech stack**: se mantienen Java + WebView + SPA vanilla + llama.cpp; nada de frameworks nuevos en la 1.0 — no hay tiempo para migraciones.
- **License**: código público con todos los derechos reservados.
- **Workflow**: GSD + GitHub; un PR por issue en su propio worktree, con CI en cada PR.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Canal de lanzamiento: Google Play | Más alcance y actualizaciones automáticas; cuenta personal anterior a noviembre de 2023 (sin la prueba obligatoria de 14 días) | — Pending |
| Modelo vía Play Asset Delivery | Sin permiso INTERNET, sin importación manual y 100 % offline | — Pending |
| `applicationId` = `com.repasumi` | Coincide con la marca; elimina el antiguo `estudiaenelbus.dev` | — Pending |
| Público declarado de 6 a 16 años (programa de apps familiares) | Es el uso real de la app; no hay anuncios ni SDKs, así que es asumible | — Pending |
| Control parental con PIN en Padres, Añadir y acciones delicadas | Lo exige el programa de apps familiares; el niño solo estudia y el padre crea y aprueba | — Pending |
| GitHub Pages solo con portada y privacidad | La web-app dependía de jsDelivr y Hugging Face, lo que contradice la privacidad | — Pending |
| Licencia con fuente visible y todos los derechos reservados | Rápido; protege el producto sin pasar el repo a privado ni perder Pages | — Pending |
| Alcance de la 1.0: #16, #2 (+#1), #6, #5 mínimo, #13 mínimo, #14 y control parental | Lanzar en 3 semanas; el resto va a la 1.1 | — Pending |
| Revisión final en el modo examen (PR #15) | El examen prometía un resultado que no mostraba | ✓ Good |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-11-12 after initialization*
