# Plan de cierre RedUniv

Rama exclusiva: `develop-reduniv`. Sin merge, push ni cambios en producción.

Cada casilla se marca únicamente con implementación y evidencia. Las validaciones institucionales no se sustituyen por pruebas simuladas.

## Requisitos trazados a los tres documentos

- [ ] **S01 — Rotar contraseña expuesta en revisión**. Institucional: no modificar cuentas reales sin autorización.
- [x] **S02 — Suite automática corregida y ejecutable**. Implementado local: regresión Java21/JUnit4 offline, E2E PostgreSQL18/SMTP/Chrome/restauración y axe ejecutables; fechas/alcance exactos por bloque.
- [ ] **S03 — Eliminar secretos/credenciales predeterminados**. Parcial: plantillas sin credenciales literales; gate actual sin hallazgos y cuatro tests. Historial: 102 ocurrencias por evaluar/rotar; sin reescritura y artefactos pendientes.
- [ ] **S04 — Autorización por recurso, no solo autenticación**. Base local: endpoints científicos y E2E edición ajena denegada; ampliar auditoría de todos los endpoints.
- [ ] **S05 — Rate limiting, auditoría y políticas seguras**. Base local: limitador PostgreSQL y plantilla HAProxy; prueba real multiinstancia y rotación pendientes.
- [ ] **S06 — Lectura, depósito, curación, publicación y administración separados**. Base local: roles USER/CURATOR/ADMINISTRATOR y controles por estado; verificar matriz completa.
- [ ] **S07 — Ocultar acciones destructivas no permitidas**. Base local: vistas de autor/curación; comprobar cada estado, rol y endpoint.
- [ ] **F01 — Tipo, estado, título, versión exacta y DOI prioritarios**. Base local: `public-resource.html/js`, `resource.html/js`; revisión visual completa contra maqueta pendiente.
- [ ] **F02 — Autores con ORCID, instituciones con ROR por autor**. Base local: identidades/afiliaciones y DataCite mapper; pruebas individuales y OAuth institucional pendientes.
- [ ] **F03 — Licencia y condiciones de acceso obligatorias**. Base local: checklist de calidad/acceso; probar ausencia de licencia, embargo y restricción en todos los flujos.
- [ ] **F04 — Copiar cita y elegir APA/Vancouver/Chicago/IEEE**. Base local: bloque de cita/exportaciones; validación bibliotecaria pendiente.
- [ ] **F05 — Exportar BibTeX, RIS y CSL-JSON para versión exacta**. Base local: servicio de cita; verificar round-trip/validez y como máximo dos acciones (§11).
- [x] **F06 — Aviso de versión antigua, DOI conceptual y de versión**. Implementado local: historial paginado de familia explícita y aviso de publicación más reciente por fecha, preserva retiradas y oculta borradores; DOI conceptual/de versión conservados. Tests/E2E/restore aprobados. DataCite real pendiente.
- [x] **F07 — Fecha de publicación y última actualización visibles**. Implementado local: publishedAt y lastUpdate públicos visibles; E2E Chrome/PostgreSQL verificó última actualización.
- [x] **F08 — Resumen, método, cobertura y palabras clave**. Implementado local: resumen estructurado, fechas de cobertura validadas y región textual en creación/edición/ficha pública/DataCite. E2E PostgreSQL/restauración y pruebas de validación aprobados.
- [ ] **F09 — Archivos: formato, tamaño, checksum y acceso**. Base local: lista de archivos/huellas/monitor; selector nativo y volumen real pendientes.
- [x] **F10 — Procedencia: producción y procesamiento de datos**. Implementado local: producción/origen, procesamiento y herramientas/versiones, públicas por versión e inmutables al publicar; formularios/preview/DataCite/RO-Crate/PROV. Es declaración del autor, no ejecución verificada. Tests/JPA/E2E aprobados.
- [ ] **F11 — Artículos, software, proyectos, financiación y datasets relacionados**. Base local: relaciones tipadas y funding; comprobar visibilidad y representación máquina de todas las categorías.
- [ ] **F12 — Última verificación y política de preservación aplicable**. Parcial: fixity existe; política institucional y su presentación aprobada pendientes.
- [ ] **F13 — Historial de cambios de metadatos y versiones publicadas**. Parcial: historial editorial existe; comprobar presentación pública según política.
- [x] **F14 — Compartir y exportar metadatos como acciones científicas**. Implementado local: copia de landing permanente y exportación JSON versionada de ficha pública (sin archivos privados), además de citas. E2E Chrome/PostgreSQL aprobado.
- [ ] **F15 — Publicado inmutable; cambios crean nueva versión**. Base local: workflow/versiones y E2E sintético; probar todos los endpoints de escritura.
- [ ] **F16 — Retirada controlada sustituye borrado publicado; tombstone**. Base local: estado WITHDRAWN/HTTP410; auditoría de autorización y permanencia pendientes.
- [ ] **C01 — Inicio y búsqueda global con identidad institucional**. Base local: catálogo público y hero; revisar maqueta sin copiar cifras ficticias.
- [ ] **C02 — Facetas: área, institución, autor, año, tipo, licencia**. Base local: API catálogo/facetas y vistas; pruebas de combinaciones/volumen pendientes.
- [ ] **C03 — Facetas: acceso, formato, proyecto/financiador, DOI, idioma**. Base local: API/funding; comprobar todas visibles y utilizables en catálogo público.
- [ ] **C04 — Búsqueda, orden y paginación en servidor**. Base local: catálogo; no sustituir por filtro de 200 registros en cliente.
- [x] **C05 — Filtros, página y orden reproducibles en URL**. Implementado local: pushState/replace inicial, popstate restaurando valores ausentes, filtro/orden/página y cancelando debounce. Test Chrome de ambos catálogos aprobado (API fixture, no prueba de búsqueda backend).
- [x] **C06 — Distinguir vacío, cero coincidencias, permisos y caída**. Implementado local: mensajes y acciones de limpiar filtros, soporte y reintento diferenciados, sin códigos HTTP ni errores técnicos al usuario. Test Chrome de ambos catálogos y recuperación aprobado.
- [x] **C07 — Colecciones temáticas e institucionales**. Implementado local: colecciones temáticas/institucionales persistentes, gestión curador/admin con modales y catálogo público paginado solo PUBLISHED. Cuatro pruebas JPA y E2E PostgreSQL/SMTP/Chrome/restauración aprobados; no duplica ni elimina datasets.
- [ ] **C08 — Políticas, ayuda y guía de citación**. Parcial: nueva `help.html` ofrece guía y condiciones técnicas; políticas institucionales formales aún no aprobadas.
- [ ] **C09 — Métricas públicas con definiciones transparentes**. Parcial: inventario público real de versiones PUBLISHED, política OPEN y colecciones públicas con definiciones/fecha; JPA y E2E aprobados. Uso/descargas y metodología COUNTER/DataCite aún pendientes.
- [x] **W01 — Mis depósitos y tareas de metadatos pendientes**. Implementado local: cola del autor paginada con estado, checklist, explicaciones y siguiente acción; excluye publicados/retirados/huérfanos y datos ajenos. Tres tests y E2E multiusuario/UI aprobados. Rendimiento institucional pendiente.
- [x] **W02 — Etapas: identidad/tipo y autores/organizaciones**. Nueve etapas; E2E Markdown/ZIP/paquete, recuperación y versión; axe 66 estados sin infracciones automáticas. Ver WIZARD_WORKFLOW.md.
- [ ] **W03 — Búsqueda ORCID/ROR**. Parcial: búsqueda ROR y OAuth ORCID; no atribuir identidad mediante búsqueda por nombre sin confirmación.
- [ ] **W04 — Descripción/metodología y archivos/documentación**. Base local: asistente y Markdown/ZIP; validar resumen y cobertura requeridos.
- [x] **W05 — Licencia, acceso, embargo y datos sensibles**. Implementado local: declaración privada del autor, control de revisión y restricción de datos personales/confidenciales; no permite sustituir restricción con embargo. Pruebas unitarias y E2E de formularios/publicación aprobadas. Política jurídica institucional pendiente.
- [x] **W06 — Artículos, software, proyectos y financiación**. Creación con relaciones tipadas/financiación, vista previa y recuperación; E2E persistencia y rechazo de HTTP, axe dinámico aprobado.
- [ ] **W07 — Guardado automático y recuperación**. Base local: metadatos locales14d y borrador servidor; no prometer persistencia automática de archivos seleccionados.
- [ ] **W08 — Revisión automática: errores, avisos, porcentaje y explicación**. Base local: calidad incluye explicación de reutilización por campo y recomendaciones de resumen/cobertura; pendiente aceptación humana y auditoría de todos los mensajes.
- [ ] **W09 — Vista previa de landing y cita antes de enviar**. Parcial: ficha real previa disponible y cita provisional sin DOI inventado añadida al asistente; ZIP muestra manifiesto seleccionado y exige revisión real después de guardarlo. Auditar vista previa integral y estilo bibliotecario.
- [ ] **W10 — Envío a curación/publicación según política**. Base local: flujo revisión/publicación; depende de política institucional.
- [ ] **W11 — Reserva DOI, embargo y acceso privado a revisores**. Base local: DOI Draft/enlaces temporales; pruebas DataCite/HAProxy pendientes.
- [ ] **A01 — Cola y validación de metadatos/archivos**. Base local: Curación/calidad; validación bibliotecaria/formatos real pendiente.
- [ ] **A02 — Control de identificadores, vocabularios y perfiles**. Parcial institucional: vocabularios y perfiles persistentes con propuesta/aprobación, revisión concurrente y reglas congeladas por dataset verificados localmente. Control de identidad/listas institucionales y su aprobación externa no acreditados.
- [x] **A03 — Licencias, privacidad y datos sensibles en curación**. Implementado local: decisión de curación con notas privadas y revisión concurrente; bloquea publicación sensible sin aprobación, revoca al devolver a borrador. E2E aprobado; no sustituye aprobación jurídica institucional.
- [ ] **A04 — Usuarios, roles e integraciones DOI/OIDC/correo**. Parcial: usuarios/DOI/correo/ORCID; OIDC institucional de acceso no equivale a OAuth ORCID.
- [x] **A05 — Estado almacenamiento/fixity, auditoría/reportes operativos**. Implementado local: consola autorizada con volumen local, inventario técnico/fixity, veinte auditorías e informe JSON fechado. Tests/E2E aprobados; no sustituye monitorización y backup institucional.
- [x] **A06 — Metadatos multilingües**. Implementado local: traducciones declaradas de título/resumen por idioma BCP47, persistidas por versión y visibles en ficha/DataCite. Editor compartido sin JSON técnico. No equivale a internacionalización UI.
- [ ] **A07 — Internacionalización sin textos incrustados en JS**. Parcial: catálogos fuente JSON es/en y acceso/login/registro/verificación/cuenta, catálogos público/privado, Mis depósitos/tareas, asistente de nueve etapas y módulos científicos compartidos, usuarios/operaciones y navegación compartida migrados, selector persistente sin borrar formularios. Chrome/catálogos/axe aprobados; demás vistas pendientes.
- [ ] **A08 — Paquetes OAIS SIP/AIP/DIP**. Parcial: BagIt/paquete curatorial/ZIP público; perfiles institucionales completos no acreditados.
- [x] **A09 — RO-Crate y W3C PROV**. Implementado local: declaración científica y eventos técnicos separados, manifiestos/paquetes probados; no inventa actividades ni fechas. Tests/E2E aprobados. Validación externa de perfiles pendiente.
- [ ] **A10 — Auditorías periódicas SHA-256 verificables**. Base local: manual/programada opcional y alertas; política/volumen/buzón real pendientes.
- [ ] **A11 — Métricas COUNTER/DataCite cuando corresponda**. Pendiente: no confundir contadores locales con conformidad COUNTER o integración de eventos DataCite.
- [ ] **A12 — Autoevaluación CoreTrustSeal**. Pendiente: requiere expediente y evidencia organizativa/técnica; no afirmar certificación.
- [ ] **A13 — OpenAPI como contrato y pruebas de compatibilidad**. Contrato científico tipado y gate estructural de 22 rutas implementados; compatibilidad de clientes institucionales y auditoría del resto pendientes.
- [ ] **A14 — WCAG2.2AA, teclado, foco, contraste, etiquetas, errores, lectores**. Parcial: axe y teclado automatizados; revisión manual de recorridos/lectores pendiente.
- [ ] **A15 — Diseño desde360px y estados no solo por color**. Base local: pruebas320px y etiquetas; revisar cada flujo dinámico real.
- [ ] **A16 — Prueba con investigadores, curadores, bibliotecarios y soporte**. Institucional: organizar evaluación funcional sin sustituirla por smoke automatizado.
- [ ] **D01 — Servicio backend desacoplado, API DataCite Test/Production**. Base local: cliente/workflow; no credenciales de producción para pruebas locales.
- [ ] **D02 — POST/dois y metadatos obligatorios; validación previa**. Base local: mapper/client; pruebas HTTP simulado, Test real pendiente.
- [ ] **D03 — Reserva Draft y publicación tras aprobación**. Base local: estados persistidos; no enlazar Draft como DOI resoluble.
- [ ] **D04 — Landing permanente, nunca archivo directo**. Base local: `/datasets/<id>`; verificar disponibilidad externa institucional.
- [ ] **D05 — DOI conceptual y específico de versión**. Base local: workflow/mapper; no reutilizar DOI de versión para contenidos distintos.
- [x] **D06 — HasVersion/IsVersionOf/IsNewVersionOf/IsPreviousVersionOf**. Implementado local: sincroniza IsPreviousVersionOf del predecesor gestionado, preserva sucesores y relaciones locales y permite reintento sin publicar localmente antes de confirmar. Pruebas mock aprobadas; DataCite real pendiente.
- [ ] **D07 — reserve/publish/updateMetadata/get/createVersion/updateUrl**. Base local: operaciones existentes y mantenimiento admin de URLs con vista previa/confirmación contra origen configurado y auditoría. Cuatro pruebas nuevas mock aprobadas; validación institucional y auditoría contractual pendientes.
- [ ] **D08 — Estado, fechas, última sincronización, URL, versión metadatos e historial local**. Base local/parcial: registros/eventos DOI; contrastar todos los campos sugeridos y semántica de retirada.
- [ ] **D09 — Secretos solo backend, nunca JS/Git**. Parcial: gate readonly seguro y árbol actual sin hallazgos. Historial/artefactos y rotación institucional siguen pendientes.
- [ ] **D10 — Sufijo generado por DataCite recomendado**. Desviación documentada: sufijo UUID estable local para idempotencia; recomendación no obligatoria, requiere transparencia institucional.
- [ ] **D11 — Cuenta Repository, prefijo, credenciales y validación Test→staging→production**. Institucional: no resuelto únicamente con código; no dar integración externa por probada.

