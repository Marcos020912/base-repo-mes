# Seguimiento de implementación RedUniv

Estado de `develop-reduniv` respecto al informe y prototipo. Una casilla marcada
significa implementada y probada en desarrollo, **no** autorizada para producción.
No fusionar con `main` sin aprobación. Actualizar esta lista al terminar cada ítem.

## Implementado en la rama

- [x] Identidad visual, portada científica, ficha con metadatos reales y DOI solo cuando existe.
- [x] Catálogo público separado de borradores, paginación, búsqueda y filtros en servidor.
- [x] Facetas agregadas de tipo, autoría, año, acceso, licencia, disciplina,
      institución, idioma, formato MIME y DOI con recuentos sobre resultados publicados;
      estados diferentes para catálogo vacío, filtros sin coincidencias,
      falta de permisos y fallo del servicio.
- [x] Estados de borrador, revisión, publicación y retirada; cola y vista de curación.
- [x] Control de acceso a archivos restringidos o embargados y aviso de versión sucesora.
- [x] Checklist de calidad antes de enviar a revisión e historial editorial.
- [x] Versiones enlazadas y bloqueo de edición de la versión publicada.
- [x] Exportación preliminar APA, Vancouver, Chicago, IEEE, BibTeX, RIS y
      CSL-JSON para la versión publicada. Pendiente validación bibliotecaria.
- [x] SHA-256 al cargar por la interfaz web, comprobación manual por curador y
      estado independiente visible en la ficha pública. Auditoría programada
      **opcional** (desactivada por defecto).
- [x] Cliente backend DataCite y mapeo de metadatos obligatorios y opcionales
      seguros (licencia, materias, institución/ROR y ORCID cuando hay un solo
      autor), probados contra HTTP simulado; desactivados por defecto y sin
      credenciales en Git.
- [x] Reserva DOI Draft conceptual y por versión, registro persistente,
      reintentos/reconciliación, publicación Findable por curador y landing
      permanente; despliegue pregunta la configuración DOI sin mostrar secretos.
      Una URL base HTTPS válida es requisito antes de la reserva.
- [x] Asistente de depósito en cuatro pasos (identidad, ficha científica,
      contenido y vista previa), con validación por etapa, tipos de archivo,
      guardado como borrador o envío a revisión. Antes de enviar un borrador
      existente, el autor ve metadatos, descripción renderizada, archivos y
      bloqueos de calidad. Si falla una carga, conserva el enlace al borrador.
      Los metadatos del asistente se guardan localmente por usuario y depósito
      durante 14 días; los archivos deben reseleccionarse tras recargar.
- [x] Recuperación del registro cuando falla SMTP y rutas de verificación accesibles.
- [x] Cambio de contraseña propia, revocación de tokens previos y respuesta 401 anónima.
- [x] Ítem 7 (curación/preservación): auditorías SHA-256 manuales y programadas
      con bloqueo distribuido PostgreSQL, histórico de ejecuciones y métricas;
      eventos de procedencia de altas/bajas de archivos web y exportación ZIP
      curatorial con metadatos, archivos, historial y manifiesto de huellas
      iniciales. Sugerencias configurables de licencia y disciplina en formularios.
- [x] Relaciones científicas tipadas DOI/URL con semántica DataCite,
      editables solo por el autor mientras el depósito sea borrador,
      visibles en la ficha pública y enviadas al DOI de versión.
- [x] Identidad científica por autor: cada creador del recurso puede tener su
      propio ORCID y hasta diez instituciones/ROR ordenadas; se presentan en la
      ficha pública y se asignan al autor correcto al generar metadatos DataCite.
      Se conserva el
      campo heredado para registros anteriores. La edición general ya no
      elimina los coautores al guardar.
- [x] Búsqueda institucional ROR v2 bajo demanda en el editor de autores,
      con URL de servicio fija, límites de consulta y respuesta, timeout y
      opción `repo.scientific.ror-lookup.enabled=false` si la VM no tiene salida.
- [x] Enlaces privados temporales de revisión emitidos/revocables por curación:
      token aleatorio de 256 bits, solo huella SHA-256 en PostgreSQL, caducidad
      máxima de 14 días, vista de solo lectura y descargas. El token viaja en
      fragmento de URL y después en cabecera, no en la ruta del servidor.
- [x] Financiación y proyectos estructurados por versión: edición solo del autor
      en borrador, ROR opcional del financiador, vista pública, facetas de
      financiador/proyecto y `fundingReferences` en DOI DataCite. Requiere la
      tabla `scientific_funding` de la migración antes del despliegue.
- [x] El arranque ya no vuelca propiedades de entorno en los logs; el filtro
      anterior podía revelar `repo.auth.jwtSecret`.
