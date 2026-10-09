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

## Segunda barrera: transacción primaria (avance posterior)

`ScientificResourceWriteLock` requiere una transacción existente y writable; no abre
una transacción separada ni acepta un uso sin transacción. Bloquea la fila `DataResource`
con `PESSIMISTIC_WRITE` antes de leer la ficha/estado y modificar tablas científicas.
Esta fila existe incluso cuando aún no existe `ScientificRecord`, evitando el hueco de
bloquear solo la ficha opcional. La base libera el bloqueo al commit/rollback primario,
no cuando se pierde la sesión del pool de leases.

Integrado en edición científica y aplicación de perfil, envío, devolución a borrador,
publicación **manual**, retirada, derivación de versiones, autores/afiliaciones,
financiación, relaciones y escritura/revisión de privacidad. La derivación toma ambos
IDs en orden para evitar bloquearlos en orden inverso. Autoría se verifica antes de
bloquear en los endpoints de autor; los endpoints curatoriales mantienen su seguridad
de método. El bloqueo no concede permisos.

La espera de lock está acotada a 2000ms. PostgreSQL usa `set_config('lock_timeout',
'2000ms',true)` dentro de la transacción, ya que no se debe confiar solo en el hint
portable JPA; esto limita espera de bloqueo, **no la duración completa de la transacción**.
Contención produce 409 seguro y rollback de la operación. El timeout se reinicia al
terminar la transacción; no cambia la configuración global de PostgreSQL.

### Alcance no completado

Los controladores legacy de archivos y el flujo DOI automatizado aún no usan esta
segunda barrera. Siguen bajo el gate normal; la muerte de su sesión dedicada durante
una subida/llamada externa sigue siendo un caso pendiente. Integrar DOI dentro de sus
transacciones cortas conservando commits de intención antes de llamadas externas;
**no envolver todo el flujo en una transacción HTTP larga**. Auditar commits, caché JPA,
rollback de filesystem/versiones y respuestas HTTP antes de extender la barrera legacy.
No afirmar F15 completo ni publicación inmutable bajo todos los fallos por esta mejora.

### Prueba adicional

E2E con una sesión PostgreSQL independiente que bloquea únicamente `data_resource`
(sin advisory lock) confirma que el gate de petición pasa pero la operación científica
no puede escribir hasta que la transacción primaria lo permita. Se verifican 409 con
espera acotada, ausencia de cambios, independencia de otro dataset y recuperación tras
rollback. Esto no es todavía una prueba de dos JVM ni de matar una sesión mientras
una subida multipart sigue activa.