## Evidencia por bloque

- C07: commit `ed94196`, cuatro pruebas JPA, E2E PostgreSQL/restauración y axe49 estados.

Las casillas de base local permanecen abiertas hasta auditoría individual; esto no significa que su código esté ausente.

### Trabajo en curso
- Inventario público real: API y catálogo implementados; seis pruebas JPA aprobadas (dos nuevas); E2E aprobado.
- F07/F14: última actualización, copia de landing y exportación JSON pública implementadas; pruebas navegador aprobadas.
- D07: mantenimiento de URLs DOI con vista previa/confirmación admin, origen configurado y auditoría; pruebas unitarias aprobadas. No cierra todavía todos los métodos del requisito.

- F07/F14: E2E PG18+SMTP+Chrome+restauración aprobado: última actualización, copiaURL y JSON público. Axe49estados0.
- C09 continúa parcial: inventario real probado, medición de uso y estándares todavía no completos.

- D06: dos pruebas mock de relación inversa y recuperación aprobadas. DataCite real pendiente.
- D07: cuatro pruebas mock de mantenimiento de URL aprobadas; aún abierto para auditoría del contrato completo y validación institucional.

### Evidencia consolidada del bloque 2026-10-08
- Regresión Java:45 suites,469 pruebas,0 fallos/errores/omitidas.
- E2E PG18+SMTP+Chrome+restauración: aprobado; incluye inventario público, exportación JSON, enlace/fecha, cita provisional y autorización de mantenimiento DOI.
- Axe:49 estados,0 infracciones automáticas. Revisión manual sigue pendiente.
- No se han probado credenciales DataCite institucionales ni servicios de producción.
- W09: cita provisional probada en los tres modos; vista previa integral aún parcial.

