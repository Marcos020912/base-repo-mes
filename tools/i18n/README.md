# Catálogos de la interfaz

Fuente: `src/main/resources/static/locales/es.json` y `en.json`. Las claves deben existir en ambos catálogos y conservar los parámetros `{nombre}` cuando proceda. Actualmente no hay interpolación de datos de autor: todo texto se inserta mediante `textContent`, no HTML.

```sh
python3 tools/i18n/build_catalogues.py
python3 tools/i18n/build_catalogues.py --check
python3 -m unittest discover -s tools/i18n -p 'test_*.py'
node tools/e2e/auth-pages-smoke.cjs
```

`ui-locales.js` es un artefacto generado determinista. No editar traducciones allí. La lógica usa claves explícitas `uiI18n.t/set/error/showError`; elementos declarativos usan `data-i18n`. No se buscan textos visibles para reemplazarlos automáticamente.

## Alcance actual

Login, registro y verificación de correo: textos estáticos, mensajes locales, título, documento `lang` y selector persistente español/inglés. El idioma inicial es español; el cambio no recarga, no altera credenciales ni borra formularios. Si el almacenamiento local está bloqueado, funciona en memoria.

Los mensajes arbitrarios del servidor conservan su texto original: no se traducen por coincidencias ni se inventa un diagnóstico. Los códigos/roles/estados de API y los metadatos científicos no cambian de idioma con la interfaz. Los mensajes nativos de validación siguen la configuración del navegador.

**A07 continúa parcial**: falta migrar catálogo, depósito, fichas, cuenta, administración, modales, transferencias y demás mensajes. No se anuncia la plataforma entera como bilingüe. Traducciones y recorridos deben revisarse con usuarios institucionales.

## Evidencia

Dos pruebas de coherencia de catálogos/bindings; prueba Chrome real de cambio sin perder campos, persistencia entre páginas, selección en memoria con localStorage bloqueado, errores locales actualizados y ausencia de solicitudes de autenticación por cambiar idioma. Auditoría axe del login inglés a320px y del baseline español; no acredita revisión WCAG manual.
