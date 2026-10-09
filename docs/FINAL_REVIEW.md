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