### Metadatos estructurados y multilingües
- F08/A06: editor creación/edición/autoguardado/vista previa, ficha pública y mapper DataCite; cobertura temporal y BCP47 validados. JPA recarga/eliminación de traducciones probada.
- E2E PG18+SMTP+Chrome+restauración aprobado: campos/traducciones conservados, fechas invertidas rechazadas, tres modos de subida. Axe49estados0; no cierra WCAG manual.
- Regresión completa de metadatos:45 suites475 pruebas Java sin fallos/errores/omitidas; commit local del bloque, sin merge/push/despliegue.

### Evaluación privada y curación
- Declaración del autor en DRAFT; revisión CURATOR/ADMIN en IN_REVIEW; notas fuera de exportaciones públicas y autoguardado local.
- Configuración opcional `repo.privacy.require-assessment` reutilizable por deploy; restricciones sensibles siempre activas.
- Siete pruebas nuevas de servicio/guardas y E2E real de modales, permisos y publicación restringida; axe51 estados sin infracciones automáticas.
- Reglas y límites documentados en `docs/PRIVACY_WORKFLOW.md`.
- Regresión privacidad:46 suites482 pruebas,0 fallos/errores/omitidas; E2E PG18/SMTP/Chrome/restauración completo aprobado (migración doble, tablas idénticas, esquema validado y descarga).

