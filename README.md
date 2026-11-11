# Repasumi — Beta Android + Web

**Del libro al reto.** App de estudio para Primaria y ESO. El alumnado practica con preguntas de opción múltiple, verdadero/falso, completar, relacionar y ordenar. Las preguntas falladas vuelven al final; hay repasos, estrellas y métricas para padres.

> **Estado:** beta técnica, no es una versión de producción ni contiene generación automática con IA local. El APK todavía debe compilarse y probarse en un teléfono real.

## Probar

- **Web:** `web/index.html` (para pruebas reales, alojar en HTTPS).
- **Android:** GitHub → **Actions** → **Compilar APK Android (pruebas)**. Tras una compilación correcta, descargar el artefacto `repasumi-beta-debug-apk` y extraer `app-debug.apk`. Es una **APK debug** para instalación manual, no está firmada para publicar en Play Store.
- También puede abrirse el proyecto en Android Studio (JDK 17 y Android SDK 36).

## Privacidad y límites

- La beta no incluye permiso Android `INTERNET`, cuentas ni servicios externos; no sube fotografías ni métricas. Los datos de progreso se guardan localmente.
- La IA local (Gemini Nano/Apple Foundation Models) y el OCR están **pendientes de integrar**.
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

1. Compilar y verificar la APK debug en un dispositivo Android.
2. OCR local y generación de preguntas con modelos en el dispositivo compatible.
3. App iOS/iPadOS, pruebas de privacidad y políticas para menores.
4. Firmas de producción y publicación en tiendas, tras revisión legal y de calidad.
