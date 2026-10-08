# Evaluación privada del depósito

La evaluación editorial se guarda separada de los metadatos públicos. El autor declara `NONE`, `PERSONAL` o `CONFIDENTIAL` únicamente en borrador. Las notas describen medidas de protección: no deben contener datos personales, secretos ni el contenido confidencial. El asistente no las autoguarda en el navegador.

Los depósitos personales/confidenciales solo pueden enviarse con acceso `RESTRICTED`. Curación debe aprobar explícitamente la evaluación antes de la publicación manual o mediante DataCite. Un embargo no sustituye esa restricción porque abre el contenido al finalizar. Los metadatos publicados siguen siendo públicos: el autor y curación deben comprobar también título, resumen y descripción.

La revisión exige una nota y control de versión; una edición concurrente devuelve conflicto. Devolver a borrador revoca la aprobación. Ni administradores ni otros usuarios pueden cambiar la declaración del autor. Solo autor y curación autorizada ven las notas; no se exportan en ficha pública, cita ni eventos públicos.

## Configuración

`repo.privacy.require-assessment=false` conserva compatibilidad con depósitos anteriores sin evaluación. No los clasifica automáticamente como libres de datos sensibles. Establecer `true` exige una declaración antes de enviar/publicar. `deploy.sh` permite configurar y reutilizar esta opción sin modificar una instalación real durante las pruebas.

Las restricciones de una declaración sensible se aplican incluso con la opción desactivada. Esta implementación no sustituye aprobación jurídica, consentimiento, anonimización ni política institucional de publicación.

## API autenticada

- `GET /api/v1/scientific/privacy-policy`: obligatoriedad configurada.
- `GET /api/v1/scientific/{id}/privacy`: evaluación privada autorizada.
- `PUT /api/v1/scientific/{id}/privacy`: declaración del autor en borrador.
- `POST /api/v1/scientific/{id}/privacy/review`: decisión de curación en revisión.

No cambian los permisos de descarga restringida ni los estados de publicación existentes.
