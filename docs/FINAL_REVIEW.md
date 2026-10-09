# Revisión final local — 9 octubre 2026

Rama: `develop-reduniv`. Alcance confirmado: registro/login propios y estadísticas
locales; DOI resuelto según el usuario. Pruebas en el PC del usuario, con bases,
archivos y cuentas sintéticos aislados. No se fusionan ramas ni se despliega.

## Resultados

- Backend: 70 suites, 1374 pruebas, cero fallos, errores u omisiones.
  Log: `/tmp/reduniv-final-review-java.log`.
- JAR compilado y 59 archivos estáticos idénticos a las fuentes. Auditoría
  heurística de sus 319 entradas propias: cero hallazgos. No acredita procedencia
  de un Release ni ausencia absoluta de secretos. Log:
  `/tmp/reduniv-final-review-artifact.json`.
- 18 fixtures de Chrome: aprobados. Incluyen login, filtros/historial,
  administración, nueve pasos de depósito, fichas, traducciones, estadísticas
  locales, transferencias y conservación de formularios.
- Axe: 69 estados, cero infracciones automáticas; no sustituye revisión humana
  ni constituye certificación WCAG. Log: `/tmp/reduniv-final-review-a11y.log`.
- Gates: 7 pruebas de releases/respaldos, 9 de seguridad/despliegue, 9 de
  traducciones, 7 de validación externa, 2 de inventario de archivos y 4 de citas.
  Sintaxis Bash y compatibilidad estructural de API aprobadas.
- Corrección del ensayo de transferencias: cargaba `transfers.js` sin sus
  dependencias reales `ui-locales.js`/`ui-i18n.js`. Se corrigió el fixture y se
  repitieron las pruebas con éxito; las páginas reales ya cargaban esos scripts.
- Ensayo PostgreSQL/SMTP/Chrome/dos JVM/restauración: aprobado, terminal0.
  Incluye registro/verificación y recuperación tras rechazo SMTP451, 132 operaciones
  privadas protegidas, rate limit compartido (20 intentos, 10 admitidos), métricas
  locales, 221 datasets/217 públicos/11 páginas estables, backup con hashes,
  migración doble, restauración de tablas y descarga posterior.
  Log: `/tmp/reduniv-final-review-e2e.log`.
- Ensayo aislado de migración: aprobado, terminal0; esquema idempotente y
  restauración de datos/archivos sintéticos. Log:
  `/tmp/reduniv-final-review-migration.log`.

## Alcance y siguiente paso

El correo utilizado aquí es un servidor SMTP local sintético: no se envían
mensajes institucionales. No se reservan/publican DOI reales ni se emiten
estadísticas externas reales. No se altera una base de datos desplegada.

La revisión humana final corresponde al administrador, según decisión del
usuario. La fusión y el despliegue requieren autorización explícita y respaldo.
El PC de pruebas no demuestra el comportamiento del HAProxy productivo.

## Comprobación durante el arranque local

Al lanzar la aplicación real sobre una base nueva aparecieron dos problemas
que no deben ocultarse con los resultados anteriores:

- `ElasticWrapper` fijaba `baserepo` para escrituras aunque se configurase otro
  índice. Ahora resuelve `repo.search.index`, conservando `baserepo` por defecto.
  Dos pruebas de contexto comprueban ambas situaciones; el índice de demo
  queda separado del anterior.
- El JSON de catálogo vacío omitía `items` por la política global `NON_EMPTY`.
  Ahora devuelve `items:[]`; una prueba específica verifica la serialización.

Tras ambas correcciones: **72 suites / 1377 pruebas, cero fallos, errores u
omisiones**, compilación JAR aprobada. La revisión final anterior de 1374 pruebas
corresponde al código previo a estas tres pruebas adicionales. El arranque y
las comprobaciones reales de navegador se realizan en el PC del usuario con
PostgreSQL persistente local dedicado, Elasticsearch y SMTP capturado localmente.
No se migran los datos H2 antiguos ni se envían mensajes/DOI/eventos externos.

## Revisión de navegación y verificación local (9 octubre 2026)

- Menú compartido por rol: mismas entradas y orden entre secciones. Colecciones
  y autoevaluación mantienen su vista pública anónima y usan la barra lateral
  cuando hay sesión. Selector de idioma entre logo y primer enlace.