### Tareas consolidadas del autor
- API autenticada y UI en Mis depósitos; paginación en servidor, inspección de calidad solo por página. Tres tests y E2E de ámbito, paginación y UI aprobados; axe51 estados0.
- `docs/DEPOSIT_TASKS.md` explica estados, privacidad y límites de escala sin presentar checklist como aprobación.
- Regresión tareas:47 suites485 pruebas0fallos/errores/omitidas; E2EPG18SMTPChrome/restauración completo aprobado.

### Consola operativa
- Medición FileStore del volumen configurado sin rutas, no compatible/no disponible distintos de cero; dos tests aprobados.
- E2E PG18SMTPChrome/restauración: consulta y exportJSON con esquema/fecha, permisos USER403/anónimo401 y no-store; aprobado. Axe53estados0.
- `docs/OPERATIONS_CONSOLE.md` distingue inventario técnico/últimos estados de verificación de métricas públicas y backups.
- Regresión operaciones:48 suites487 pruebas0fallos/errores/omitidas; E2E completo y restauración aprobados.

### Historial reproducible de catálogo
- `node tools/e2e/catalog-history.cjs`: ambos catálogos, URL inicial, búsqueda, página, orden, Atrás/Adelante, valores ausentes, facetas y recarga aprobados. API/auth fixture; no reemplaza integración de seguridad/búsqueda.
- Build JAR aprobado; axe53estados0. Última regresión backend sin cambios Java:48 suites487pruebas aprobadas en bloque operaciones.

### Estados de búsqueda
- Test Chrome fixture de ambos catálogos: vacío, cero coincidencias, permiso403 y caída503 con ayuda/limpieza/reintento que recupera resultados. JAR aprobado y axe53estados0.

