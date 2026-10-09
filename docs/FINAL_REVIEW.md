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
