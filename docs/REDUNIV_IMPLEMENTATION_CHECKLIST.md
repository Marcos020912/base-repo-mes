# Seguimiento de implementación RedUniv

Estado de `develop-reduniv` respecto al informe y prototipo. Una casilla marcada
significa implementada y probada en desarrollo, **no** autorizada para producción.
No fusionar con `main` sin aprobación. Actualizar esta lista al terminar cada ítem.

## Implementado en la rama

- [x] Identidad visual, portada científica, ficha con metadatos reales y DOI solo cuando existe.
- [x] Catálogo público separado de borradores, paginación, búsqueda y filtros en servidor.
- [x] Facetas agregadas de tipo, año, acceso, licencia, disciplina,
      institución, idioma y DOI con recuentos sobre resultados publicados;
      estados diferentes para catálogo vacío, filtros sin coincidencias,
      falta de permisos y fallo del servicio.
- [x] Estados de borrador, revisión, publicación y retirada; cola y vista de curación.
- [x] Control de acceso a archivos restringidos o embargados y aviso de versión sucesora.
- [x] Checklist de calidad antes de enviar a revisión e historial editorial.
- [x] Versiones enlazadas y bloqueo de edición de la versión publicada.
- [x] Exportación preliminar APA, BibTeX, RIS y CSL-JSON.
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
      propio ORCID, institución y ROR; se presenta en la ficha pública y se
      asigna al autor correcto al generar metadatos DataCite. Se conserva el
      campo heredado para registros anteriores. La edición general ya no
      elimina los coautores al guardar.

## Pendiente o sujeto a validación

- [ ] Validar migración/retroceso con copia de PostgreSQL y archivos en entorno de prueba.
- [ ] Probar funcionalmente con usuarios reales y revisar accesibilidad WCAG 2.2 AA.
- [ ] Probar el nuevo asistente en un navegador conectado a una instancia de
      prueba: carga de Markdown/ZIP, fallos parciales, envío y nueva versión.
- [ ] Rotar credenciales de producción; pruebas de seguridad y limitación distribuida de solicitudes.
- [ ] Enlaces privados temporales para revisores externos y pruebas de concurrencia editorial.
- [ ] Validar el flujo DOI real con cuenta Repository, prefijo y credenciales
      de DataCite Test/Production; probar resolución pública y conciliar DOI
      manuales existentes antes de activar el modo automático en producción.
- [ ] Integrar búsqueda/verificación en vivo ORCID/ROR y, si se requiere,
      múltiples afiliaciones por un mismo autor; la captura actual valida el
      formato y permite una institución por autor. La búsqueda ORCID necesita
      token de su API y no debe usarse para atribuir identidad por coincidencia
      de nombres; para acreditar titularidad se requiere OAuth ORCID y
      autorización del investigador. ROR dispone de consulta pública v2.
- [ ] Aprobar vocabularios institucionales de licencia/disciplinas y activar
      `repo.scientific.strict-vocabulary=true` después de migrar valores
      heredados; las listas actuales no son un catálogo institucional aprobado.
- [ ] Validación bibliotecaria de citas y estilos adicionales.
- [ ] Completar facetas de autor, formato y proyecto/financiador y validar
      rendimiento de consultas agregadas con volumen real en PostgreSQL.
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
