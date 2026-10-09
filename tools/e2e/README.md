# Pruebas de navegador e integración

Estado actualizado: `docs/LOCAL_COMPLETION.md` y último apartado de
`docs/REDUNIV_EXECUTION_PLAN.md`. Las descripciones históricas H2/cuatro etapas
de abajo corresponden al primer incremento; el asistente actual tiene nueve etapas
y la E2E opcional PostgreSQL/SMTP/dosJVM/restauración cubre el flujo completo.
Añade inventario anónimo/HEAD API, baseline estructural, Actuator/listados legacy,
rate limit compartido20intentos y volumen221registros/217publicados/11páginas.

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

## Migración y restauración del fixture PostgreSQL

```bash
E2E_POSTGRES=1 E2E_POSTGRES_RESTORE=1 node tools/e2e/wizard-smoke.cjs
```

Tras los escenarios detiene el JAR, aplica la migración SQL dos veces al
fixture, hace pg_dump custom y pg_restore en otra base del mismo clúster
temporal, y arranca el JAR apuntando a esa restauración. Verifica login, título
del dataset publicado, descarga CSV y huellas SHA-256 del árbol de archivos
sin cambios. No modifica ni importa bases existentes.
El fixture empieza con el esquema actual generado por Hibernate; no representa
todas las variantes de esquemas antiguos ni sustituye el ensayo de staging.
El reinicio renueva la clave JWT efímera. Se conserva el token antiguo del
navegador y se verifica que el login siga accesible y lo sustituya, sin limpiar
localStorage manualmente.

En el segundo arranque se usa ddl-auto=validate: Hibernate no puede completar
ni corregir el esquema restaurado para ocultar incompatibilidades. Antes de
arrancarlo se compara por cada tabla del esquema public el recuento de filas y
una huella MD5 del JSON de filas ordenado, entre origen migrado y restauración.
Esta huella es un control del fixture, no un mecanismo criptográfico de
preservación ni una prueba de upgrade desde todos los esquemas históricos.
La restauración exporta además content_information como CSV y ejecuta el
inventario de rutas --strict contra el árbol temporal: no debe estar vacío ni
tener URI inválidas, archivos ausentes o rutas fuera de repo.basepath.

## Páginas de acceso aisladas

```bash
node tools/e2e/auth-pages-smoke.cjs
```

Servidor HTTP loopback con archivos reales y un rechazo de login controlado.
Comprueba que login y registro permanecen accesibles con token viejo, que la
confirmación incorrecta de contraseña no envía solicitud y que un fallo de
login muestra mensaje legible. No crea cuentas ni prueba correo/verificación.
La prueba de acceso también simula un reenvío fallido y posterior éxito:
verificar/reenviar quedan bloqueados mientras espera, el doble click no duplica
peticiones y el éxito elimina el estilo de error. Simula verificación exitosa
y comprueba bloqueo hasta navegar al login. No valida entrega real de correo
ni la validez de un código contra la base de datos.

## Registro y verificación con SMTP local

```bash
E2E_POSTGRES=1 E2E_MAIL=1 node tools/e2e/wizard-smoke.cjs
```

Inicia un receptor SMTP de prueba en 127.0.0.1 con puerto libre. No reenvía correo
y solo acepta destinatarios example.invalid. El mensaje se mantiene en memoria
y no imprime el código. Prueba JavaMail real: registro 201, código de seis
dígitos, login bloqueado antes de verificar, verificación, login con rol USER
y rechazo de reutilizar el código consumido. El SMTP efímero no autentica ni
usa TLS; no certifica STARTTLS, credenciales ni entrega del correo institucional.
El SMTP fixture también rechaza MAIL FROM con 451: registro devuelve 503
recuperable, la cuenta sigue existiendo y no puede entrar, repetir registro
devuelve conflicto y el reenvío fallido conserva mensaje opaco. Tras levantar
el rechazo, reenvía el código, verifica y permite login USER. No reproduce
DNS interno, TLS ni corrige el servidor de correo institucional.
El modo correo verifica además permisos multiusuario contra el backend:
el usuario verificado ve el dataset compartido de otro autor, no puede listar
usuarios ni editar un borrador ajeno; tras suspensión administrativa, su token
ya emitido queda bloqueado y el login devuelve ACCOUNT_RESTRICTED.
El escenario multiusuario cambia también la contraseña propia: el token anterior
debe dejar de servir y un login inmediato con la nueva contraseña debe producir
una sesión utilizable, antes de comprobar la suspensión administrativa.

