# Cierre de los cuatro bloques técnicos

Rama exclusiva: `develop-reduniv`. Sin merge, push ni cambios de producción.

| Bloque | Implementación | Evidencia |
|---|---|---|
| Rutas antiguas | Transacciones/fence DRAFT en CRUD/PID/raw content/content metadata; prioridad de If-Match/readonly conservada; callback ORCID protegido después del HTTP. | Regresión Java existente, rowlock SQL, pérdida de lease multipart y prueba real de dos JVM. |
| Elasticsearch | Cola SQL durable en el commit de cada modificación, lectura del estado actual bloqueado, snapshots separados y reintento idempotente; borrado del documento después de borrar dataset. | Cinco tests worker y dos tests de integración JPA: paginación101, sin mutar padre gestionado, commit/rollback y fallo/reintento. |
| DOI | Claim familiar durable, runner único incluso mismo recurso, retorno a borrador bloqueado mientras pendiente; transacciones cortas sin I/O remoto. | Tests de estado cambiado durante reserva, familia competidora, runner duplicado, fallo remoto y conciliación sin nuevo DOI. |
| Auditoría/i18n | Cobertura de18 páginas y1272 claves es/en; revisión de escritores científicos/legacy/ORCID, pruebas entre JVM. | Ocho tests catálogos,16 fixtures Chrome e integración PostgreSQL/SMTP/restauración. |

## Operación y límites explícitos

- Migración idempotente: `docs/migrations/2026-09-scientific-records.sql`; añade cola y
  claims, no elimina ni recrea datos. Aplicar antes de arrancar si usa ddl-auto=validate.
- ES caído: no se pierde la escritura SQL; ver `SEARCH_INDEX_RECOVERY.md`.
- Caída abrupta durante DOI: ejecución queda bloqueada por seguridad; recuperar
  únicamente tras verificar detenidos los runners anteriores, según
  `DOI_CONCURRENCY_RECOVERY.md`. No caducar a ciegas una llamada remota incierta.
- El índice es eventualmente consistente. No se promete atomicidad distribuida
  entre SQL, filesystem, Elasticsearch y DataCite.
- Continúan fuera del cierre de código: aceptación DataCite institucional, HAProxy
  real, terminología inglesa, accesibilidad manual y aprobaciones de políticas.
  La matriz completa contiene otros requisitos; este documento no certifica toda
  la plataforma ni reemplaza las decisiones institucionales.

Los logs finales y conteos se consignan en `REDUNIV_EXECUTION_PLAN.md` después de
terminar la ejecución de verificación; no se consideran aprobadas corridas en curso.
