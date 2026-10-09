# Indexación recuperable

`search_index_tasks` es una cola SQL persistente de intenciones, no una copia de metadatos.
Se guarda dentro de la misma transacción que la modificación; un rollback no genera trabajo.
Cada cinco segundos el worker toma hasta 50 tareas. El worker bloquea la tarea y la fila
primaria, reconstruye **el estado actual comprometido**, copia los objetos para no modificar
entidades JPA y actualiza Elasticsearch. La tarea se elimina solo después del acuse externo.
Si falla Elasticsearch o se interrumpe el commit SQL, permanece y se reintenta: entrega
al menos una vez, con operaciones idempotentes. Los borrados se reconstruyen como eliminación
del documento cuando ya no existe el recurso SQL. Las tareas no tienen FK al dataset.

Dos workers usan locks SQL, no un mutex de memoria. Los lectores pueden observar un retraso
breve del índice; no se promete una transacción distribuida PostgreSQL/Elasticsearch.
El cliente existente limita conexión a 5 s y socket a 3 s; el worker usa transacción de 30 s
y espera PostgreSQL de lock de 2 s. No se mantienen transacciones durante HTTP DataCite.

Configuración opcional:

```properties
repo.search.index-retry-delay-ms=5000
repo.search.index-initial-delay-ms=5000
```

Diagnóstico (sin borrar datos):

```sql
SELECT count(*) AS pendientes, min(created_at) AS primera_pendiente FROM search_index_tasks;
SELECT resource_id, count(*) FROM search_index_tasks GROUP BY resource_id ORDER BY count(*) DESC;
```

Aplique la migración idempotente de `docs/migrations/2026-09-scientific-records.sql` si el
servidor utiliza `ddl-auto=validate`. No vacíe la cola para corregir una caída de Elasticsearch.
La cola empieza a cubrir las modificaciones desde esta actualización; no representa por sí
sola una reconstrucción inicial de un índice histórico incompleto.
