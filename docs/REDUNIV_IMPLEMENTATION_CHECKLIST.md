# Seguimiento de implementación RedUniv

Estado de `develop-reduniv` respecto al informe y prototipo. Una casilla marcada
significa implementada y probada en desarrollo, **no** autorizada para producción.
No fusionar con `main` sin aprobación. Actualizar esta lista al terminar cada ítem.

## Implementado en la rama

- [x] Monitor de transferencias en el asistente, fichas autenticada/pública,
      Curación y revisión externa: progreso
      de subida con XHR, lectura de descarga por streaming, tamaño indeterminado
      cuando no existe Content-Length, cancelación y limpieza de finalizadas.
      El historial es por pestaña/página; «Descarga preparada» no certifica la
      escritura en disco. Cancelar la conexión no revierte bytes ya aceptados
      por el servidor. E2E verifica descarga completada, fallo de subida y axe
      del panel real sin infracciones.

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
- [x] Alerta SMTP opt-in, resumida una vez por auditoría completada con
      `MISMATCH` o `MISSING_FILE`. `deploy.sh` pregunta si activar el barrido
      semanal y la dirección `repo.fixity.alert-to` (vacía = sin correo);
      la curación muestra si el envío tuvo éxito o falló. Un error SMTP no
      convierte una auditoría de archivos completada en fallida. La salida
      de correo tiene tiempos máximos configurados en despliegues nuevos.
- [x] Ensayo aislado de migración y restauración en
      `tools/migrations/rehearse.sh`: clúster PostgreSQL efímero por socket
      local, migración aplicada dos veces, comparación de esquema y de datos
      antes/después de `pg_dump`/`pg_restore`, y copia/restauración de archivos.
      Pasó con fixture y con un dump/árbol de archivos de prueba. Aún falta
      repetirlo con una copia real de staging y arrancar la app restaurada.
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
- [x] Dos modos de contenido en el asistente: descripción y archivos separados,
      o un ZIP integral con `description/description.md`, imágenes auxiliares
      bajo `description/` y datos en la raíz. El backend pre-valida estructura,
      extensión, nombres en conflicto, rutas y límites antes de guardar;
      los ZIP requieren revisar la ficha renderizada antes de enviar a curación.
      Smoke test local con Chrome y H2: tres depósitos correctos, Markdown con
      imagen, rechazo de ZIP sin descripción o con extensión incorrecta y
      envío del paquete a revisión tras la vista previa real. También simula
      una carga de datos interrumpida: conserva el borrador, permite repararlo
      desde su ficha y enviarlo después. Una versión publicada sintética en
      H2 permite probar «Crear nueva versión»: vínculo, DOI conceptual heredado
      y archivos seleccionados de nuevo; no registra un DOI real.
- [x] Ambos endpoints ZIP rechazan rutas absolutas o con `..`, componentes
      vacíos, caracteres de control y nombres duplicados o con conflicto
      archivo/directorio. El ZIP de descripción solo acepta `description.md`
      no vacío e imágenes auxiliares; validación previa evita cargas parciales
      por estos errores. Pruebas unitarias de rutas y E2E con ZIP adversariales.
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
- [x] Lectura local confinada a `repo.basepath`: descargas públicas,
      revisión externa/interna y ZIPs resuelven symlinks y rechazan un
      `contentUri` que salga del árbol real del repositorio, antes de enviar
      bytes. Revisar archivos heredados fuera de esa ruta antes de desplegar.
- [x] Herramienta de inventario de solo lectura `tools/storage/audit_content_paths.py`
      para exportación CSV de `content_information`: compara rutas reales con
      `repo.basepath`, identifica symlinks externos, referencias remotas y
      archivos faltantes, y puede generar informe privado `0600`. Probada con
      fixture local; aún falta ejecutarla sobre copia real de staging.
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
      que el ZIP completo solo se ofrece con acceso abierto. El modo de carga
      ZIP integral se audita también en escritorio y a 320 px. Total 43 estados
      sin infracciones automáticas.
      La revisión manual y de contenido real permanece pendiente.
- [x] Limitación distribuida de intentos de inicio de sesión, registro,
      verificación, reenvío y cambio de contraseña mediante PostgreSQL:
      ventanas por cuenta+IP y por IP, claves HMAC en vez de datos personales,
      respuesta 429 con `Retry-After` y limpieza de ventanas expiradas.
      HAProxy sobrescribe las cabeceras de reenvío para evitar IP falsificada;
      la plantilla ya no contiene una clave JWT utilizable.

## Pendiente o sujeto a validación

- [ ] Validar migración/retroceso con copia **real** de PostgreSQL y archivos
      de staging, medir tiempos y arrancar la aplicación restaurada.
- [ ] Inventariar `contentUri` heredados que apunten fuera de `repo.basepath`
      ejecutando la herramienta sobre copia real de staging; migrar archivos o
      documentar una solución de almacenamiento aprobada antes de activar las
      descargas restringidas por ruta.