- [x] Limitación distribuida de intentos de inicio de sesión, registro,
      verificación, reenvío y cambio de contraseña mediante PostgreSQL:
      ventanas por cuenta+IP y por IP, claves HMAC en vez de datos personales,
      respuesta 429 con `Retry-After` y limpieza de ventanas expiradas.
      HAProxy sobrescribe las cabeceras de reenvío para evitar IP falsificada;
      la plantilla ya no contiene una clave JWT utilizable.

## Pendiente o sujeto a validación

- [ ] Validar migración/retroceso con copia de PostgreSQL y archivos en entorno de prueba.
- [ ] Probar funcionalmente con usuarios reales y revisar accesibilidad WCAG 2.2 AA.
- [ ] Probar el nuevo asistente en un navegador conectado a una instancia de
      prueba: carga de Markdown/ZIP, fallos parciales, envío y nueva versión.
- [ ] Rotar credenciales/JWT de producción y revisar logs históricos; pruebas
      de seguridad y de carga del limitador con varias instancias y HAProxy
      realmente desplegado. Restringir acceso directo al backend.
- [ ] Probar con revisores externos el acceso temporal detrás de HAProxy,
      políticas de logs/caché y pruebas de concurrencia editorial.
- [ ] Validar el flujo DOI real con cuenta Repository, prefijo y credenciales
      de DataCite Test/Production; probar resolución pública y conciliar DOI
      manuales existentes antes de activar el modo automático en producción.
- [ ] Integrar autenticación/verificación ORCID; la captura actual valida solo
      el formato y ya permite varias instituciones por autor. La búsqueda ORCID necesita
      token de su API y no debe usarse para atribuir identidad por coincidencia
      de nombres; para acreditar titularidad se requiere OAuth ORCID y
      autorización del investigador. La consulta ROR necesita salida a Internet
      desde la VM o un proxy institucional.
- [ ] Aprobar vocabularios institucionales de licencia/disciplinas y activar
      `repo.scientific.strict-vocabulary=true` después de migrar valores
      heredados; las listas actuales no son un catálogo institucional aprobado.
- [ ] Validación bibliotecaria de todos los estilos de cita y del orden de
      autoría heredado; los nuevos formatos aún son preliminares.
- [ ] Validar rendimiento de facetas de proyecto/financiador y otras consultas
      agregadas con volumen real en PostgreSQL; validar valores institucionales
      de proyectos/financiadores y metadatos de financiación en DataCite Test.
- [ ] Dimensionar I/O y pool JDBC para la auditoría, configurar alerta externa
      por `MISMATCH`/`MISSING_FILE` y política de archivos anteriores sin huella.
- [ ] Validar paquetes de preservación y procedencia en almacenamiento externo,
      firmar manifiestos si la institución lo requiere e internacionalizar la UI.

## Operación de la auditoría SHA-256

La verificación manual usa `POST /api/v1/scientific/{id}/fixity?path=...` y exige
rol CURATOR o ADMINISTRATOR. Compara el archivo actual con la huella registrada
al subirlo, sin modificar esa referencia. Posibles estados: `MATCH`, `MISMATCH`,
`MISSING_FILE`, `NO_BASELINE`, `UNSUPPORTED_URI`, `READ_ERROR`.

Después de aplicar la migración, la comprobación semanal puede activarse con
`repo.fixity.enabled=true`; su cron por defecto es `0 0 3 * * SUN` y puede
cambiarse con `repo.fixity.cron`. El servicio usa `pg_try_advisory_lock(6413,7)`:
solo una instancia ejecuta el barrido a la vez. También puede iniciarse en
Curación o por `POST /api/v1/scientific/preservation/audits`. Historial:
`GET /api/v1/scientific/preservation/audits`; métricas:
`GET /api/v1/scientific/preservation/metrics`. El servicio mantiene una conexión
JDBC durante todo el barrido; dimensionar pool e I/O antes de habilitarlo.
`NO_BASELINE` identifica archivos anteriores sin huella; no se adopta la huella
actual automáticamente porque ocultaría una alteración previa. El paquete
`GET /api/v1/scientific/preservation/{id}/package` está reservado a curadores;
su manifiesto contiene huellas **al ingreso**, no certifica el estado actual.
Los eventos `STORED`/`DELETED` se registran en las rutas web integradas, no en
todas las cargas realizadas por clientes legados o directamente en el disco.
Las sugerencias del depósito usan `repo.scientific.licenses` y
`repo.scientific.disciplines` (valores separados por comas). Actualmente
permiten texto libre para no rechazar depósitos previos. La opción
`repo.scientific.strict-vocabulary=true` hace que el backend rechace nuevos
valores fuera de esas listas; definir y aprobar la taxonomía y migrar datos
heredados antes de activarla.
