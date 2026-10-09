# Catálogos de la interfaz

Fuente: `src/main/resources/static/locales/es.json` y `en.json`. Las claves deben existir en ambos catálogos y conservar los parámetros `{nombre}` cuando proceda. Los parámetros explícitos se interpolan como texto mediante `textContent`, nunca HTML.

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

**A07 continúa parcial**: la navegación compartida también usa claves, pero se han migrado los catálogos público/privado, Mis depósitos/tareas y administración de usuarios/operaciones. El asistente de nueve etapas y sus módulos compartidos de traducciones de metadatos, privacidad, perfiles y transferencias también están migrados. Faltan fichas, restantes pantallas administrativas, colecciones, ayuda/acceso de revisión y demás mensajes. Las páginas aún no migradas mantienen `lang=es`; cada nodo traducido declara su idioma. Solo las páginas completamente migradas cambian el idioma del documento. No se anuncia la plataforma entera como bilingüe. Traducciones y recorridos deben revisarse con usuarios institucionales.

## Evidencia

Seis pruebas de coherencia, protección de controles y orden de assets de catálogos/bindings; prueba Chrome real de cambio sin perder campos, persistencia entre páginas, selección en memoria con localStorage bloqueado, errores locales actualizados y ausencia de solicitudes de autenticación por cambiar idioma. Auditoría axe del login y la cuenta en inglés a320px y del baseline español; no acredita revisión WCAG manual.

Los bindings de texto se colocan únicamente en nodos hoja. Una etiqueta que contiene un input debe tener un span separado con `data-i18n`; el guard de HTML rechaza bindings que borrarían controles o markup. Para atributos, usar `data-i18n-aria-label`/`data-i18n-alt`.

## Contenido de autores y propiedad de bindings

El runtime registra nodos declarativos únicamente durante la carga inicial del HTML estático de confianza. Los nodos dinámicos de la interfaz deben registrarse mediante `uiI18n.set`/`attribute`; no se vuelve a escanear el DOM al cambiar de idioma. Un atributo `data-i18n` dentro de Markdown o contenido añadido después no activa traducción. Los nodos retirados se podan del registro para no acumular toasts.

`uiI18n.plain` desregistra un mensaje antes de colocar texto externo. Los mensajes mixtos deben desregistrar el contenedor y usar spans hoja traducibles, manteniendo intactos enlaces (por ejemplo, contacto de soporte para cuenta restringida). Solo se admiten atributos traducibles `alt`, `aria-label` y `placeholder`, nunca destinos de enlaces, rutas, identificadores o datos científicos.

Administración de usuarios: `node tools/e2e/users-i18n.cjs` verifica cambios de idioma con formularios abiertos, preservación de nombres y ausencia de consultas adicionales. Es un fixture de UI, no prueba de autorización. `t(key, params)` y `set(node, key, params)` aceptan parámetros que se insertan como texto; claves desconocidas o parámetros ausentes fallan explícitamente.

Pruebas adicionales: `node tools/e2e/catalog-history.cjs`, `node tools/e2e/deposit-i18n.cjs`, `node tools/e2e/operations-i18n.cjs`. Cubren cambios es/en sin nuevas consultas ni cambios de filtros, nombres de autor o estado; inventario con fecha válida, perfiles/tareas por códigos, cifras operativas sin reutilizar datos caducados y axe a320px. No sustituyen autorización ni preservación backend.

Asistente: `node tools/e2e/wizard-i18n.cjs` recorre las nueve etapas, alterna idiomas en perfil/vista previa/confirmación y durante una subida local. Preserva metadatos y Markdown originales, no consulta APIs por cambiar idioma ni envía sin confirmación. Controles ocultos se precargan como fixture; no es una auditoría manual de teclado. Los mensajes compuestos usan spans hoja; archivos y rutas nunca se traducen. Claves parametrizadas también funcionan en errores/toasts y atributos accesibles permitidos.

### Módulos de ficha (avance)
`public-versions.js`, `relations.js`, `funding.js` y `creators.js` registran texto propio y mantienen datos originales. Los estados de búsqueda ROR se desregistran antes de montar sugerencias, evitando que un cambio de idioma borre resultados. Validación UI aislada: `node tools/e2e/record-editors-i18n.cjs` (no sustituye pruebas de seguridad del servidor). La ficha principal y otros módulos administrativos siguen pendientes.

`review-access.html/js` incluye español/inglés para acceso temporal; `node tools/e2e/review-access-i18n.cjs` comprueba conservación del contenido y token en memoria, paginado fallido y cambios de idioma sin consultas. No prueba la autorización del backend ni implica que el resto de las fichas esté migrado.

Ficha pública: `node tools/e2e/public-record-i18n.cjs` verifica interfaz bilingüe sin traducir contenido científico ni cambiar la selección de traducción declarada. La cita se conserva como artefacto bibliográfico original; los exportadores mantienen sus códigos de formato. Las fechas de presentación utilizan la configuración regional de la interfaz.

Administración de vocabularios: `node tools/e2e/vocabulary-i18n.cjs` valida etiquetas es/en, preservación de términos/notas/formulario, ausencia de solicitudes por cambio de idioma y aprobación explícita con revisión. Los términos no se traducen automáticamente; la aprobación local no certifica aprobación institucional externa.

Perfiles administrativos: `node tools/e2e/profiles-admin-i18n.cjs` comprueba etiquetas es/en, definición original, requisitos conocidos traducidos por código y etiquetas institucionales desconocidas conservadas, selección de checkbox y aprobación con revisión explícita. No sustituye pruebas del backend ni de snapshots congelados de los datasets.

Colecciones: `node tools/e2e/collections-i18n.cjs` prueba modos público/gestión, datos originales y formularios conservados, revisión en PUT y eliminación explícita de pertenencia. Cambiar idioma no consulta API ni cambia URL/filtros. No prueba permisos reales de servidor ni que un borrador se oculte correctamente en publicación.

Guía de ayuda: `node tools/e2e/help-i18n.cjs` comprueba español/inglés, enlaces y comandos intactos, avisos de política pendiente y limitaciones de descarga/certificación, sin API, axe automático y overflow a 320px. Los párrafos con links/code usan spans hoja, nunca bindings del contenedor. La suite de catálogos cuenta ahora con siete pruebas.

Curación: `node tools/e2e/reviews-i18n.cjs` valida vista previa es/en, metadatos y nota privada intactos, enlace temporal conservado en su control y cero solicitudes por cambio de idioma. Las notas/códigos del servidor no se traducen automáticamente. No prueba publicación real DataCite ni autorización curatorial.