- [ ] Probar funcionalmente con usuarios reales y revisar accesibilidad WCAG 2.2 AA.
- [ ] Completar pruebas del asistente con usuarios en staging: fallos de red o
      almacenamiento reales a mitad de subida, nueva versión con datos reales y
      comportamiento con PostgreSQL/HAProxy. La prueba local automatizada con
      Chrome y H2 ya cubre cargas Markdown/ZIP, validación, envío, reparación
      de un fallo de red simulado y derivación de versión sintética.
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
- [ ] Dimensionar I/O y pool JDBC para la auditoría, configurar/validar una
      dirección institucional real para las alertas y definir política de
      archivos anteriores sin huella.
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
Para activar el aviso por correo, configure `repo.fixity.alert-to` con un único
buzón y compruebe la entrega con una auditoría de prueba que detecte una
incidencia controlada. En `deploy.sh`, responda **N** a «¿Reutilizarla sin
volver a pedir datos?» para cambiar el buzón; Enter conserva el anterior y
`-` lo desactiva. Sin dirección configurada, la auditoría sigue funcionando
pero no sale ningún correo. Los despliegues nuevos fijan en 10 s los tiempos
máximos SMTP de conexión, lectura y escritura; para una instalación anterior
que reutiliza la configuración sin reescribirla, añada esos valores de forma
controlada antes de activar las alertas.
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

### Seguimiento de descargas públicas
- [x] Ficha pública: archivos individuales, ZIP completo y exportaciones de citas usan el monitor de transferencias.
- [x] Funciona sin cargar autenticación; la cancelación se informa sin mostrar un error técnico.
- [x] Comprobación Chrome aislada: descarga anónima y cancelación; auditoría axe: 43 estados, 0 infracciones automáticas.
- [ ] Validar este flujo con archivos grandes y endpoints públicos reales en staging; el monitor mantiene el archivo en memoria antes de ofrecer su guardado.
- [x] E2E real local Chrome + JAR + H2: contexto sin token descarga CSV, ZIP válido con description.md y datos, y BibTeX; verifica tres transferencias completadas y ausencia de errores JavaScript. La publicación usa DOI sintético, no DataCite.
- [x] Prueba reproducible del monitor (`node tools/e2e/transfer-smoke.cjs`): stream HTTP lento sin tamaño, cancelación con cierre de conexión, éxito, error y limpieza, sin sesión ni servicios externos. No demuestra rendimiento de archivos masivos ni rollback de subidas.
- [x] Monitor de subidas: prueba HTTP local de multipart/201, rechazo 400 con mensaje legible y cancelación XHR con cierre de conexión después de que la petición llega al servidor. No valida rollback del almacenamiento.
- [x] Monitor accesible por teclado: foco al abrir, botón Cerrar/Escape y retorno al activador. Prueba Chrome de foco y aria-expanded pasó; panel no modal, sin atrapamiento de foco.
- [x] El monitor distingue archivo enviado de confirmación del servidor: mantiene petición activa/progreso indeterminado hasta respuesta satisfactoria. Prueba HTTP con respuesta retenida y cancelación aprobada.
- [x] Curación tolera informes de calidad sin blockers; smoke Chrome/JAR/H2 valida vista previa completa y descarga de revisión monitorizada.
- [x] Archivos de revisión y paquetes de preservación usan monitor de transferencias autenticado. Prueba real Chrome/JAR/H2 verifica también descarga del paquete, integridad ZIP, datos/descripción, JSON y hashes BagIt payload/tag contra los bytes incluidos. No certifica perfiles externos RO-Crate/OAIS.
- [x] Revisión externa: descarga monitorizada con X-Review-Token y cache no-store; el monitor rechaza origen distinto y redirecciones. E2E local Chrome/JAR/H2 crea enlace temporal, abre contexto sin sesión, verifica retirada del fragmento y descarga completada. No sustituye pruebas HAProxy/logs/caché institucionales.
- [x] Revisión externa local E2E: no-store en descarga y rechazo, revocación DELETE desde Curación y denegación de nueva descarga desde página ya abierta; monitor/mensaje muestran fallo. No retira copias previamente descargadas ni prueba caché del HAProxy.
- [x] Errores de descarga JSON muestran detail/message legible sin prefijar código HTTP; revisión privada usa mensaje genérico explícito. Prueba Chrome/HTTP de problem+json y ocultación privada aprobada.

### Regresión conjunta local — 2026-10-08
- Gradle sin perfil minimal: 43 suites, 452 pruebas, 0 fallos/errores/omitidas.
- JAR minimal construido correctamente; ambos smoke tests Chrome/HTTP y Chrome/JAR/H2 completados.
- Axe: 43 estados, 0 infracciones automáticas.
- Esta evidencia no cierra los pendientes institucionales, staging, archivos masivos ni WCAG manual enumerados arriba.
- Comandos reproducibles en `tools/e2e/README.md`; `-Dprofile=minimal test` solo ejecuta la suite documental.

