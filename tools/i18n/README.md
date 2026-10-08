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

Login, registro, verificación de correo y cuenta personal: textos estáticos, mensajes locales, título, documento `lang` y selector persistente español/inglés. El idioma inicial es español; el cambio no recarga, no altera credenciales ni borra formularios. Si el almacenamiento local está bloqueado, funciona en memoria.

Los mensajes arbitrarios del servidor conservan su texto original: no se traducen por coincidencias ni se inventa un diagnóstico. Los códigos/roles/estados de API y los metadatos científicos no cambian de idioma con la interfaz. Los mensajes nativos de validación siguen la configuración del navegador.

**A07 continúa parcial**: la navegación compartida también usa claves, pero falta migrar el contenido de catálogo, depósito, fichas, administración, modales, transferencias y demás mensajes. Las páginas aún no migradas mantienen `lang=es`; cada nodo traducido declara su idioma. Solo las páginas completamente migradas cambian el idioma del documento. No se anuncia la plataforma entera como bilingüe. Traducciones y recorridos deben revisarse con usuarios institucionales.

## Evidencia

Cuatro pruebas de coherencia, protección de controles y orden de assets de catálogos/bindings; prueba Chrome real de cambio sin perder campos, persistencia entre páginas, selección en memoria con localStorage bloqueado, errores locales actualizados y ausencia de solicitudes de autenticación por cambiar idioma. Auditoría axe del login y la cuenta en inglés a320px y del baseline español; no acredita revisión WCAG manual.

Los bindings de texto se colocan únicamente en nodos hoja. Una etiqueta que contiene un input debe tener un span separado con `data-i18n`; el guard de HTML rechaza bindings que borrarían controles o markup. Para atributos, usar `data-i18n-aria-label`/`data-i18n-alt`.

## Contenido de autores y propiedad de bindings

El runtime registra nodos declarativos únicamente durante la carga inicial del HTML estático de confianza. Los nodos dinámicos de la interfaz deben registrarse mediante `uiI18n.set`/`attribute`; no se vuelve a escanear el DOM al cambiar de idioma. Un atributo `data-i18n` dentro de Markdown o contenido añadido después no activa traducción. Los nodos retirados se podan del registro para no acumular toasts.

`uiI18n.plain` desregistra un mensaje antes de colocar texto externo. Los mensajes mixtos deben desregistrar el contenedor y usar spans hoja traducibles, manteniendo intactos enlaces (por ejemplo, contacto de soporte para cuenta restringida). Solo se admiten atributos traducibles `alt` y `aria-label`, nunca destinos de enlaces, rutas, identificadores o datos científicos.

Administración de usuarios: `node tools/e2e/users-i18n.cjs` verifica cambios de idioma con formularios abiertos, preservación de nombres y ausencia de consultas adicionales. Es un fixture de UI, no prueba de autorización. `t(key, params)` y `set(node, key, params)` aceptan parámetros que se insertan como texto; claves desconocidas o parámetros ausentes fallan explícitamente.
