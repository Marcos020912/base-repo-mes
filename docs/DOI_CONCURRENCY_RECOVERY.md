# Publicación DOI: concurrencia y recuperación

Cada familia mantiene un responsable pendiente y una marca de ejecución persistente.
Una transacción corta con locks primarios ordenados valida revisión/calidad/privacidad y
confirma el claim antes de llamar a DataCite. Mientras esté pendiente no se permite volver
a borrador. Otra versión de la misma familia no puede publicar, ni una segunda petición del
mismo recurso ejecutar llamadas de publicación simultáneamente si se pierde la lease HTTP.

El handler libera la ejecución al terminar, también ante un fallo de red, pero conserva
el responsable pendiente cuando no confirmó publicación. Reintentar el mismo depósito
concilia los DOI ya reservados/findables; no genera identificadores nuevos. Finalizar
correctamente libera la familia y marca la versión publicada en una transacción corta.

**Caída abrupta de JVM o pérdida de SQL al finalizar:** se mantiene la marca de ejecución
por seguridad. No se caduca automáticamente: hacerlo podría habilitar otro publisher
mientras el antiguo todavía estuviera enviando una operación remota. Antes de recuperar,
el operador debe detener/verificar detenidas **todas** las instancias que pudieran tener esa
publicación en curso y esperar la finalización de conexiones externas. Entonces puede
limpiar únicamente la marca de ejecución del ID de familia afectado:

```sql
BEGIN;
SELECT resource_id, doi_publication_owner, doi_publication_pending, doi_publication_running
FROM scientific_records WHERE resource_id = 'ID_RAIZ_DE_FAMILIA' FOR UPDATE;
UPDATE scientific_records SET doi_publication_running = false, revision = revision + 1
WHERE resource_id = 'ID_RAIZ_DE_FAMILIA' AND doi_publication_running = true;
COMMIT;
```

No borrar `doi_registrations`, no limpiar `doi_publication_owner` ni convertir el depósito
a DRAFT para eludir la conciliación. Reiniciar y reintentar la publicación del recurso
indicado en `doi_publication_owner`. Esto no valida las credenciales o políticas DataCite
institucionales y no afirma atomicidad distribuida con DataCite.
