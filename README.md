# Repasumi — Beta Android + Web

**Del libro al reto.** App de estudio para Primaria y ESO. El alumnado practica con preguntas de opción múltiple, verdadero/falso, completar, relacionar y ordenar. Las preguntas falladas vuelven al final; hay repasos, estrellas y métricas para padres.

> **Estado:** beta técnica compilada con OCR local y generación mediante Gemini Nano en dispositivos compatibles. La inferencia real todavía requiere pruebas en hardware; no es una versión de producción.

## Probar

- **Web:** `web/index.html` (para pruebas reales, alojar en HTTPS).
- **Android:** GitHub → **Actions** → **Compilar APK Android (pruebas)**. Tras una compilación correcta, descargar el artefacto `repasumi-beta-debug-apk` y extraer `app-debug.apk`. Es una **APK debug** para instalación manual, no está firmada para publicar en Play Store.
- También puede abrirse el proyecto en Android Studio (JDK 17 y Android SDK 36).

## Privacidad y límites

- La beta Android no solicita permiso `INTERNET`, no incluye cuentas ni backend de inferencia: no sube fotografías ni métricas. Los datos de progreso se guardan localmente.
- **OCR:** ML Kit Text Recognition (modelo Latin bundled); seleccionar 1–6 imágenes en Añadir. Lee textos de imágenes en el propio móvil, los muestra para revisión y no persiste fotografías ni OCR.
- **Generación:** Gemini Nano mediante ML Kit Prompt API beta4. La app comprueba `AVAILABLE` / `DOWNLOADABLE` / `UNAVAILABLE`. Si `DOWNLOADABLE`, puede descargar los archivos del modelo a través de los servicios Android; las fotos no se transmiten. La IA genera trece preguntas en tres lotes; el JSON se debe revisar antes de guardarse.
- **Compatibilidad:** no todos los Android tienen AICore/Gemini Nano. En los demás, el OCR sigue disponible, y el usuario puede utilizar la importación manual JSON. iOS/Apple Foundation Models siguen pendientes.
- **Calidad:** la validación formal JSON no sustituye la verificación de los hechos. El padre debe revisar cada pregunta y su fuente antes de utilizarla con menores.
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
2. Probar disponibilidad, posible descarga y generación de Gemini Nano en un dispositivo compatible; validar las preguntas manualmente.
3. Diseñar pruebas automatizadas de OCR y calidad factual; luego iOS/iPadOS y privacidad infantil.
4. Firmas de producción y publicación en tiendas, tras revisión legal y de calidad.
