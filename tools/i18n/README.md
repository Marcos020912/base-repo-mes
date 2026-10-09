# Internacionalización de la interfaz

Fuente: `src/main/resources/static/locales/es.json` y `en.json`; bundle determinista
`ui-locales.js`. No editar traducciones en el bundle. Todas las claves y parámetros
`{nombre}` deben coincidir; los parámetros se insertan como texto, nunca HTML.

## Alcance implementado (9 octubre 2026)

Las 19 páginas de la plataforma tienen idioma de documento, título y selector es/en:
acceso, registro, verificación, cuenta, catálogos, Mis depósitos, asistente, fichas
públicas/privadas, curación/revisor, usuarios, operaciones, vocabularios, perfiles,
colecciones, ayuda y autoevaluación interna. Se traducen estados propios de loading/vacío/permiso/error,
modales, acciones, avisos locales, atributos accesibles y módulos compartidos.

Cambiar idioma no consulta otra vez la API, no borra formularios ni traduce contenido
científico, Markdown, nombres/autores, notas privadas o identificadores. Los errores
arbitrarios del backend se conservan originales: no se inventan traducciones de un
fallo por coincidencia de texto. Validación nativa depende del idioma del navegador.
Si localStorage está bloqueado, el selector sigue funcionando en memoria.

## Verificación reproducible

```sh
python3 tools/i18n/build_catalogues.py --check
python3 -m unittest discover -s tools/i18n -p 'test_*.py'
for f in auth-pages-smoke catalog-history collections-i18n deposit-i18n help-i18n operations-i18n profiles-admin-i18n public-record-i18n record-editors-i18n resource-dynamic-i18n resource-static-i18n review-access-i18n reviews-i18n users-i18n vocabulary-i18n wizard-i18n usage-assessment-i18n; do
  node "tools/e2e/$f.cjs" || exit 1
done
```

Nueve pruebas de coherencia verifican claves, parámetros, orden de assets, seguridad
de bindings hoja y cobertura de las 19 páginas. Los 17 fixtures Chrome ejercitan
estados dinámicos, formularios preservados, cambios de idioma sin solicitudes,
recarga de curación, Markdown/datos no traducidos, fallos y restauración.
Los fixtures usan API simulada; la E2E integrada PostgreSQL/SMTP/JAR/restauración
verifica los recorridos reales de acceso, depósito, fichas y administración.

## Convención de mantenimiento

Usar `uiI18n.t/set/error/showError` o `data-i18n` explícito. Un binding de texto va
solo en nodo hoja: una etiqueta que contiene input lleva un span separado.
No escanear DOM para sustituir textos, no traducir automáticamente contenido del
usuario. Limpiar los bindings de filas retiradas y vaciar contenedores antes de
registrar loading/error sobre ellos; el guard evita borrar controles/markup.

La cobertura técnica no sustituye revisión institucional de la terminología inglesa
ni pruebas manuales con personas y tecnologías de asistencia. Los resultados axe
son automáticos, no una certificación WCAG.