### E2E PostgreSQL aislado — 2026-10-08
- [x] `E2E_POSTGRES=1 node tools/e2e/wizard-smoke.cjs` pasó en PostgreSQL 18 efímero: asistente, ZIP, recuperación, versiones, Curación/preservación, acceso externo/revocación y descargas públicas.
- Clúster nuevo loopback/SCRAM con credenciales aleatorias; sin tocar bases existentes. Esquema Hibernate nuevo: los pendientes de migración/restauración con staging real y HAProxy no se consideran cerrados.
- [x] Ensayo PostgreSQL sintético con arranque restaurado: `E2E_POSTGRES=1 E2E_POSTGRES_RESTORE=1 node tools/e2e/wizard-smoke.cjs` pasó. Aplica migración dos veces, dump/restore a DB nueva, reinicia JAR, verifica login/dataset/CSV y hashes de archivos sin cambios. Esquema inicial actual; no cierra migración legacy ni staging real.
- [x] Restauración sintética reforzada: recuentos+huellas JSON ordenado de todas las tablas public iguales, segundo arranque con ddl-auto=validate (sin reparación Hibernate), login y descarga aprobados. No demuestra upgrade desde esquemas legacy reales.
- [x] Inventario de contentUri integrado con PostgreSQL restaurado real del fixture: exportación SQL CSV no vacía y auditoría --strict sin anomalías, sin modificar datos. Ejecución completa aprobada; inventario legacy de staging sigue pendiente.
- [x] Login accesible con JWT guardado inválido tras reinicio: no redirige solo por existencia del token. E2E PostgreSQL restaurado conserva sesión vieja y verifica login nuevo que la sustituye, sin limpiar localStorage manualmente.
- [x] Registro accesible con token viejo, sin redirección automática. `node tools/e2e/auth-pages-smoke.cjs` pasó: acceso a login/registro, confirmación incorrecta sin petición y error login legible; no prueba correo ni registro backend.
- [x] Verificación de correo: acciones verificar/reenviar mutuamente bloqueadas en curso, progreso anunciado y éxito limpia error previo. Prueba Chrome de reenvío503->200 sin duplicados y verify200->login pasó; SMTP/código reales no probados por este escenario.
- [x] Registro/verificación contra JavaMail y PostgreSQL18 reales con SMTP loopback de prueba: registro201, recepción código6dígitos, bloqueo previo sin token, verify200, loginUSER y código consumido rechazado400. Ejecución completa aprobada; STARTTLS/autenticación/entrega institucional siguen pendientes.
- [x] Recuperación SMTP451 local con PostgreSQL real: cuenta conservada sin acceso, registro duplicado409, reenvío503 seguro, correo recuperado->código->verificación/loginUSER. Smoke completo pasó; fallo institucional de lookup/TLS sigue fuera de esta prueba.
- [x] E2E PostgreSQL18+SMTP: usuario verificado ve catálogo compartido, administración403, edición de borrador ajeno403; suspensión invalida token existente401 y login indica cuenta restringida. Corregido AccessDeniedHandler para no convertir falta de rol en «inicie sesión».
- [x] Corregida precisión de invalidación tras cambiar contraseña: claim JWT firmado passwordVersion evita comparar iat en segundos con timestamp fraccionario. Tres unit tests JUnit4 y E2E PG18+SMTP probaron rechazo token antiguo y nueva sesión inmediata válida. Tokens antiguos sin claim mantienen chequeo conservador anterior.
- [x] Compatibilidad JWT local sin passwordVersion probada: rechazo antes del cambio de contraseña y aceptación después. Regresión Java completa: 44 suites, 457 pruebas, 0 fallos/errores/omitidas; E2E combinado PostgreSQL18+SMTP+restauración pasó.
- [x] Login/registro sin envíos duplicados en curso, aria-busy y errores noJSON legibles. Prueba ChromeUI verifica requestSubmit duplicado, rechazoHTML y registro503 recuperable->verificación; aviso de cuenta restringida construido con nodos de texto.
- [x] Cambio de contraseña UI bloquea solicitudes concurrentes, recupera botón tras rechazo y mantiene bloqueo hasta logout tras éxito; prueba Chrome confirma eliminación del token.
- [x] Reenvío de verificación valida correo vacío/inválido antes de enviar: prueba Chrome verifica cero solicitudes inválidas y mantiene recuperación/éxito. Sintaxis JS y diff-check aprobados.
- [x] Login valida estructura mínima de respuesta exitosa (token, usuario y rol existente) antes de guardar sesión; prueba Chrome rechaza HTTP200 vacío sin sobrescribir sesión y confirma login válido. No sustituye la validación criptográfica del JWT en backend.
- [x] Regresión tras los cambios UI de login/cuenta/verificación: JAR reconstruido JDK21; `E2E_POSTGRES=1 E2E_MAIL=1 E2E_POSTGRES_RESTORE=1 node tools/e2e/wizard-smoke.cjs` pasó (SMTP451, multiusuario, asistente, descargas, preservación, revisión externa y restauración). Axe repitió 43 estados con cero infracciones automáticas. No cierra validaciones institucionales, legacy ni WCAG manual.
