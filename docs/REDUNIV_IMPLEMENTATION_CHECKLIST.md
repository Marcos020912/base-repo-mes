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
- [x] El ZIP curatorial incluye ahora un RO-Crate 1.2 adjunto
      (`ro-crate-metadata.json` en la raíz) con entidad Dataset, autores,
      versión/DOI publicado y entidades File. Las huellas `sha256` del crate
      se calculan sobre los bytes realmente empaquetados, distintas del
      manifiesto histórico con huellas al ingreso. Sin licencia informada se
      declara explícitamente que no se conceden permisos de reutilización.
      Un ZIP de prueba pasó los requisitos obligatorios del validador
      `roc-validator` 0.11.4; siguen cinco recomendaciones ligadas a URL de
      licencia y URL/ROR/contacto institucional no disponibles.
- [x] El ZIP curatorial incluye `preservation/prov.jsonld`, proyección
      W3C PROV-O de los eventos de archivo efectivamente registrados:
      actividades de carga/eliminación, entidades, agentes, ubicación, tiempo
      y SHA-256. Conserva `provenance.json` interno y no inventa una carga
      para archivos heredados sin evento de origen.
- [x] El ZIP curatorial adopta además el formato de transferencia BagIt 1.0:
      `data/`, `bagit.txt`, `manifest-sha256.txt` de los bytes actuales y
      `tagmanifest-sha256.txt` de metadatos/RO-Crate. La huella histórica
      `preservation/manifest-sha256.txt` permanece distinta. BagIt no implica
      por sí solo conformidad con los perfiles institucionales OAIS. La prueba
      comprueba todas las rutas y huellas del ZIP real; `bagit-python` 1.9.0
      valida el artefacto con nombre simple, pero no decodifica `%25` de un
      nombre con `%` como exige RFC 8493 (issue #157 del proyecto).
- [x] ZIP público de distribución de una versión publicada en
      `GET /api/v1/public/resources/{id}/archive`: entrega descripción y
      archivos originales sin historial ni metadatos internos de curación,
      solo para acceso abierto o embargo vencido. La ficha pública muestra
      el enlace cuando procede. Es una base DIP, no un perfil OAIS acordado.
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
- [x] Autenticación ORCID OAuth opcional por autor seleccionado: estado aleatorio
      de un solo uso (solo SHA-256 persistido), caducidad de diez minutos,
      intercambio del código en backend y distintivo separado para ORCID
      autenticado frente a declarado. Solo el depositante puede iniciarla en
      borrador; la devolución vuelve a comprobar cuenta y propiedad. Las
      credenciales y URL de retorno se configuran en `deploy.sh`, no en JS/Git.
      **OAuth prueba el control de la cuenta ORCID, no el nombre del autor**.
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
- [x] Línea base de accesibilidad para teclado: enlace visible al recibir foco
      para saltar al contenido en las trece páginas, `<main>` enfocables,
      navegación con nombre accesible, botones de cierre identificados,
      indicador de foco de dos colores y respeto de movimiento reducido.
      **No equivale a conformidad WCAG**; quedan pruebas con tecnologías de
      asistencia y revisión de formularios, contraste y componentes dinámicos.
- [x] Auditoría automática reproducible (`tools/a11y`): axe-core en Chrome por
      HTTP local revisa las 13 páginas, diálogos y cuatro pasos del asistente
      sin backend ni cuentas. Detectó y se corrigió contraste 1.88:1 de la
      marca en revisión privada; ahora 26 estados sin infracciones automáticas.
      Se eliminó el `autofocus` que saltaba el primer enlace en login/registro;
      el auditor comprueba primer Tab y activación con Enter en las 13 páginas.
      Una segunda pasada a 320 px detectó y corrigió desbordamiento horizontal
      de Curación; dos estados dinámicos simulados de la ficha pública verifican
      que el ZIP completo solo se ofrece con acceso abierto. Total 41 estados
      sin infracciones automáticas.
      La revisión manual y de contenido real permanece pendiente.
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
- [ ] Validar ORCID OAuth con credenciales Sandbox reales y usuarios de prueba,
      registrar el callback HTTPS institucional y acordar política para
      coautores que no pueden autenticarse con la cuenta del depositante.
      La búsqueda ORCID por nombre no debe utilizarse para atribuir identidad;
      ROR necesita salida a Internet desde la VM o un proxy institucional.
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
- [ ] Validar RO-Crate con curadores externos, aportar URL/ROR/contacto
      institucional aprobados y completar perfiles OAIS SIP/AIP/DIP y
      cobertura de procedencia de cargas legadas. Validar la proyección
      PROV-O con consumidores externos y paquetes de preservación
      y procedencia en almacenamiento externo,
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
