# Ensayo aislado de migración RedUniv

`rehearse.sh` arranca un clúster PostgreSQL temporal **solo por socket Unix**,
aplica `docs/migrations/2026-09-scientific-records.sql` dos veces, compara el
esquema, hace `pg_dump`/`pg_restore` y compara esquema, datos y archivos tras
restaurar. El clúster y las copias temporales se eliminan al salir. Ejecútelo
**sin sudo**; nunca conecta al servidor de origen.

```bash
# Smoke test con datos ficticios (no valida una instalación real):
tools/migrations/rehearse.sh

# Ensayo con copias obtenidas previamente en staging:
tools/migrations/rehearse.sh --db-dump /ruta/segura/staging.dump \
  --files /ruta/segura/copia-de-archivos
```

El dump debe ser custom (`pg_dump -Fc`). Obtenga la copia y el directorio de
archivos de una **misma ventana de mantenimiento**, sin escrituras concurrentes.
Proteja el dump: puede contener usuarios, hashes y otros datos sensibles. No
lo suba a Git. Compruebe además permisos, capacidad de disco, tiempos de
restauración y arranque funcional de la aplicación en staging; este script no
sustituye esas verificaciones ni modifica la producción.
El ensayo necesita espacio temporal para el clúster, dump y tres copias de los
archivos. Rechaza enlaces simbólicos en el directorio fuente porque `cp -a`
solo conservaría el enlace, no los datos del destino externo.

## Diagnóstico de diferencias

La comparación normaliza únicamente las claves aleatorias de las líneas psql
`\restrict`/`\unrestrict` emitidas por algunas versiones de pg_dump; no modifica
SQL ni el dump utilizado para restaurar. La detección de `--restrict-key` no usa
una tubería que pueda fallar por SIGPIPE con `pipefail`. Un cambio real de tablas,
columnas o datos sigue bloqueando el ensayo.

Para conservar diferencias cuando falla una comparación:

```bash
tools/migrations/rehearse.sh --db-dump /ruta/segura/database.dump \
  --diagnostics /ruta/segura/diagnostico-nuevo
```

El directorio debe ser nuevo. Se crea privado (0700; archivos0600) e incluye los
dumps comparados, diferencia y logs. Puede contener datos personales y secretos:
no publicarlo ni pegarlo completo en un chat. El clúster temporal se limpia igual.
