# Artefactos y respaldo privado

## Verificación antes de actualizar

`update.sh` ofrece dos modos. Para descargar código de `main`, reconstruir y
reiniciar con la configuración existente:

```bash
sudo ./update.sh
# O una versión estable aprobada:
sudo ./update.sh vX.Y.Z
```

Descarga la referencia y avanza solo con fast-forward (rechaza cambios locales
versionados e historiales divergentes). Solicita confirmar respaldo de BD/archivos,
guarda configuración/JAR anterior y llama a `deploy.sh --update-from-source`
bajo el mismo bloqueo. Este modo omite instalación/configuración de PostgreSQL,
Elasticsearch y firewall, conserva las propiedades y **fuerza compilación** en
staging antes de detener Java. Requiere JDK21 y acceso a Gradle/dependencias.
`main` no equivale a una Release aprobada; en producción se recomienda un tag.
Un fallo de compilación conserva la aplicación anterior ejecutándose, aunque el
código ya esté actualizado. No se revierten automáticamente datos ni esquema.
No debe utilizarse desde la rama de desarrollo para fusionarla con producción.

Para una VM sin acceso a Gradle se conserva el modo de tres argumentos:

```bash
sudo ./update.sh vX.Y.Z /ruta/candidato.jar SHA256_DE_LA_RELEASE_APROBADA
```

El checksum se recibe por un canal de Release aprobado, no se calcula del JAR
recibido para demostrar su autenticidad. El script verifica antes de descargar
código o detener Java: SHA-256, estructura bootJar, rutas inequívocas, patrones
conocidos de secretos en entradas propias y ausencia de configuración privada
`application.properties` embebida. Ante un fallo, aborta sin cambiar el servicio.

También puede comprobarlo sin actualizar:

```bash
python3 tools/releases/verify_artifact.py /ruta/candidato.jar SHA256_ESPERADO
```

No demuestra firma del editor ni correspondencia con un commit. No audita todos
los secretos arbitrarios ni las dependencias anidadas. No imprime los valores
de hallazgos. Conservar revisión de Release y análisis institucional de historial.

## Respaldo

En mantenimiento, detener **todas** las instancias/escrituras y obtener un dump
custom con `pg_dump -Fc`, usando `.pgpass`/servicio PostgreSQL privado; no poner
contraseñas en comandos ni Git. Después empaquetar la misma copia de datos:

```bash
python3 tools/releases/package_backup.py \
  --dump /ruta/privada/database.dump --files /var/lib/base-repo/data \
  --config /ruta/config/application.properties \
  --output /ruta/privada/backup-nuevo.tar.gz --writes-stopped
```

`--writes-stopped` es una confirmación del operador, no una comprobación remota.
La herramienta nunca se conecta a PostgreSQL, detiene servicios ni restaura.
Comprueba `pg_restore --list`, archivos regulares y ausencia de enlaces; rechaza
sobrescribir o colocar el respaldo dentro de los datos. Salida0600 y manifiesto
SHA-256 por entrada. Es **confidencial**, incluye usuarios/hashes/configuración:
no subir a Git/GitHub ni compartirlo como evidencia pública. No está cifrado;
proteger almacenamiento, acceso y transferencia institucionalmente.

Comprobar hashes no sustituye restaurar. Ensayar dump/archivos en una instalación
separada con `tools/migrations/rehearse.sh` y el recorrido E2E, antes de actualizar.
No ejecutar SQL de respaldos no confiables; inspeccionarlos y usar entorno aislado.
No hay restauración automática a producción ni reversión automática del esquema.

## Pruebas

```bash
python3 -m unittest discover -s tools/releases -p 'test_*.py'
E2E_POSTGRES=1 E2E_MAIL=1 E2E_POSTGRES_RESTORE=1 node tools/e2e/wizard-smoke.cjs
```

La E2E usa dump/archivos/configuración sintéticos privados y verifica el manifiesto
junto al ensayo PostgreSQL. No prueba la consistencia de una copia de producción
ni todos los esquemas históricos.

La comprobación de arranque exige además que **el PID del candidato** tenga un
socket de escucha en el puerto comprobado (`ss`), antes de aceptar el HTTP200.
Así una página de otro proceso no basta. `APP_CHECK_URL` se valida antes de parar
servicios: HTTP interno, sin credenciales/query/fragmento. El operador debe indicar
la dirección de esta instalación, no el HAProxy remoto. Sigue sin sustituir las
pruebas funcionales de DB/SMTP/ES ni demostrar actualización sin interrupción.

## Candidato de producción

Con JDK21 y árbol Git limpio/revisado:

```bash
./tools/releases/build_candidate.sh
```

Construye en staging, sin sobrescribir el JAR de una demo activa. Salida privada
ignorada en `build/releases/COMMIT/`: JAR, SHA256SUMS, manifiesto del commit,
auditoría del artefacto y runbook. No crea tags/releases ni sube nada. La suma
local detecta corrupción, no certifica procedencia por sí sola. Un candidato con
versión SNAPSHOT no sustituye el tag estable aprobado. Rechaza sobrescribir un
candidato; reconstruir desde un commit nuevo después de la aprobación.

El gate de `production_preflight.py --config ... --strict-schema` es de lectura;
no imprime valores ni confirma servicios remotos. Ver `docs/PRODUCTION_RUNBOOK.md`.

Las operaciones Git de `update.sh` se ejecutan como el usuario que llamó a sudo
(`SUDO_USER`), no como root. La instalación mantiene privilegios para configuración
y arranque. Si un despliegue antiguo dejó el índice de Git propiedad de root,
corregir únicamente sus metadatos, desde la carpeta del proyecto:

```bash
sudo chown -R "$(id -u):$(id -g)" .git
git pull --ff-only
```

No usar `chmod 777`, `sudo git pull` ni cambiar permisos de datos/configuración
privada para resolverlo. Un fallo al leer el índice aborta antes de fetch/deploy.
