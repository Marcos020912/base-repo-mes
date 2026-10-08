# Contrato HTTP científico v1

El backend genera `/v3/api-docs` (OpenAPI 3) y Swagger UI. La versión HTTP `1` no es la versión del JAR. Los nombres de esquemas e identificadores de operaciones científicas son explícitos; los identificadores derivan del método y la ruta, no del orden de registro de los controladores.

## Fronteras

- Operaciones privadas: bearer JWT; las comprobaciones reales de cuenta activa/verificada, autoría y roles siguen en el backend. El documento no concede permisos.
- Catálogo y fichas públicas: sin JWT. Las fichas retiradas tienen respuesta 410; sus archivos no pasan a ser públicos por aparecer en el esquema.
- Revisión externa: token temporal en query, separado de la sesión. No compartir URLs de revisión ni guardar tokens en informes.
- Callback ORCID: público, redirección 303; la validación de estado OAuth sigue siendo obligatoria.
- Exportación de citas: `/api/v1/scientific/{id}/citation`, autenticada. Texto para estilos/BibTeX/RIS y JSON para CSL-JSON.
- ZIP y archivos: respuesta binaria, Content-Disposition; el MIME del archivo depende del contenido.

## Comprobación local

```bash
node tools/contract/openapi-check.cjs http://127.0.0.1:8090
```

Solo consulta un backend HTTP de loopback explícito. Comprueba presencia y seguridad declarada de rutas seleccionadas, referencias locales resolubles, operationIds únicos, campos estructurados, separación de notas privadas, descargas y estados específicos. La prueba E2E ejecuta el mismo gate contra un JAR aislado con PostgreSQL y SMTP locales.

Este gate estructural no sustituye pruebas de comportamiento, auditoría de todos los endpoints, generación de clientes ni compatibilidad con consumidores institucionales. Revisar cambios de esquemas/operationIds al regenerar clientes; no se promete compatibilidad con nombres autogenerados anteriores. No contiene credenciales institucionales ni confirma servicios externos.
