# Repasumi — Beta Android + Web

**Del libro al reto.** App de estudio para Primaria y ESO. El alumnado practica con preguntas de opción múltiple, verdadero/falso, completar, relacionar y ordenar. Las preguntas falladas vuelven al final; hay repasos, estrellas y métricas para padres.

> **Estado:** beta técnica con OCR ML Kit y motor GGUF local llama.cpp ARM64 (Xiaomi 12T). Compilación y ejecución real en hardware pendientes de verificación; no es una versión de producción.

## Probar

- **Web:** `web/index.html` (para pruebas reales, alojar en HTTPS).
- **Android:** GitHub → **Actions** → **Compilar APK Android (pruebas)**. Tras una compilación correcta, descargar el artefacto `repasumi-beta-debug-apk` y extraer `app-debug.apk`. Es una **APK debug** para instalación manual, no está firmada para publicar en Play Store.
- También puede abrirse el proyecto en Android Studio (JDK 17 y Android SDK 36).

## Privacidad y límites

- La beta Android no solicita permiso `INTERNET`, no incluye cuentas ni backend de inferencia: no sube fotografías ni métricas. Los datos de progreso se guardan localmente.
- **OCR:** ML Kit Text Recognition (modelo Latin bundled); seleccionar 1–6 imágenes en Añadir. Lee textos de imágenes en el propio móvil, los muestra para revisión y no persiste fotografías ni OCR.
- **Generación:** llama.cpp nativo (JNI, ARM64) con modelo GGUF importado mediante el selector Android; inferencia 100 % local, sin AICore ni permiso INTERNET. El modelo no se incluye en la APK.\n- **Modelo piloto:** [Qwen2.5-0.5B-Instruct GGUF Q4_K_M oficial](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/blob/9217f5db79a29953eb74d5343926648285ec7e67/qwen2.5-0.5b-instruct-q4_k_m.gguf), 491.400.032 bytes, SHA-256 `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db`. Descarga el archivo desde el navegador del móvil, abre Repasumi → Añadir → Importar modelo GGUF. La app verifica SHA-256 y copia el archivo al almacenamiento privado.\n- **Calidad:** la validación formal JSON no sustituye la verificación de los hechos. El padre debe revisar cada pregunta y su fuente antes de utilizarla con menores.
- No se incluyen fotos de libros de terceros ni datos reales de menores.
- El nombre y la mascota son **provisionales** mientras se comprueba la disponibilidad registral de la marca.

## Estructura

```
app/                         Android (WebView embebida + recursos)
web/index.html               Aplicación web de prueba
brand/                       Identidad visual
.github/workflows/           Compilación automatizada del APK
tools/verify_source.py       Comprobaciones estáticas
docs/                        Plan y verificación de marca
```

## Licencias y contribuciones

El repositorio es público para facilitar el desarrollo, pero **publicar código no concede automáticamente una licencia de reutilización**. Aún no se ha elegido licencia de código abierto. No introducir información personal, fotografías de libros protegidos ni secretos en commits o incidencias.

## Próximos hitos

1. Instalar la APK debug en un móvil real y verificar la lectura OCR con fotos propias.
2. Importar GGUF en Xiaomi 12T y probar generación local; medir latencia, memoria, temperatura y validez de JSON; revisar manualmente los hechos.
3. Diseñar pruebas automatizadas de OCR y calidad factual; luego iOS/iPadOS y privacidad infantil.
4. Firmas de producción y publicación en tiendas, tras revisión legal y de calidad.

## Compilar con llama.cpp

La compilación usa **llama.cpp v0.9.0** y Android NDK/CMake. GitHub Actions descarga la versión fijada en `vendor/llama.cpp` durante la compilación (solo en CI). Para compilar localmente:

```bash
mkdir -p vendor
git clone --depth 1 --branch v0.9.0 https://github.com/ggml-org/llama.cpp.git vendor/llama.cpp
gradle :app:assembleDebug
```

La aplicación instalada **no necesita permiso INTERNET**. El GGUF se descarga de forma independiente con el navegador y se importa localmente. El código no registra el OCR ni las preguntas en servidores.

**Limitaciones:** modelo de 0,5B puede devolver JSON inválido o introducir errores factuales; el flujo falla de forma visible si un bloque no es JSON válido y exige revisión humana antes de guardar. No hay garantía de generación correcta de 14 preguntas en todas las páginas. La ejecución en Xiaomi 12T requiere pruebas físicas.
