# Administración local de vocabularios

Solo ADMINISTRATOR puede consultar el registro de propuestas y proponer/aprobar listas desde `vocabulary-admin.html`. El endpoint de sugerencias existente conserva su forma: `licenses` y `disciplines`.

Cada lista tiene una propuesta pendiente separada de la última lista aprobada. Hasta la primera aprobación se utilizan `repo.scientific.licenses` y `repo.scientific.disciplines`. La aprobación explícita activa la lista persistente; guardar otra propuesta no la sustituye. El registro guarda revisión, motivo, actor y fechas de la última propuesta/aprobación. No constituye un historial exhaustivo ni una certificación institucional. No exige dos administradores distintos: esa política debe acordarse antes de imponerla.

`repo.scientific.strict-vocabulary` continúa controlando si las listas son sugerencias o una validación obligatoria. No puede cambiarse desde este formulario. Los valores existentes sin modificar se conservan aunque dejen de formar parte del vocabulario; no se modifica ni migra automáticamente ningún dataset publicado.

- Entre 1 y 100 términos por lista; duplicados sin distinción de mayúsculas rechazados.
- Máximo 100 caracteres por licencia y 255 por disciplina; controles/vacíos rechazados.
- Revisión obligatoria en cambios de registros existentes y aprobación; un cliente obsoleto recibe conflicto y debe recargar, no sobrescribir.
- La lista aprobada permanece en PostgreSQL y entra en el backup/restauración ordinarios.
- Migración idempotente: `docs/migrations/2026-09-scientific-records.sql`; no ejecutada contra producción.

No elimina las políticas institucionales de revisión de licencias, identificadores o perfiles de metadatos. Los perfiles son un módulo independiente implementado en `METADATA_PROFILES.md`; su definición y aprobación institucional no se sustituyen por estas listas.
