# Consola operativa

`operations.html` está disponible para curación y administración. No permite editar archivos o modificar configuración del servidor: consulta capacidad del volumen local, inventario técnico, estados de integridad registrados y las últimas veinte auditorías. El informe JSON exporta los mismos resultados con esquema y fecha, no contraseñas ni rutas del servidor.

## Interpretación

- Capacidad, espacio utilizable y espacio no asignado corresponden al **volumen**, compartido posiblemente con otros servicios. No equivalen al tamaño de datasets, cuota asignada ni espacio de copias de seguridad.
- Un almacenamiento no local se muestra no compatible con esta medición; un fallo se muestra no disponible, nunca cero bytes.
- El inventario técnico de archivos puede incluir borradores y registros pendientes de limpieza; no es una estadística pública de contenidos publicados.
- Los estados independientes de SHA-256 son resultados guardados; no significa que se haya realizado otra comprobación al abrir esta página.
- La ausencia de auditorías no acredita integridad. Los resultados identifican coincidencias, alteraciones, ausencias, falta de huella, no compatibles y errores.
- Las consultas individuales ocurren cerca de la fecha del informe, pero no constituyen una instantánea transaccional de todos los sistemas.

La nueva API `GET /api/v1/scientific/preservation/storage` requiere CURATOR/ADMINISTRATOR y devuelve `Cache-Control: no-store`. La página utiliza también las APIs de métricas e historial de preservación existentes. Para iniciar una comprobación se accede a Curación; esta consola no lanza auditorías por abrirse o exportar.

No sustituye monitorización externa, alertas institucionales, capacidad planificada, backups probados ni estadísticas certificadas COUNTER/DataCite.