- Mostrar/ocultar contraseña en cada campo, sin alterar su valor ni enviar el
  formulario. Login y registro seleccionan explícitamente el botón de envío.
- Comprobación en el Chrome ya abierto del usuario: catálogo, usuarios,
  colecciones, operaciones y cuenta; idioma persistente y controles correctos.
- Java: **73 suites / 1380 pruebas**, sin fallos. **19 fixtures de navegador**,
  **69 estados axe sin infracciones**; ensayo PostgreSQL/SMTP/dos JVM y
  restauración aprobado. Fixture compartido: 36 combinaciones rol/sección.
- El correo de esta demo se captura en el visor local `http://localhost:8025/`;
  no llega a un buzón externo. La cuenta creada permanece sin verificar hasta
  introducir el código. Reenviar cuando expire, sin borrar ni recrear la cuenta.

`GET /api/v1/public/mail-delivery` devuelve únicamente el modo de entrega y,
si está habilitado explícitamente, un enlace HTTP de visor loopback validado.
No publica credenciales, usuarios ni códigos. Por defecto
`repo.mail.delivery-mode=SMTP` y `repo.mail.preview-url` está vacío.
Para demo local se configura `LOCAL_CAPTURE` y el visor; esto solo muestra
instrucciones y **no cambia el transporte SMTP**. En producción conservar
SMTP y configurar el servidor real mediante `spring.mail.*`.

Cambios solo en `develop-reduniv`, sin fusión ni publicación. La aplicación
local se recompiló y reinició conservando PostgreSQL y el índice de demo.

### Barra lateral independiente del documento

La barra lateral de escritorio ahora se limita al alto visible (`100dvh`),
permanece adherida arriba y no se estira con la fila del grid. Solo los enlaces
se desplazan dentro del menú; logo, idioma e identidad/cierre de sesión se
conservan. En móvil permanece la navegación adaptable anterior.

Comprobado en Chrome real con admin (`localhost`) y usuario (`127.0.0.1`):
desplazar el catálogo 179/87 px deja la barra en top=0 y el pie en la misma
posición que en Mi cuenta. No se modifican sesiones, contraseñas ni datos.
El fixture compartido incluye documentos de 3000 px en cuatro páginas para
ambos roles, pie estable, scroll del menú a 600 px y móvil a 320 px.
Fixture aprobado; axe: 69 estados, cero infracciones; JAR recompilado y
aplicación reiniciada, health UP.

### Asistente, avisos y tarjetas de pendientes

- Avisos compartidos: errores rojos y éxitos verdes, 10 segundos, botón de cierre
  accesible; los errores conectados de `uiI18n.showError` ya no duplican texto
  rojo en el cuerpo. Los mensajes de progreso y los detalles diagnósticos de
  modales conservan su función. Avisos alojados sobre el diálogo abierto para
  evitar que la capa nativa del modal los oculte.
- Disciplina/licencia: sugerencias con búsqueda, navegación con flechas,
  Enter/Escape y estilos propios; conserva el campo original y permite entrada
  libre. No hay cambios a los vocabularios ni reglas del servidor.
- Radios de carga en tarjetas y checkbox de confirmación alineado con su texto.
- Regeneración de vista previa: bloqueo durante lectura, resultado confirmado,
  errores legibles y control de resultados obsoletos; no transfiere archivos.
- Mis depósitos: tarjeta breve con «Ver detalles», modal de requisitos y
  recomendaciones, «Cerrar» y «Completar depósito». Enlace no partido entre
  líneas para mantener una superficie de clic fiable.

Pruebas: 21 fixtures aislados aprobados; prueba específica de controles,
modal y sugerencias con axe aprobada; auditoría base de 69 estados sin
infracciones; 9 pruebas i18n aprobadas. Ensayo del asistente con JAR/Chrome y
backend aislado aprobado: md, ZIP de descripción, ZIP integral, subida
interrumpida, recuperación, versiones, permisos, curación y vocabularios.
La compilación y el reinicio local conservan los datos del usuario; no se
publican recursos ni se fusionan ramas. Comprobado modal real de pendientes en
la sesión del usuario sin modificar el dataset.

### Preparación del depósito guiada

- Implementado el diseño aprobado: progreso, número de requisitos pendientes,
  tarjetas con «Cómo resolverlo» y botones hacia descripción, archivos,
  metadatos o autores. El destino recibe foco y resaltado temporal; navegar no
  abre formularios ni realiza escrituras.