### Procedencia científica
- Campos producción/origen, procesamiento y herramientas/versiones en creación/edición/autoguardado/preview/sucesora/ficha pública y metadatos exportados.
- Declaración contextual en RO-Crate/PROV y Methods DataCite; no genera evidencia de ejecución inexistente. `docs/SCIENTIFIC_PROVENANCE.md`.
- Regresión48 suites490pruebas0fallos/errores/omitidas; axe53estados0; E2E PostgreSQL/SMTP/Chrome/restauración validó declaración, paquete/manifiestos y persistencia.

### Familia pública de versiones
- Tres tests: padres/hijas/hermanas, ciclo, paginación, retiradas, borradores/huérfanos excluidos y ausencia de agrupación por coincidencia DOI. No inventa anterioridad con fechas ausentes/empatadas.
- E2E PG18/SMTP/Chrome/restauración: borrador no visible, publicación sintética de sucesora, aviso con enlace correcto, retirada conservada en familia pública y ficha permanente.
- Regresión49suites493pruebas0fallos/errores/omitidas; JAR y axe53estados0. `docs/PUBLIC_VERSION_HISTORY.md`.

### Contrato científico HTTP v1
- Seguridad declarada diferenciada JWT/público/token revisor; esquemas tipados, binarios, citas autenticadas y tombstone410.
- Gate contra JAR aislado:22 rutas y140 referencias locales resolubles. No reemplaza comportamiento, todos los endpoints o clientes institucionales.
- Regresión50suites495pruebas0fallos/errores/omitidas; después, tres tests contractuales aprobados incluyendo estabilidad por orden. E2EPG18SMTPChrome/restauración completo aprobado; axe53estados0 e historial de ambos catálogos aprobado. `docs/SCIENTIFIC_API_CONTRACT.md`.

### Gobernanza de vocabularios
- Registro LICENSE/DISCIPLINE con propuesta separada de lista aprobada, revisión optimista, motivo/actor/fecha y modales admin. Conserva valores heredados sin modificar; no cambia publicados ni strict-vocabulary.
- Regresión51suites499pruebas0fallos/errores/omitidas; E2EPG18SMTPChrome/restauración aprobado, migración doble y vocabulario aprobado conservado. Axe57estados0.
- `docs/VOCABULARY_ADMINISTRATION.md`; perfiles se implementan y verifican en el bloque siguiente.

### Perfiles de metadatos por versión
- Administración propuesta/aprobación/disponibilidad, defaults seguros, campos adicionales obligatorios y snapshot persistente por dataset. Solo autor DRAFT aplica/quita; IN_REVIEW y publicados protegidos. Hereda snapshot al derivar nueva versión; nunca relaja checks base.
- E2EPG18SMTPChrome/restauración completo aprobado: permisos, modales, revisión409, reglas bloquean envío, desactivación preserva copia, autoría no reemplazada y reglas conservadas después de restore/migración doble/schema validate.
- Regresión52suites503pruebas0fallos/errores/omitidas; después18pruebas dirigidas aprobadas, incluyendo nuevo guard de publicación manual e independencia de la copia al derivar. Axe61estados0; no certifica revisión manual.
- `docs/METADATA_PROFILES.md`. No impone un perfil institucional obligatorio ni acredita aprobación organizativa.

### Asistente de nueve etapas y relaciones durante creación

- W02/W06: etapas separadas, precomprobación local claramente identificada, vista previa completa antes del cierre y confirmación explícita para enviar.
- Relaciones tipadas DOI/HTTPS se guardan con el endpoint existente de autor/DRAFT; vista previa segura, recuperación de metadatos y preselección de nueva versión.
- E2E PostgreSQL/SMTP/Chrome/restauración aprobado: tres modos, subida interrumpida, nueva versión, ningún POST sin confirmar, rechazo de URL HTTP y persistencia SOFTWARE; axe del formulario dinámico sin infracciones.
- Regresión Java 21: 52 suites, 504 pruebas, cero fallos/errores/omitidas. Axe estático: 66 estados, cero infracciones automáticas. No acredita revisión WCAG manual ni certificación institucional.
- Guard de accesibilidad verifica id y tabindex en la misma etiqueta main sin depender del orden de atributos HTML.

### Auditoría de credenciales versionadas

- Siete pruebas Python: placeholders vacíos, fallback inseguro, redacción de tokens/claves, hallazgo histórico eliminado sin modificar Git y resolución segura de entorno/default en deploy.sh.
- Árbol actual: cero patrones detectados. Historial: 102 ocurrencias de propiedades literales en ocho rutas; no son necesariamente 102 secretos activos. No se revelaron valores ni se reescribió historia.
- Plantillas DB/Rabbit/JWT usan configuración externa/env. H2 existente requiere conservar contraseña anterior externa; no se cambian cuentas ni datos. Ver tools/security/README.md.
- JAR reconstruido y E2E PostgreSQL/SMTP/Chrome/restauración aprobado después de retirar defaults. `bash -n deploy.sh` aprobado; no se ejecutó el despliegue ni se modificaron servicios reales.

### Internacionalización — acceso (A07 parcial)

