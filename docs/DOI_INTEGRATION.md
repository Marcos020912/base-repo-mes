# Integración DataCite: estado y requisitos

La especificación institucional recibida el 28-09-2026 propone usar la API REST
de DataCite, no desplegar un servidor DOI. Se contrastó con las guías oficiales
de [creación](https://support.datacite.org/docs/api-create-dois),
[actualización](https://support.datacite.org/docs/updating-metadata-with-the-rest-api)
y [estados](https://support.datacite.org/docs/doi-states).

## Ya implementado en `develop-reduniv`

- `DataCiteService` encapsula reserva Draft, publicación Findable, lectura
  autenticada y actualización de metadatos/URL. Solo admite los endpoints
  oficiales de pruebas y producción mediante HTTPS. Credenciales solo backend.
- `DataCiteMetadataMapper` prepara los campos mínimos de una versión y exige
  landing HTTPS, título, autoría, editorial y año. El cliente está **apagado**
  por defecto; ninguna ruta de la aplicación lo invoca todavía.
- Pruebas HTTP locales comprueban el método, autenticación, carga útil y que
  la reserva Draft no incluya `event=publish`. No se han usado credenciales ni
  realizado pruebas contra DataCite real.

## Configuración futura (privada, nunca en Git)

```properties
repo.datacite.enabled=false
repo.datacite.api-url=https://api.test.datacite.org
repo.datacite.repository-id=<id de cuenta Repository del entorno de pruebas>
repo.datacite.password=<secreto entregado por canal seguro>
repo.datacite.prefix=<prefijo de ese entorno>
```

La configuración de producción usa `https://api.datacite.org` y **otras**
credenciales/prefijo. No activar hasta completar y aprobar el flujo editorial.
No compartir secretos en incidencias, capturas ni conversaciones.

## Pendiente antes de activar

1. Credenciales Repository y prefijo, primero de pruebas y luego de producción.
2. Persistir cada reserva y su estado con historial de sincronización. Diseñar
   reconciliación para fallos entre la API externa y PostgreSQL, reintentos sin
   duplicar reservas, y control de concurrencia.
3. Asegurar una URL permanente por versión, por ejemplo `/datasets/{uuid}`.
   El frontend actual usa `public-resource.html?id=...`; no enviar a DataCite
   una ruta futura que todavía no exista y no apunte a una landing durable.
4. Reservar Draft en el flujo del autor; **no** presentar Draft como DOI público
   ni generar cita definitiva con ese identificador: Draft no resuelve.
5. Publicar Findable solo tras validación curatorial y comprobar el estado
   devuelto por DataCite. La operación remota y el cambio de estado local
   requieren reconciliación; no se puede asumir una transacción distribuida.
6. DOI conceptual, DOI por versión y relaciones de versionado verificadas por
   bibliotecarios. Una retirada conserva la landing/tombstone y el DOI; los
   registros Findable/Registered no se pueden borrar.
7. Pruebas en DataCite Test, staging y producción con aprobación institucional.

El actual botón editorial de publicación con confirmación de registro externo
permanece como flujo **manual** hasta completar lo anterior. No mezclarlo con
el cliente automático sin una migración explícita de DOI previamente asignados.