- Recomendaciones separadas de requisitos, ayuda desplegable y comprobaciones
  completadas plegadas. DOI/ORCID/ROR no se presentan como obligatorios.
- «Volver a comprobar» consulta el informe del servidor; cambiar idioma vuelve
  a dibujar el informe en caché sin consultas adicionales. Fallos del informe
  no producen una falsa confirmación de completitud.
- Acciones solo para el autor de un borrador. Completar esta comprobación no
  equivale a aprobar privacidad ni a publicar el depósito.
- Descripción y archivos tienen encabezados explícitos y «Subir descripción».

Validado: fixture guiado con navegación/foco/permisos/es-en/320 px y axe;
regresiones estáticas/dinámicas de ficha y 36 casos de barra lateral; auditoría
base 69 estados sin infracciones y 9 pruebas i18n; flujo integral con PostgreSQL,
SMTP y JAR aislados aprobado. JAR compilado y aplicación local reiniciada UP.
En Chrome real: depósito del usuario al 76 %, cero requisitos base pendientes,
recomendaciones separadas; «Ir a autores» enfoca Editar identidades sin abrir
modal, sin desbordamiento horizontal. Datos y archivos del usuario preservados.

### Modal de autores corregido

Estructura header/modal-body/footer conforme al modal compartido; márgenes
internos, altura limitada al viewport, cuerpo con scroll y acciones persistentes.
Nombres largos e instituciones no desbordan. Fixture con diez instituciones,
320 px y axe aprobado, sin escrituras a cuentas o depósitos reales.

### Cuenta desde identidad y tareas vacías

- Eliminado «Mi cuenta» del registro compartido de navegación para todos los
  roles. El nombre de usuario abre un modal con el formulario existente de
  contraseña, visibilidad por campo y validación; al cerrar borra los valores.
  La URL antigua sigue compatible, sin enlace de menú. No se cambian credenciales
  ni se elude la contraseña actual; éxito conserva la invalidación de sesiones.
- Error admin reproducido en Chrome localhost: una cola vacía perdía `items`
  debido al Jackson global NON_EMPTY. DTO Tasks ahora incluye explícitamente
  `items: []`; prueba de serialización con la misma política global aprobada.
- En Chrome real admin ahora muestra «Sin tareas de depósito»; modal verificado
  en admin localhost y usuario127.0.0.1 sin enviar formularios. Fixture de menú
  de36casos ampliado con apertura/cierre/borrado/visibilidad y axe del modal.

### Barra de búsqueda de Mis depósitos

Buscador flexible con etiqueta e icono, botón alineado y contador en insignia;
reglas locales evitan heredar columna de170px del catálogo. Móvil distribuye
búsqueda en primera fila. Cuenta singular/plural y resultados filtrados es/en.
Fixture específico aprobado: campo amplio, filtrado, idioma,320px y axe0,
sin escrituras;9tests i18n y menú compartido aprobados.

### Preparación de producción (candidato, sin despliegue remoto)

- Gate de configuración de lectura y sin valores de secretos: duplicados,
  autenticación/JWT, PostgreSQL/esquema no destructivo, persistencia, CORS HTTPS,
  HAProxy, SMTP cifrado/identidad/captura, privacidad y Actuator mínimo. Update
  exige además ddl-auto=validate, tras migraciones y respaldo aprobado.
- Deploy compila en staging, audita estructura del JAR antes de parar Java,
  bloqueo compartido con update, parada solo de esta instalación y confirmación
  HTTP perteneciente al PID candidato. No se recrean bases ni se toca la VM.
- Nueva configuración usa CORS real de seguridad y SMTP limitado al host con
  identidad; configuraciones existentes inseguras requieren corrección explícita.
- Empaquetado candidato con commit/SHA256/auditoría/runbook sin reemplazar JAR
  activo, ni secretos/configuración local, sin crear tag ni subir Release.
- Regresión completa:73suitesJava/1381tests,0fallos;13tests releases,9security,
  catálogos/migraciones/validación/contrato Python y fixtures de UI aprobados.
  Corregida accesibilidad del botón de nombre: aria-label incluye nombre visible.
- Validación final VM/HAProxy/SMTP/DOI, restore real, revisión CVE/carga y
  supervisor no-root siguen siendo aceptación operativa, no garantizada por
  pruebas locales. Procedimiento en PRODUCTION_RUNBOOK.md.