- Catálogos fuente JSON es/en con38claves y bundle estático determinista; claves explícitas, sin sustitución de texto de autor ni HTML.
- Login/registro/verificación migrados, selector persistente sin reload/pérdida de campos; errores locales cambian de idioma, mensajes originales del backend se conservan. Funciona en memoria cuando localStorage está bloqueado.
- Dos tests de coherencia/bindings y Chrome authfixture aprobado, incluidos idioma entre páginas, mensajes dinámicos, credenciales conservadas y login inglés a320px sin desbordamiento/infracciones axe.
- Regresión Java21:52suites504tests0fallos/errores/omitidas. JAR y E2E PostgreSQL/SMTP/Chrome/restauración aprobados después de añadir whitelist deassets; axe estático66estados0.
- Catálogo, depósito, fichas, cuenta, administración y transferencias aún pendientes de migración. No marcar A07 completo ni anunciar plataforma entera bilingüe. Ver tools/i18n/README.md.

### Internacionalización — cuenta y navegación (A07 parcial)

- 63 claves es/en; cuenta personal, cambio de contraseña y toasts locales migrados. Navegación compartida traducida por rutas estables en14páginas; roles y permisos no cambian.
- Solo páginas totalmente migradas cambian idioma del documento. Otras conservan español; nodos traducidos declaran idioma propio. Metadatos de autor no se traducen.
- Guard impide bindings de texto en etiquetas con controles/markup: cuatro pruebas Python; Chrome cuenta en inglés320px sin infracciones axe/desbordamiento, preserva contraseña al cambiar idioma. Authfixture e historial de ambos catálogos aprobados.
- RegresiónJava2152suites504tests0fallos/errores/omitidas; JAR y E2E PostgreSQL/SMTP/Chrome/restauración completo aprobados. No implica traducción completa ni revisión WCAG manual.

### Propiedad de bindings de traducción

- Traducción registrada solo desde HTML estático de confianza o helpers explícitos. Contenido tardío de autores con data-i18n no se traduce ni dispara errores de claves.
- Mensajes externos se desregistran; aviso de cuenta restringida conserva enlace de soporte al cambiar idioma. Guard runtime hoja y poda de nodos retirados.
- Fixture Chrome confirma marcador tardío intacto y enlace de cuenta restringida con respuesta API simulada; auth, historial público/privado y axe66estados0 aprobados. No sustituye prueba de autorización backend.
- JAR reconstruido y E2E PostgreSQL/SMTP/Chrome/restauración completo aprobado después del registro de propiedad. No se modificaron servicios/productivo ni se publicaron ramas.

### A07 — Contenido de administración de usuarios

- Pantalla de usuarios, formularios, estados y confirmación de eliminación traducidos es/en; nombres de cuentas y valores de roles se conservan.
- Parámetros de traducción interpolados solo como texto, nunca HTML; bindings siguen siendo explícitos y de nodos hoja.
- Cuatro pruebas de catálogo, fixture Chrome de usuarios (formularios conservados, confirmación con nombre especial, una sola consulta), regresión de autenticación y axe66estados0 aprobados.
- Pendiente: contenido completo de catálogo, depósito, fichas y restantes pantallas administrativas. A07 no se considera terminado. Sin cambios de backend ni publicación.

### A07 — Catálogos, depósito y operaciones

- Catálogos público/privado: filtros, placeholders, tipos conocidos, facetas por ámbito, paginación, estados de vacío/permiso/caída e inventario es/en. Conservan consultas/historial, valores de filtros y contenido de autores. Autor llamado OPEN no se convierte en un estado de acceso.
- Mis depósitos/tareas: estados, acciones, comprobaciones por código y reglas de perfil conocidas traducidos sin volver a consultar al cambiar idioma.
- Operaciones: snapshot y JSON exportado originales separados de presentación; fechas/números locales, fallo no muestra cifras anteriores como actuales.
- Asistente de nueve etapas: controles, validación, autosave, perfil, metadatos declarados, preflight, vista previa/cita, confirmación y subida es/en. Markdown y declaraciones originales no se traducen. Confirmación específica precede al mensaje genérico; no se envía sin aceptación.
- Módulos compartidos de privacidad, traducciones declaradas, perfiles congelados y transferencias migrados. Cambiar idioma no consulta APIs ni reinicia subidas. Perfiles fallidos mantienen su fallo, transferencias esperan confirmación del servidor.
- 629 claves es/en, seis pruebas Python; Chrome fixtures usuarios/catálogos/historial/Mis depósitos/operaciones/asistente aprobados, inglés320px sin desbordamiento e infracciones automáticas en ámbitos comprobados. Axe estático66estados0. Detectados y corregidos objetivo de filtros y contraste de descartar autosave condicional.
- Regresión Java21:52suites504tests0fallos/errores/omitidas tras corregir guard HTML dependiente del orden de atributos. Los fixtures no sustituyen seguridad/backend ni auditoría manual WCAG.
- Pendiente A07: fichas públicas/privadas y módulos de autores/financiación/relaciones/versiones/DOI, curación, vocabularios/perfiles administrativos, colecciones, ayuda y acceso de revisión. No se anuncia plataforma completa bilingüe ni se cierra A07.

