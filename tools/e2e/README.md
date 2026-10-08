# Smoke test del asistente en navegador real

Ejecute en la rama de desarrollo, **sin sudo**:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew --offline --no-daemon -Dprofile=minimal bootJar
npm ci --prefix tools/a11y
node tools/e2e/wizard-smoke.cjs
```

El script inicia el JAR únicamente en `127.0.0.1`, con PostgreSQL sustituido
por una base H2 en memoria, cuenta administrativa y secreto JWT aleatorios, y
directorio de archivos temporal. Chrome recorre el login y las cuatro etapas
del asistente tres veces: `description.md` + CSV; ZIP de descripción con
imagen + dos CSV; y un ZIP integral con `description/description.md`, imagen
y dos CSV. Comprueba la vista previa, el borrador y la imagen renderizada.
Además intenta subir ZIP integrales sin descripción o con una extensión no
permitida, verifica que no alteren el depósito y confirma el envío a revisión
solo después de mostrar la descripción y los archivos extraídos.
También corta deliberadamente una carga de CSV después de crear el borrador:
comprueba que permanece en `DRAFT`, abre el enlace de recuperación, vuelve a
subir el archivo desde la ficha y solo entonces lo envía a revisión.
Por último, publica una **versión sintética en H2** con un DOI ficticio de
prueba, abre «Crear nueva versión» y comprueba que el nuevo borrador enlaza la
anterior, conserva el DOI conceptual y no hereda archivos sin consentimiento.
Esto no llama a DataCite ni valida un DOI institucional.
Los ZIP adversariales generados con Python 3 comprueban rechazo de rutas con
`..`, entradas duplicadas y archivos HTML dentro de la descripción, además de
las extensiones no admitidas del dataset. Todas esas pruebas verifican que el
contenido previamente cargado permanezca intacto.
Al terminar detiene Java y elimina la base, credenciales y archivos temporales.
No toca la configuración ni los datos del despliegue. `CHROME_BIN` y `JAVA_BIN`
permiten indicar rutas alternativas.

Si se selecciona un ZIP, el asistente guarda un borrador y la ficha muestra
una vista previa real antes de confirmar el envío a curación; la vista previa
del paso 4 solo puede mostrar el nombre del ZIP antes de descomprimirlo.

Esto **no** sustituye pruebas con personas, PostgreSQL/HAProxy reales, fallos
de almacenamiento a mitad de escritura, curación real, DataCite ni versiones
con datos históricos de staging.
