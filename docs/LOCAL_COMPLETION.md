# Cierre del trabajo técnico independiente — 9 octubre 2026

Rama `develop-reduniv`. **Sin merge, push, publicación de Release ni despliegue.**
Este cierre distingue trabajo local verificable de aceptación productiva y
certificaciones. No afirma ausencia absoluta de vulnerabilidades ni rendimiento
ilimitado, compatibilidad con clientes desconocidos o certificación WCAG/OAIS.

## Trabajo realizado sin datos institucionales

| Bloque | Implementación/revisión y evidencia local |
|---|---|
| Permisos | Guards por recurso/estado/autoría y matrices existentes; inventario HTTP real de operaciones privadas incluido HEAD, revocación de cuenta/contraseña, roles de administración/curación y notas privadas. Cerradas vías alternativas de listados/search legacy para USER y exposición anónima de Actuator. |
| Concurrencia y abuso | PostgreSQL real compartido por dos JVM, leases/fence, pérdida de sesión multipart y recuperación; 20 intentos de login concurrentes, exactamente 10 admitidos y Retry-After compartido. Transacciones del limitador5s y lock_timeout PostgreSQL2000ms. |
| Catálogo | Filtros de autor/categoría/año/licencia/institución/disciplina/idioma/acceso, orden estable, URL/historial, límites de entrada consistentes y Locale.ROOT. Pruebas JPA y PostgreSQL:221 registros,217 publicados,11 páginas sin duplicados; facetas no cuentan borradores. No es un benchmark de millones de registros. |
| API | Seguridad de transporte explícita de toda superficie v1, 110 operaciones y baseline de103 esquemas; referencias/modelos/binarios y siete clases de cambios incompatibles comprobadas. Aceptación de consumidores reales queda externa. Cita pública de versión publicada documentada correctamente. |
| DOI | Workflow recuperable con clientes simulados, claim familiar, runner único e inmutabilidad. Registro local detallado: fechas de intentos/éxitos, URL aceptada, prefijo/sufijo/id y contador de envíos de metadatos confirmados. Errores no inventan éxito; valores históricos desconocidos conservanNULL. API y ficha es/en; migración aditiva. |
| Interfaz | Catálogo/depósito/fichas/administración es/en, controles preservados sin refetch por idioma, fixtures320px, modales/avisos/monitor y axe. No sustituye validación humana con lector de pantalla. |
| Actualización | `update.sh` verifica checksum de Release y JAR antes de fetch o parar Java; no aprovisiona ni recrea DB/ES. Candidato con checksum incorrecto/configuración embebida/patrones conocidos se rechaza. No hay rollback automático de esquema. |
| Respaldo y recuperación | Paquete privado0600 sin sobrescribir/symlinks, dump existente validado y manifiestosSHA256; requiere escrituras detenidas confirmadas. Ensayo local de migración doble y restauración de todas las tablas/archivos; procedimiento de vuelta controlada documentado. |
| Estadísticas y expediente | Métricas locales y DataCite separados, tracker opt-in/SRI/DNT/GPC, reportes/export mensuales; expediente interno disponible. No declara certificación ni emite eventos reales sin permiso. |
| Preparación institucional | Tabla concreta de entregas/responsables y propuesta técnica de políticas: `INSTITUTIONAL_ACCEPTANCE.md`. No necesita que el usuario invente datos ni comprenda OIDC para reenviarla. |

## Resultados finales

Los resultados del último código se registran en el apartado «Cierre local
independiente» de `REDUNIV_EXECUTION_PLAN.md`. Corridas fallidas/intermedias no
se consideran aprobadas. El checksum del JAR sirve para verificar bytes, no
para demostrar correspondencia con un tag aprobado que aún no se ha publicado.

## Únicos siguientes pasos que necesitan entrada externa

1. Confirmar existencia/proveedor de acceso institucional y reglas de cuenta,
   o aprobar mantener login local.
2. Configuración privada/autorización DataCite para ensayo Test→staging→producción.
3. ID de estadísticas y aprobación de transferencia; recepción autorizada y
   comprobación del reporte mensual posterior/certificación si se exige.
4. Entorno/permiso de staging, HAProxy/DNS/CA SMTP y cuenta de correo de prueba;
   volumen/condiciones de operación institucional representativos.
5. Responsables y aprobación de políticas/perfiles de preservación/evidencias.
6. Personas para revisión bibliotecaria, accesibilidad, idioma y usabilidad.
7. Seguridad autoriza evaluación/rotación de credenciales históricas y artefactos
   ya publicados; no se altera el historial ni cuentas reales unilateralmente.
8. Autorización de revisión/merge/Release y ventana de despliegue con respaldo.

Detalle reenviable: [INSTITUTIONAL_ACCEPTANCE.md](INSTITUTIONAL_ACCEPTANCE.md).
No se necesita información adicional para repetir las pruebas locales.
