# Plan de cierre RedUniv

Rama exclusiva: `develop-reduniv`. Sin merge, push ni cambios en producción.

Cada casilla se marca únicamente con implementación y evidencia. Las validaciones institucionales no se sustituyen por pruebas simuladas.

## Requisitos trazados a los tres documentos

- [ ] **S01 — Rotar contraseña expuesta en revisión**. Institucional: no modificar cuentas reales sin autorización.
- [x] **S02 — Suite automática corregida y ejecutable**. Implementado local: regresión Java21/JUnit4 offline, E2E PostgreSQL18/SMTP/Chrome/restauración y axe ejecutables; fechas/alcance exactos por bloque.
- [ ] **S03 — Eliminar secretos/credenciales predeterminados**. Base local: configuración de despliegue/JWT; auditar también historia Git y artefactos reales.
- [ ] **S04 — Autorización por recurso, no solo autenticación**. Base local: endpoints científicos y E2E edición ajena denegada; ampliar auditoría de todos los endpoints.
- [ ] **S05 — Rate limiting, auditoría y políticas seguras**. Base local: limitador PostgreSQL y plantilla HAProxy; prueba real multiinstancia y rotación pendientes.
- [ ] **S06 — Lectura, depósito, curación, publicación y administración separados**. Base local: roles USER/CURATOR/ADMINISTRATOR y controles por estado; verificar matriz completa.
- [ ] **S07 — Ocultar acciones destructivas no permitidas**. Base local: vistas de autor/curación; comprobar cada estado, rol y endpoint.
- [ ] **F01 — Tipo, estado, título, versión exacta y DOI prioritarios**. Base local: `public-resource.html/js`, `resource.html/js`; revisión visual completa contra maqueta pendiente.
- [ ] **F02 — Autores con ORCID, instituciones con ROR por autor**. Base local: identidades/afiliaciones y DataCite mapper; pruebas individuales y OAuth institucional pendientes.
- [ ] **F03 — Licencia y condiciones de acceso obligatorias**. Base local: checklist de calidad/acceso; probar ausencia de licencia, embargo y restricción en todos los flujos.
- [ ] **F04 — Copiar cita y elegir APA/Vancouver/Chicago/IEEE**. Base local: bloque de cita/exportaciones; validación bibliotecaria pendiente.
- [ ] **F05 — Exportar BibTeX, RIS y CSL-JSON para versión exacta**. Base local: servicio de cita; verificar round-trip/validez y como máximo dos acciones (§11).
- [ ] **F06 — Aviso de versión antigua, DOI conceptual y de versión**. Base local: sucesora/DOI; verificar historial completo, no solo enlace anterior/siguiente.
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
- [ ] **W02 — Etapas: identidad/tipo y autores/organizaciones**. Base local: asistente de cuatro pantallas agrupa operaciones; comprobar cobertura de nueve etapas funcionales del informe.
- [ ] **W03 — Búsqueda ORCID/ROR**. Parcial: búsqueda ROR y OAuth ORCID; no atribuir identidad mediante búsqueda por nombre sin confirmación.
- [ ] **W04 — Descripción/metodología y archivos/documentación**. Base local: asistente y Markdown/ZIP; validar resumen y cobertura requeridos.
- [x] **W05 — Licencia, acceso, embargo y datos sensibles**. Implementado local: declaración privada del autor, control de revisión y restricción de datos personales/confidenciales; no permite sustituir restricción con embargo. Pruebas unitarias y E2E de formularios/publicación aprobadas. Política jurídica institucional pendiente.
- [ ] **W06 — Artículos, software, proyectos y financiación**. Base local: relaciones/funding en edición; comprobar disponibilidad durante creación.
- [ ] **W07 — Guardado automático y recuperación**. Base local: metadatos locales14d y borrador servidor; no prometer persistencia automática de archivos seleccionados.
- [ ] **W08 — Revisión automática: errores, avisos, porcentaje y explicación**. Base local: calidad incluye explicación de reutilización por campo y recomendaciones de resumen/cobertura; pendiente aceptación humana y auditoría de todos los mensajes.
- [ ] **W09 — Vista previa de landing y cita antes de enviar**. Parcial: ficha real previa disponible y cita provisional sin DOI inventado añadida al asistente; ZIP muestra manifiesto seleccionado y exige revisión real después de guardarlo. Auditar vista previa integral y estilo bibliotecario.
- [ ] **W10 — Envío a curación/publicación según política**. Base local: flujo revisión/publicación; depende de política institucional.
- [ ] **W11 — Reserva DOI, embargo y acceso privado a revisores**. Base local: DOI Draft/enlaces temporales; pruebas DataCite/HAProxy pendientes.
- [ ] **A01 — Cola y validación de metadatos/archivos**. Base local: Curación/calidad; validación bibliotecaria/formatos real pendiente.
- [ ] **A02 — Control de identificadores, vocabularios y perfiles**. Parcial: listas configurables e identidad; administración de perfiles/vocabularios y aprobación no acreditadas.
- [x] **A03 — Licencias, privacidad y datos sensibles en curación**. Implementado local: decisión de curación con notas privadas y revisión concurrente; bloquea publicación sensible sin aprobación, revoca al devolver a borrador. E2E aprobado; no sustituye aprobación jurídica institucional.
- [ ] **A04 — Usuarios, roles e integraciones DOI/OIDC/correo**. Parcial: usuarios/DOI/correo/ORCID; OIDC institucional de acceso no equivale a OAuth ORCID.
- [x] **A05 — Estado almacenamiento/fixity, auditoría/reportes operativos**. Implementado local: consola autorizada con volumen local, inventario técnico/fixity, veinte auditorías e informe JSON fechado. Tests/E2E aprobados; no sustituye monitorización y backup institucional.
- [x] **A06 — Metadatos multilingües**. Implementado local: traducciones declaradas de título/resumen por idioma BCP47, persistidas por versión y visibles en ficha/DataCite. Editor compartido sin JSON técnico. No equivale a internacionalización UI.
- [ ] **A07 — Internacionalización sin textos incrustados en JS**. Pendiente: strings españoles siguen en scripts; acordar idiomas, separar catálogos y traducir estados dinámicos.
- [ ] **A08 — Paquetes OAIS SIP/AIP/DIP**. Parcial: BagIt/paquete curatorial/ZIP público; perfiles institucionales completos no acreditados.
- [x] **A09 — RO-Crate y W3C PROV**. Implementado local: declaración científica y eventos técnicos separados, manifiestos/paquetes probados; no inventa actividades ni fechas. Tests/E2E aprobados. Validación externa de perfiles pendiente.
- [ ] **A10 — Auditorías periódicas SHA-256 verificables**. Base local: manual/programada opcional y alertas; política/volumen/buzón real pendientes.
- [ ] **A11 — Métricas COUNTER/DataCite cuando corresponda**. Pendiente: no confundir contadores locales con conformidad COUNTER o integración de eventos DataCite.
- [ ] **A12 — Autoevaluación CoreTrustSeal**. Pendiente: requiere expediente y evidencia organizativa/técnica; no afirmar certificación.
- [ ] **A13 — OpenAPI como contrato y pruebas de compatibilidad**. Parcial: infraestructura OpenAPI existente; cobertura contractual de endpoints nuevos y compatibilidad pendientes de auditoría.
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
- [ ] **D09 — Secretos solo backend, nunca JS/Git**. Base local: properties/despliegue; revisar historial y artefactos con manejo seguro.
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
