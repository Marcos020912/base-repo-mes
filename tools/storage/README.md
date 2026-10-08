# Inventario de rutas de archivos heredados

`audit_content_paths.py` es de **solo lectura**: compara el `content_uri` de
`content_information` con `repo.basepath`, sigue enlaces simbólicos y clasifica
archivos faltantes, no locales o fuera de la carpeta real. No mueve archivos ni
modifica PostgreSQL. Necesita Python 3.10+ y acceso de lectura al mismo árbol
de archivos que usa la aplicación; ejecute en la VM de staging o sobre una
copia montada **en las mismas rutas absolutas**. Un volcado de base de datos
sin los archivos no permite concluir que todos estén perdidos.

Ejemplo con una **copia de staging autorizada**, usando autenticación de
PostgreSQL ya configurada (no escriba contraseñas en el comando):

```bash
set -o pipefail
psql -X -v ON_ERROR_STOP=1 -d base_repo \
  -c "COPY (SELECT id, parent_resource_id, relative_path, content_uri FROM content_information ORDER BY id) TO STDOUT WITH CSV HEADER" \
  | python3 tools/storage/audit_content_paths.py \
      --basepath 'file:/var/lib/base-repo/data' \
      --report ./content-path-anomalies.csv --strict
```

Compruebe los nombres de tabla/columnas con `\d content_information` si la
instalación heredada utiliza otro esquema. También puede pasar un CSV exportado
con `--csv archivo.csv`. La salida normal solo muestra recuentos; `--report`
crea un CSV nuevo con permisos `0600` que contiene rutas internas e
identificadores. Guárdelo fuera de Git, no lo publique y elimínelo después de
planificar la migración. El código de salida `1` con `--strict` significa que
hay anomalías; **no** significa que el script haya cambiado nada.

`OUTSIDE_ROOT` incluye enlaces simbólicos que salen de `repo.basepath`;
`MISSING_FILE` significa que la ruta no existe desde la cuenta ejecutora;
`UNSUPPORTED_URI` incluye referencias remotas no descargables por las rutas
locales actuales. No corrija `content_uri` directamente con SQL: primero
respalde datos y archivos, determine el origen de cada caso y ensaye la
migración/restauración.

Pruebas locales sin PostgreSQL:

```bash
python3 -m unittest discover -s tools/storage -p 'test_*.py'
```

Integración local con datos generados por la app y PostgreSQL real:

```bash
E2E_POSTGRES=1 E2E_POSTGRES_RESTORE=1 node tools/e2e/wizard-smoke.cjs
```

El ensayo exporta content_information de la base temporal restaurada y ejecuta
este inventario con --strict sobre sus archivos. Exige registros no vacíos y
cero anomalías sin crear informe ni modificar datos. No reemplaza el inventario
de URI heredadas de una copia de staging.