Todos los escenarios opcionales pueden ejecutarse juntos:

```bash
E2E_POSTGRES=1 E2E_MAIL=1 E2E_POSTGRES_RESTORE=1 node tools/e2e/wizard-smoke.cjs
```

La compatibilidad de tokens locales anteriores sin passwordVersion se prueba
en LocalJwtPasswordVersionTest: se aceptan si se emitieron después del último
cambio de contraseña y se rechazan si lo preceden. No se habilitan JWT de
emisores externos ni se desactiva la validación de firma.
Login/registro también bloquean envíos mientras están pendientes: la prueba
invoca requestSubmit de nuevo y exige una sola petición. Un rechazo HTML del
proxy produce mensaje genérico legible; registro503 recuperable conserva el
aviso y redirige a verificación con correo y mailPending.

### Guardado directo a disco

El monitor ofrece una opción manual cuando `showSaveFilePicker` está disponible en contexto seguro. No se activa automáticamente. La prueba `transfer-smoke.cjs` sustituye solo el selector y utiliza un handle OPFS real del navegador para comprobar write/close/abort; no prueba el diálogo del sistema ni permisos del disco del usuario. En otros navegadores continúa la descarga mediante Blob en memoria. Validar archivos grandes y selección nativa en staging antes de declarar soporte masivo.

### Navegación compartida y contraseñas

`node tools/e2e/shared-shell-smoke.cjs` recorre 36 combinaciones de rol/sección
con HTML, estilos y scripts compartidos reales. Comprueba orden y permisos del
menú, idioma entre logo y navegación, persistencia de idioma, página activa,
contraseñas visibles/ocultas sin perder el valor ni enviar el formulario,
instrucciones del correo capturado y ausencia de desbordamiento a 320 px.
No usa cuentas reales ni envía mensajes. `E2E_SCREENSHOT_PATH` permite guardar
una captura de la barra lateral del fixture.
El fixture compartido también comprueba que documentos largos no estiren la
barra de escritorio: usuario/admin, cuatro páginas de 3000 px, scroll del
contenido y posición estable del pie; a 600 px el menú tiene su propio scroll.

### Controles del asistente y avisos flotantes

`node tools/e2e/wizard-controls-smoke.cjs` usa APIs sintéticas y archivos locales
para comprobar sugerencias filtrables y teclado sin enviar el formulario,
valores libres, regeneración de vista previa con confirmación, avisos cerrables
que desaparecen a los 10 segundos, radios/checkboxes compactos y modal de
pendientes con cierre y enlace para completar. Comprueba axe con sugerencias
abiertas y con modal abierto, y que no existan escrituras HTTP. Los avisos
permanecen visibles también sobre diálogos nativos abiertos.
`E2E_SCREENSHOT_PATH` guarda una captura sintética del modal.

### Preparación guiada del depósito

`node tools/e2e/readiness-smoke.cjs` comprueba tarjetas de requisitos y
recomendaciones, instrucciones, navegación con foco y resaltado sin abrir
modal ni mutar datos, permisos de autor, es/en sin consultas adicionales,
actualización del informe y accesibilidad/ausencia de desbordamiento a 320 px.
Usa informes sintéticos y no modifica depósitos reales.
`E2E_SCREENSHOT_PATH` guarda una captura de prueba de esta sección.

### Modal de identidad de autores

`node tools/e2e/creator-modal-smoke.cjs` prueba encabezado/cuerpo/pie con
márgenes, nombres largos, diez instituciones, cuerpo desplazable a 320 px,
axe y ausencia de escrituras; datos sintéticos. `E2E_SCREENSHOT_PATH` opcional.

El fixture compartido también comprueba que «Mi cuenta» no aparezca en el menú
para ningún rol; el nombre abre un modal, los campos se vacían y ocultan al
cerrarlo y el modal pasa axe. No envía cambios de contraseña.
