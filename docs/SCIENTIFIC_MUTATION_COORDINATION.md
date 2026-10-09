# Coordinación de mutaciones científicas

Rama `develop-reduniv`, 2026-10-08. No autoriza despliegue ni cierra por sí sola F15.

## Qué está implementado

Las peticiones autenticadas POST/PUT/PATCH/DELETE sobre un recurso existente en
`/api/v1/scientific/{id}/...` y `/api/v1/dataresources/{id}/...` pasan por
`ResourceMutationGateFilter`, después de JWT y antes del control de autoría/estado.
Un autor, personal curatorial/administrador o identidad de servicio confiada debe
adquirir una lease del recurso antes de entrar en el controlador. Los demás usuarios
no toman lease ni pueden observar el estado ocupado de un dataset privado; siguen
recibiendo el rechazo normal de los controles existentes. La lease **no otorga permisos**.

En PostgreSQL, `pg_try_advisory_lock(bigint)` usa la misma clave en todas las
instancias: los ocho primeros bytes SHA-256, interpretados como entero con signo
big-endian, de `reduniv:resource-mutation:` seguido del ID canónico UTF-8.
Una colisión solo serializaría dos recursos, no autorizaría acceso a ninguno.
Se usan bloqueos de sesión, independientes de commits/rollbacks editoriales;
se liberan explícitamente o al terminar la sesión. Véase la
[documentación oficial de PostgreSQL 18](https://www.postgresql.org/docs/18/explicit-locking.html#ADVISORY-LOCKS).

El pool de leases está separado del pool JPA: no retiene las conexiones editoriales
ni convierte el flujo DOI recuperable en una transacción larga. Ocupación devuelve
409 con mensaje seguro, `Retry-After: 1` y `Cache-Control: no-store`; indisponibilidad
SQL/pool devuelve 503 sin detalles del driver. Al no poder confirmar unlock, la
conexión se expulsa del pool para no reutilizar una sesión bloqueada. Cerrar una
lease repetidamente es inocuo.

En H2 solo se admite el uso embebido en una instancia. TCP/SSL remoto y AUTO_SERVER
no pueden activar silenciosamente un mutex local. Un despliegue multiinstancia
requiere PostgreSQL. Los filtros se registran solo en `SecurityFilterChain` y usan
la ruta servlet canónica, evitando eludir controles con URI codificada.

## Operación y capacidad

- Configuración: `repo.mutations.lock-pool-size=4`, rango PostgreSQL 1–32.
- Utiliza `spring.datasource.url/username/password`; no configurar un destino
  diferente mediante overrides Hikari independientes.
- Presupuestar conexiones por instancia: pool JPA + pool de leases + administración,
  migraciones y backups. Cuatro escrituras largas de datasets diferentes ocupan los
  cuatro slots por defecto; no es un límite de tamaño ni un mecanismo de compresión.
- El rol PostgreSQL necesita ejecutar `pg_try_advisory_lock(bigint)` y
  `pg_advisory_unlock(bigint)`. Si infraestructura revoca esas funciones, se falla
  cerrado; no quitar el gate como solución.
- Todas las instancias deben participar con el mismo algoritmo. Versiones antiguas,
  escrituras SQL manuales y herramientas externas no coordinadas quedan fuera de
  esta garantía. Actualizar instancias en mantenimiento, no mezclar versiones sin gate.
- Los handlers actuales son síncronos. Un futuro escritor asíncrono necesita extender
  el ciclo de vida de la lease hasta completar su trabajo, no hasta devolver HTTP.

## Evidencia local

- Pruebas de mutex H2 entre hilos, recursos independientes, cierre repetido,
  rechazo de bases no soportadas, gate de verbos/rutas y liberación ante IOException.
- Pruebas SQL simuladas: unlock correcto, conflicto, error seguro y eviction.
- Suite Java final: 59 suites, 1331 casos, cero fallos/errores/omitidos,
  `/tmp/reduniv-mutation-final-full-java.log`.
- E2E PostgreSQL/SMTP/Chrome/restauración: sesión SQL independiente mantiene el lock;
  nueve mutaciones no entran en controladores, otro dataset sigue editable. Una
  subida multipart real incompleta mantiene lease y bloquea envío a revisión.
  Al terminar la subida se libera y el estado continúa DRAFT. Las URI codificadas
  tampoco permiten borrar una versión publicada como administrador.
  `/tmp/reduniv-mutation-final-e2e.log`, terminal 0, sin errores JavaScript finales.
- Esto no equivale a prueba de dos JVM desplegadas, fallo de red ni certificación.

## Riesgo todavía abierto: pérdida de sesión durante una escritura

El bloqueo es cooperativo. La muerte de la sesión dedicada libera el lock en
PostgreSQL aunque el handler HTTP todavía esté ejecutándose con otra conexión.
La coordinación normal probada no es un fencing token transaccional. Para cerrar
F15 de forma robusta faltan guardas transaccionales sobre la fila del recurso/estado,
serialización dentro de las transacciones de datos, prueba de pérdida de sesión
mientras hay una subida activa y auditoría de escritores internos/externos.
No dar publicado inmutable por acreditado únicamente con este mutex.