- E2E integrado detectó consumidor de transferencias con ruta absoluta sin runtime i18n (revisión temporal/público). Corregido orden de assets y añadido guard para rutas relativas/absolutas; no se modificaron permisos ni se ampliaron timeouts. Nueva validación integrada aprobada con JAR actual: PostgreSQL18/SMTP/Chrome, seguridad multiusuario, descargas públicas/revisor revocado y restauración con migración doble. Veinticuatro assets estáticos modificados comparados byte a byte con el JAR, sin discrepancias.

### Historial público bilingüe — avance A07 (2026-10-08)
- [x] Etiquetas, paginación, avisos de versión posterior y fallos recuperables del historial público en español/inglés. Versiones, DOI y fechas del servidor permanecen originales; cambiar idioma no dispara consultas.
- [x] Validación inicial: seis pruebas de catálogos y sintaxis JavaScript aprobadas.
- [ ] Regresión de navegador específica del historial y migración del resto de la ficha pública/privada. A07 y el objetivo global continúan pendientes.
- [x] Editores de relaciones, financiación e identidades de autores bilingües; valores científicos, nombres y controles conservados. ORCID declarado sigue distinto de cuenta autenticada y de autoría contrastada.
- [x] Fixture Chrome `node tools/e2e/record-editors-i18n.cjs`: historial y reintento, modales con valores preservados, payloads de relaciones/financiación, resultados ROR originales, sin nuevas consultas por idioma. Es prueba UI aislada, no prueba de autorización del backend.

### Acceso de revisión temporal bilingüe — avance A07 (2026-10-08)
- [x] Vista temporal, paginación, checksum de ingreso y mensajes de acceso/descarga en español/inglés; título, autoría, Markdown y nombres originales preservados.
- [x] Fallos de paginado capturados y lista de archivos limpiada; respuestas anteriores no reemplazan una consulta más reciente.
- [x] Fixture Chrome `node tools/e2e/review-access-i18n.cjs`: eliminación del token del historial, no persistencia en localStorage, cabecera conservada, fallo/revocación, ausencia de token y cambio de idioma sin consultas adicionales. Es evidencia UI aislada, no sustituye seguridad real del backend.
- [ ] Internacionalización de fichas principales y demás áreas pendientes; el objetivo global sigue sin cerrar.

### Ficha pública bilingüe — avance A07 (2026-10-08)
- [x] Ficha publicada es/en: navegación, etiquetas de metadatos, autores, financiación, integridad, traducciones declaradas y acciones de descarga/compartir/exportar. Cita y declaraciones originales no se traducen automáticamente.
- [x] Fixture Chrome `node tools/e2e/public-record-i18n.cjs`: cambios es/en preservan título, Markdown, cita, archivos y selección de idioma de metadatos; sin consultas adicionales. Catálogos y fixtures de revisión temporal/editores aprobados.
- [ ] Pruebas integradas JAR y verificación completa de paginado/fallos; ficha privada y administración restantes. A07 y objetivo global continúan abiertos.

### Administración de vocabularios bilingüe — avance A07 (2026-10-08)
- [x] Vocabularios es/en: lista, estado activo/configurado/estricto, propuesta y confirmación explícita; términos, notas, revisiones y responsables originales se conservan.
- [x] Fixture Chrome `node tools/e2e/vocabulary-i18n.cjs`: sin consultas por idioma, texto/control original conservado en modal, payload PUT con revisión, ningún POST hasta confirmar y aprobación POST con revisión. Evidencia UI aislada, no autorización de servidor.
- [ ] Administración de perfiles, revisiones, colecciones/ayuda y ficha privada pendientes; no cierre global.

### Administración de perfiles bilingüe — avance A07 (2026-10-08)
- [x] Perfiles administrativos es/en: definición, requisitos conocidos por código, propuesta, aprobación y disponibilidad; nombres/descripciones/notas y campos institucionales desconocidos originales.
- [x] Chrome `node tools/e2e/profiles-admin-i18n.cjs`: cambio es/en sin consultas, controles y checks preservados, payload PUT con revisión/campos y POST solo tras confirmación. Fixture UI aislada, no prueba autorización ni inmutabilidad de snapshots backend.
- [ ] Ficha privada, revisiones, colecciones y ayuda pendientes; pruebas integradas reales y demás requisitos globales siguen abiertos.

### Colecciones públicas y gestión curatorial bilingües — avance A07 (2026-10-08)
- [x] Vista de colecciones, fichas agrupadas, filtros/paginación, creación/edición y confirmaciones es/en; títulos, descripción, autoría y valores de categorías permanecen originales.
- [x] Chrome `node tools/e2e/collections-i18n.cjs`: público/gestión, sin consultas ni cambios de URL por idioma, formulario conservado, PUT con revisión y eliminación de pertenencia solo tras confirmación. Fixture UI no demuestra permisos de servidor.
- [ ] Ficha privada, revisiones, ayuda y comprobación integrada general siguen pendientes; objetivo global activo.

