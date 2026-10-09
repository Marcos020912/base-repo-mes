# Contrato HTTP científico v1

El backend genera `/v3/api-docs` (OpenAPI 3) y Swagger UI. La versión HTTP `1` no es la versión del JAR. Los nombres de esquemas e identificadores de operaciones científicas son explícitos; los identificadores derivan del método y la ruta, no del orden de registro de los controladores.

## Fronteras

- Operaciones privadas: bearer JWT; las comprobaciones reales de cuenta activa/verificada, autoría y roles siguen en el backend. El documento no concede permisos.
- Catálogo y fichas públicas: sin JWT. Las fichas retiradas tienen respuesta 410; sus archivos no pasan a ser públicos por aparecer en el esquema.
- Revisión externa: token temporal en query, separado de la sesión. No compartir URLs de revisión ni guardar tokens en informes.
- Callback ORCID: público, redirección 303; la validación de estado OAuth sigue siendo obligatoria.
- Exportación de citas: `/api/v1/scientific/{id}/citation`, pública solo para versiones PUBLISHED. Texto para estilos/BibTeX/RIS y JSON para CSL-JSON.
- ZIP y archivos: respuesta binaria, Content-Disposition; el MIME del archivo depende del contenido.

## Comprobación local

```bash
node tools/contract/openapi-check.cjs http://127.0.0.1:8090
```

Solo consulta un backend HTTP de loopback explícito. Comprueba presencia y seguridad declarada de todas las fronteras de transporte v1, además de rutas críticas seleccionadas, referencias locales resolubles, operationIds únicos, campos estructurados, separación de notas privadas, descargas y estados específicos. La prueba E2E ejecuta el mismo gate contra un JAR aislado con PostgreSQL y SMTP locales.

Este gate estructural no sustituye pruebas de comportamiento, auditoría de todos los endpoints, generación de clientes ni compatibilidad con consumidores institucionales. Revisar cambios de esquemas/operationIds al regenerar clientes; no se promete compatibilidad con nombres autogenerados anteriores. No contiene credenciales institucionales ni confirma servicios externos.

## Baseline de compatibilidad

`tools/contract/compatibility-check.cjs` compara operaciones, identificadores,
seguridad, parámetros, cuerpos, tipos/formatos y propiedades contra
`v1-baseline.json`. Rechaza eliminaciones/cambios y nuevos campos obligatorios;
acepta campos opcionales nuevos. Gate conservador: no es prueba de compatibilidad
semántica con clientes externos. La E2E lo ejecuta contra el contrato real.

```bash
node tools/contract/test-compatibility.cjs
```

## Rutas legacy y operación

Las cuentas locales USER consultan el catálogo científico o Mis depósitos: no
utilizan los listados legacy raíz ni `search`/`search/data`, que podían presentar
registros visibles por ACL antigua sin comprobar el estado científico. Curadores,
administradores y servicios JWT heredados explícitamente habilitados conservan
sus caminos editoriales/ACL. Las rutas por ID mantienen guards de estado/acceso.
La búsqueda Elasticsearch raw `/api/v1/search` requiere curación/administración.
Health/info permanecen públicos; otros endpoints Actuator expuestos requieren
ADMINISTRATOR/ADMIN/ACTUATOR, no ANONYMOUS ni permisos de escritura genéricos.
Estos cambios son endurecimiento deliberado; comunicar restricciones a clientes
legacy antes de actualizar. La plantilla legacy con autenticación desactivada no
representa la política productiva: `deploy.sh` configura `repo.auth.enabled=true`.
