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
La descarga individual comprueba el monitor real (estado terminado y progreso
100 %) y audita su panel con axe. La interrupción de subida comprueba también
que el monitor muestre el error de red.
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

El smoke test también abre un contexto de navegador sin sesión para probar las
descargas públicas del depósito publicado de prueba: valida el contenido CSV,
la integridad del ZIP y la inclusión de description.md/datos, y la exportación
BibTeX. Comprueba los estados finales del monitor, no el guardado en disco del
usuario. Sigue pendiente validar archivos grandes y despliegue real.

## Monitor de transferencias aislado

```bash
node tools/e2e/transfer-smoke.cjs
```

No necesita el JAR. Arranca un servidor HTTP de prueba en loopback y Chrome:
comprueba progreso indeterminado sin Content-Length, cancelación de una descarga
en curso y cierre real de la conexión HTTP, éxito con progreso completo, error
HTTP y limpieza del historial. Verifica que el acceso anónimo no envía
Authorization. No valida cargas de archivos grandes ni rollback de subidas.

También prueba la subida multipart con respuesta 201, un rechazo 400 con
mensaje JSON y la cancelación XHR de una petición que ya llegó al servidor.
El servidor de prueba consume el cuerpo y retiene la respuesta para permitir
cancelar una petición activa: no simula almacenamiento ni garantiza rollback.
El test del monitor verifica además el foco al abrir, cierre con Escape y botón
Cerrar, estado aria-expanded y retorno al activador. El panel es no modal:
permite seguir navegando por el resto de la página.
Cuando termina de enviar los bytes y el servidor aún no responde, se comprueba
el estado «esperando confirmación del servidor» y que siga siendo cancelable.
El fixture HTTP declara UTF-8 explícitamente para validar mensajes en español.

El flujo del asistente entra además en Curación para abrir la vista previa de
un depósito completo (blockers puede omitirse en JSON) y descargar un archivo
mediante el monitor autenticado. También descarga el paquete de preservación desde Curación y verifica ZIP,
datos/descripción, declaración BagIt 1.0, JSON y hashes payload/tag contra
los bytes incluidos. Esto no sustituye validación externa RO-Crate/OAIS.

Se crea un enlace temporal de revisión en el backend efímero y se abre en un
contexto sin sesión: verifica retirada del token del fragmento de URL y descarga
con monitor. El monitor conserva no-store, rechaza destinos de otro origen y
no sigue redirecciones; no se prueba el HAProxy institucional.
El escenario de revisión comprueba no-store tanto en el archivo servido como
en el rechazo posterior. Revoca el enlace desde la sesión curatorial y vuelve
a descargar desde la página del revisor ya abierta: debe ser denegado y mostrar
error. Las copias descargadas antes de revocar no se pueden retirar.
También comprueba errores application/problem+json: conserva detail legible
sin prefijar el estado HTTP, y permite imponer un mensaje genérico para la
revisión privada. Los cuerpos no JSON o inválidos conservan el mensaje genérico.

## Regresión conjunta (sin servicios productivos)

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew --offline --no-daemon test
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew --offline --no-daemon -Dprofile=minimal bootJar
node tools/e2e/transfer-smoke.cjs
node tools/e2e/wizard-smoke.cjs
npm run audit --prefix tools/a11y
```

**Importante:** `-Dprofile=minimal test` ejecuta solo la suite documental.
No lo utilice como evidencia de la suite Java completa. El perfil minimal
sí sirve para construir el JAR usado por el smoke test.

## Mismo flujo con PostgreSQL real efímero

```bash
E2E_POSTGRES=1 node tools/e2e/wizard-smoke.cjs
# Otra versión local:
E2E_POSTGRES=1 PG_BIN=/usr/lib/postgresql/16/bin node tools/e2e/wizard-smoke.cjs
```

Sin sudo. Por defecto usa binarios PostgreSQL 18. Crea un clúster nuevo bajo
/tmp, escucha exclusivamente en 127.0.0.1 con puerto libre, usa SCRAM y una
contraseña aleatoria, y crea su propia base reduniv_e2e. Al terminar detiene
el clúster y elimina los temporales. Nunca usa el PostgreSQL instalado como
servicio ni las bases existentes. Ejecuta todos los escenarios del mismo smoke.
El modo sin E2E_POSTGRES sigue usando H2.
Esto comprueba flujo funcional PostgreSQL con esquema generado por Hibernate:
**no** prueba migración/retroceso, restauración de staging ni HAProxy real.