### Guía de ayuda bilingüe — avance A07 (2026-10-08)
- [x] Guía completa es/en para búsqueda/descarga, depósito, citación, integridad, políticas y soporte; enlaces y comandos originales intactos. No presenta políticas pendientes ni exportaciones preliminares como aprobadas/certificadas.
- [x] Chrome `node tools/e2e/help-i18n.cjs`: textos/caveats, links/code conservados, sin API, inglés a 320px sin overflow ni violaciones axe automáticas WCAG2/2.1/2.2. No sustituye revisión manual ni certifica cumplimiento WCAG.
- [x] Siete pruebas de catálogos aprobadas incluyendo bindings hoja de ayuda.
- [ ] Ficha privada y revisiones pendientes, así como comprobación integrada y requisitos globales.

### Curación bilingüe — avance A07 (2026-10-08)
- [x] Revisión curatorial es/en: controles/confirmaciones, vista previa, mensajes de privacidad, enlaces temporales y resumen de preservación. Metadatos, notas privadas y códigos científicos originales permanecen intactos.
- [x] Chrome `node tools/e2e/reviews-i18n.cjs`: vista previa y notas originales, cambio es/en sin consultas, campo de enlace secreto conservado, creación explícita, ninguna publicación DOI por idioma. Fixture UI, no prueba autorización ni integración DataCite real.
- [ ] Ficha privada y revisión integrada global pendientes; auditorías y evidencias institucionales no cerradas por esta prueba.

### Formularios estáticos de ficha privada — avance parcial A07 (2026-10-08)
- [x] Etiquetas estáticas, formularios y modales de resource.html en es/en; controles, códigos de acceso/tipo y rutas/nombre description.md intactos.
- [x] Chrome `node tools/e2e/resource-static-i18n.cjs`: controles originales y valores preservados al cambiar idioma. Excluye scripts de negocio deliberadamente; no demuestra el flujo de ficha ni backend.
- [ ] Contenido dinámico de resource.js y verificación integrada pendientes. No se activa lang global inglés mientras la ficha dinámica siga sin migrar.

### Identidad y archivos de ficha privada — avance dinámico A07 (2026-10-08)
- [x] Identidad/publicación, campos científicos y listado de archivos con bindings es/en propios; títulos, valores, nombres y cita originales preservados. Estados desconocidos no se muestran falsamente como borrador.
- [x] Chrome `node tools/e2e/resource-dynamic-i18n.cjs`: render dinámico, metadatos/archivos/cita y confirmación de archivo conservados, sin API por idioma. Compartidos/backend stubbed, no evidencia de edición/autorización real.
- [ ] Mensajes de DOI, historial/calidad, políticas de archivos y vista previa de envío aún por migrar; ficha privada global permanece parcial.

### DOI, historial y calidad de ficha privada — avance A07 (2026-10-08)
- [x] Políticas de archivos, estados DOI/confirmación de reserva, historial editorial y checklist de calidad es/en; códigos DOI, actores y notas originales. Fallo de calidad limpia checks y porcentaje anterior.
- [x] Fixture Chrome dinámica ampliada: estado DOI DRAFT sin presentar publicación, checklist conocido traducido/campo institucional desconocido original, historial con actor/nota intactos y cero consultas por idioma. Fixture UI con backend stubbed.
- [ ] Vista previa de envío, mensajes/errores restantes y flujo completo real siguen pendientes; ficha privada todavía no finalizada.

### Vista previa de envío y errores bilingües — avance A07 (2026-10-08)
- [x] Vista previa privada es/en con contenido, lista de archivos, bloqueos y confirmación explícita; errores locales traducibles y detalles API originales. Se activa idioma global en ficha privada tras migración principal.
- [x] Chrome fixture dinámica ampliada: preview conserva título/nombre archivo al idioma, cero consultas por cambio de idioma y ningún POST hasta confirmación; exactamente un envío confirmado. Backend stubbed: no sustituye revisión real de permisos/estado.
- [ ] Regresión Java integral iniciada, resultado pendiente; JAR/E2E integrado y auditoría de requisitos globales restantes no cerrados.

### Regresión completa tras migración de fichas — 2026-10-08
- [x] Java21 offline: 52 suites, 504 tests, cero fallos/errores/omitidos. Log /tmp/reduniv-i18n-full-java.log.
- [x] Dieciséis fixtures Chrome secuenciales aprobados, acceso/catálogo/depósito/operaciones/fichas/administración/ayuda; log /tmp/reduniv-i18n-all-fixtures.log. No equivalen a permisos reales de servidor.
- [x] JAR minimal Java21 construido correctamente (12s); log /tmp/reduniv-i18n-full-jar.log.
- [ ] E2E aislado PostgreSQL/SMTP/restore en ejecución; resultado pendiente. A07 requiere además auditoría textual/estados y revisión institucional, sin cerrar objetivo global.
