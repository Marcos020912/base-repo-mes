# Requisitos RedUniv contrastados con las fuentes originales

Revisión: 2026-10-08. Rama: `develop-reduniv`. **No equivale a cierre, ni autoriza producción.**

## Fuentes recuperadas

| Documento | Fecha interna | SHA-256 |
|---|---|---|
| `INFORME_MEJORAS_REPOSITORIO_CIENTIFICO.pdf` (7 páginas) | 23-09-2026 | `48f141f0dd68f5ebbd438d7a5768cd5990af6a4e543bef24949fd618a3b202a4` |
| `prototipo-repositorio-cientifico.html` | Maqueta estática | `319ece296e91facd8fb005adfd971f4fefd904e406feafd2e4fc8b99494e7aa6` |
| `Integracion_DOI_Repositorio_RedUniv.md` | 28-09-2026 | `1c5bdac6753030fd8301a2217acf3d1edbaa75ae9340f8115858e0727ef26590` |

Leídos los tres archivos en `/home/marcos/Descargas/`. El HTML contiene ejemplos
ficticios: cifras, DOI, instituciones y nombres no deben convertirse en datos
reales de la aplicación. Este registro amplía el checklist anterior: no es
válido reducir el alcance a sus catorce pendientes de staging.

Estados: **Base local** = hay implementación identificada, pendiente de auditoría
individual completa; **Parcial** = falta implementación o evidencia del alcance;
**Pendiente** = no se encontró solución en la revisión dirigida; **Institucional**
= requiere además decisiones, credenciales o validación externas. Ninguna fila
«Base local» acredita por sí sola todos los criterios del informe.

## Seguridad y permisos — informe §8 P0 y §9

| ID | Requisito | Estado / evidencia / siguiente acción |
|---|---|---|
| S01 | Rotar contraseña expuesta en revisión | Institucional: no modificar cuentas reales sin autorización. |
| S02 | Suite automática corregida y ejecutable | Implementado local: Gradle JUnit4, E2E PostgreSQL18/SMTP/Chrome/restauración y axe ejecutables; evidencia por bloque en plan. No sustituye validación externa. |
| S03 | Eliminar secretos/credenciales predeterminados | Parcial: retiradas credenciales literales de plantillas; gate local del árbol sin hallazgos y cuatro tests aprobados. Historial contiene 102 ocurrencias de propiedades literales en blobs únicos (incluye defaults de desarrollo), requiere evaluación/rotación y autorización de limpieza; artefactos aún pendientes. |
| S04 | Autorización por recurso, no solo autenticación | Base local: endpoints científicos y E2E edición ajena denegada; ampliar auditoría de todos los endpoints. |
| S05 | Rate limiting, auditoría y políticas seguras | Base local: limitador PostgreSQL y plantilla HAProxy; prueba real multiinstancia y rotación pendientes. |
| S06 | Lectura, depósito, curación, publicación y administración separados | Base local: roles USER/CURATOR/ADMINISTRATOR y controles por estado; verificar matriz completa. |
| S07 | Ocultar acciones destructivas no permitidas | Base local: vistas de autor/curación; comprobar cada estado, rol y endpoint. |

## Identidad, citación y ficha — informe §4.1–4.3, §6 y prototipo

| ID | Requisito | Estado / evidencia / siguiente acción |
|---|---|---|
| F01 | Tipo, estado, título, versión exacta y DOI prioritarios | Base local: `public-resource.html/js`, `resource.html/js`; revisión visual completa contra maqueta pendiente. |
| F02 | Autores con ORCID, instituciones con ROR por autor | Base local: identidades/afiliaciones y DataCite mapper; pruebas individuales y OAuth institucional pendientes. |
| F03 | Licencia y condiciones de acceso obligatorias | Base local: checklist de calidad/acceso; probar ausencia de licencia, embargo y restricción en todos los flujos. |
| F04 | Copiar cita y elegir APA/Vancouver/Chicago/IEEE | Base local: bloque de cita/exportaciones; validación bibliotecaria pendiente. |
| F05 | Exportar BibTeX, RIS y CSL-JSON para versión exacta | Base local: servicio de cita; verificar round-trip/validez y como máximo dos acciones (§11). |
| F06 | Aviso de versión antigua, DOI conceptual y de versión | Implementado local: familia pública paginada de padres/hijas/hermanas, retiradas retenidas y borradores ocultos; aviso de publicación posterior por fecha sin adivinar fechas desconocidas. Tests/E2E/restore aprobados; DataCite real pendiente. |
| F07 | Fecha de publicación y última actualización visibles | Implementado local: publishedAt y lastUpdate públicos visibles; E2E Chrome/PostgreSQL verificó última actualización. |
| F08 | Resumen, método, cobertura y palabras clave | Implementado local: resumen estructurado, fechas de cobertura validadas y región textual en creación/edición/ficha pública/DataCite. E2E PostgreSQL/restauración y pruebas de validación aprobados. |
| F09 | Archivos: formato, tamaño, checksum y acceso | Base local: lista de archivos/huellas/monitor; selector nativo y volumen real pendientes. |
| F10 | Procedencia: producción y procesamiento de datos | Implementado local: producción/origen, procesamiento y herramientas/versiones por versión, creación/edición/preview/ficha pública y exportación DataCite/RO-Crate/PROV; pruebas/JPA/E2E aprobadas. Es declaración del autor, no ejecución verificada. |
| F11 | Artículos, software, proyectos, financiación y datasets relacionados | Base local: relaciones tipadas y funding; comprobar visibilidad y representación máquina de todas las categorías. |
| F12 | Última verificación y política de preservación aplicable | Parcial: fixity existe; política institucional y su presentación aprobada pendientes. |
| F13 | Historial de cambios de metadatos y versiones publicadas | Parcial: historial editorial existe; comprobar presentación pública según política. |
| F14 | Compartir y exportar metadatos como acciones científicas | Implementado local: copia de landing permanente y exportación JSON versionada de ficha pública (sin archivos privados), además de citas. E2E Chrome/PostgreSQL aprobado. |
| F15 | Publicado inmutable; cambios crean nueva versión | Base local: workflow/versiones y E2E sintético; probar todos los endpoints de escritura. |
| F16 | Retirada controlada sustituye borrado publicado; tombstone | Base local: estado WITHDRAWN/HTTP410; auditoría de autorización y permanencia pendientes. |

## Descubrimiento y área pública — informe §4.4–4.5 y §5

| ID | Requisito | Estado / evidencia / siguiente acción |
|---|---|---|
| C01 | Inicio y búsqueda global con identidad institucional | Base local: catálogo público y hero; revisar maqueta sin copiar cifras ficticias. |
| C02 | Facetas: área, institución, autor, año, tipo, licencia | Base local: API catálogo/facetas y vistas; pruebas de combinaciones/volumen pendientes. |
| C03 | Facetas: acceso, formato, proyecto/financiador, DOI, idioma | Base local: API/funding; comprobar todas visibles y utilizables en catálogo público. |
| C04 | Búsqueda, orden y paginación en servidor | Base local: catálogo; no sustituir por filtro de 200 registros en cliente. |
| C05 | Filtros, página y orden reproducibles en URL | Implementado local: URL reproducible, historial de filtros/orden/página y restauración en popstate; prueba Chrome de ambos catálogos aprobada (API fixture, no prueba de búsqueda backend). |
| C06 | Distinguir vacío, cero coincidencias, permisos y caída | Implementado local: vacío general, cero coincidencias, permiso y caída diferenciados en ambos catálogos; limpiar filtros/soporte/reintento y prueba Chrome de recuperación aprobados. |
| C07 | Colecciones temáticas e institucionales | Implementado local: colecciones temáticas/institucionales persistentes, gestión curador/admin con modales y catálogo público paginado solo PUBLISHED. Cuatro pruebas JPA y E2E PostgreSQL/SMTP/Chrome/restauración aprobados; no duplica ni elimina datasets. |
| C08 | Políticas, ayuda y guía de citación | Parcial: nueva `help.html` ofrece guía y condiciones técnicas; políticas institucionales formales aún no aprobadas. |
| C09 | Métricas públicas con definiciones transparentes | Parcial: inventario público real de versiones PUBLISHED, política OPEN y colecciones públicas con definiciones/fecha; JPA y E2E aprobados. Uso/descargas y metodología COUNTER/DataCite aún pendientes. |

## Investigador y asistente — informe §5 y §7

| ID | Requisito | Estado / evidencia / siguiente acción |
|---|---|---|
| W01 | Mis depósitos y tareas de metadatos pendientes | Implementado local: cola consolidada del autor paginada con estado, checklist, pendientes y siguiente acción; tres tests y E2E multiusuario/UI aprobados. Rendimiento institucional pendiente. |
| W02 | Etapas: identidad/tipo y autores/organizaciones | Implementado local: nueve etapas separadas con autoría/identidad, navegación accesible, precomprobación explícitamente local y confirmación final. E2E de los tres modos y recuperación/restauración aprobado; véase WIZARD_WORKFLOW.md. |
| W03 | Búsqueda ORCID/ROR | Parcial: búsqueda ROR y OAuth ORCID; no atribuir identidad mediante búsqueda por nombre sin confirmación. |
| W04 | Descripción/metodología y archivos/documentación | Base local: asistente y Markdown/ZIP; validar resumen y cobertura requeridos. |
| W05 | Licencia, acceso, embargo y datos sensibles | Implementado local: declaración privada, acceso restringido para datos sensibles y aprobación de curación; E2E aprobado. Política jurídica institucional pendiente. |
| W06 | Artículos, software, proyectos y financiación | Implementado local: etapa de creación con relaciones DataCite tipadas para artículos/software/datasets/proyectos, DOI o HTTPS, financiación y publicaciones; vista previa y recuperación local. E2E comprueba rechazo de HTTP, persistencia de SOFTWARE y formulario dinámico sin infracciones axe. |
| W07 | Guardado automático y recuperación | Base local: metadatos locales14d y borrador servidor; no prometer persistencia automática de archivos seleccionados. |
| W08 | Revisión automática: errores, avisos, porcentaje y explicación | Base local: calidad incluye explicación de reutilización por campo y recomendaciones de resumen/cobertura; pendiente aceptación humana y auditoría de todos los mensajes. |
| W09 | Vista previa de landing y cita antes de enviar | Parcial: ficha real previa disponible y cita provisional sin DOI inventado añadida al asistente; ZIP muestra manifiesto seleccionado y exige revisión real después de guardarlo. Auditar vista previa integral y estilo bibliotecario. |
| W10 | Envío a curación/publicación según política | Base local: flujo revisión/publicación; depende de política institucional. |
| W11 | Reserva DOI, embargo y acceso privado a revisores | Base local: DOI Draft/enlaces temporales; pruebas DataCite/HAProxy pendientes. |

## Curación, administración y preservación — informe §5, §8 P2/P3 y §9

| ID | Requisito | Estado / evidencia / siguiente acción |
|---|---|---|
| A01 | Cola y validación de metadatos/archivos | Base local: Curación/calidad; validación bibliotecaria/formatos real pendiente. |
| A02 | Control de identificadores, vocabularios y perfiles | Parcial institucional: vocabularios y perfiles persistentes con propuesta/aprobación, revisión concurrente y reglas congeladas por dataset verificados localmente. Control de identidad/listas institucionales y su aprobación externa no acreditados. |
| A03 | Licencias, privacidad y datos sensibles en curación | Implementado local: revisión privada con control concurrente, aprobación obligatoria y revocación al devolver borrador; tests y E2E aprobados. Validación institucional pendiente. |
| A04 | Usuarios, roles e integraciones DOI/OIDC/correo | Parcial: usuarios/DOI/correo/ORCID; OIDC institucional de acceso no equivale a OAuth ORCID. |
| A05 | Estado almacenamiento/fixity, auditoría/reportes operativos | Implementado local: consola CURATOR/ADMIN de volumen local, inventario técnico/fixity y veinte auditorías con informe JSON fechado; tests/E2E aprobados. No equivale a monitorización/backup institucional. |
| A06 | Metadatos multilingües | Implementado local: traducciones declaradas de título/resumen por idioma BCP47, persistidas por versión y visibles en ficha/DataCite. Editor compartido sin JSON técnico. No equivale a internacionalización UI. |
| A07 | Internacionalización sin textos incrustados en JS | Pendiente: strings españoles siguen en scripts; acordar idiomas, separar catálogos y traducir estados dinámicos. |
| A08 | Paquetes OAIS SIP/AIP/DIP | Parcial: BagIt/paquete curatorial/ZIP público; perfiles institucionales completos no acreditados. |
| A09 | RO-Crate y W3C PROV | Implementado local: exportaciones incluyen declaración científica separada de eventos técnicos; PROV no inventa ejecuciones. Pruebas/paquete E2E aprobados; validación externa de perfiles sigue pendiente. |
| A10 | Auditorías periódicas SHA-256 verificables | Base local: manual/programada opcional y alertas; política/volumen/buzón real pendientes. |
| A11 | Métricas COUNTER/DataCite cuando corresponda | Pendiente: no confundir contadores locales con conformidad COUNTER o integración de eventos DataCite. |
| A12 | Autoevaluación CoreTrustSeal | Pendiente: requiere expediente y evidencia organizativa/técnica; no afirmar certificación. |
| A13 | OpenAPI como contrato y pruebas de compatibilidad | Parcial: contrato científico tipado, seguridad y binarios explícitos; gate estructural de 22 rutas y referencias locales integrado en E2E. Compatibilidad de clientes institucionales y auditoría del resto de endpoints pendientes. |
| A14 | WCAG2.2AA, teclado, foco, contraste, etiquetas, errores, lectores | Parcial: axe y teclado automatizados; revisión manual de recorridos/lectores pendiente. |
| A15 | Diseño desde360px y estados no solo por color | Base local: pruebas320px y etiquetas; revisar cada flujo dinámico real. |
| A16 | Prueba con investigadores, curadores, bibliotecarios y soporte | Institucional: organizar evaluación funcional sin sustituirla por smoke automatizado. |

## DOI — especificación Markdown §§3–11

| ID | Requisito | Estado / evidencia / siguiente acción |
|---|---|---|
| D01 | Servicio backend desacoplado, API DataCite Test/Production | Base local: cliente/workflow; no credenciales de producción para pruebas locales. |
| D02 | POST/dois y metadatos obligatorios; validación previa | Base local: mapper/client; pruebas HTTP simulado, Test real pendiente. |
| D03 | Reserva Draft y publicación tras aprobación | Base local: estados persistidos; no enlazar Draft como DOI resoluble. |
| D04 | Landing permanente, nunca archivo directo | Base local: `/datasets/<id>`; verificar disponibilidad externa institucional. |
| D05 | DOI conceptual y específico de versión | Base local: workflow/mapper; no reutilizar DOI de versión para contenidos distintos. |
| D06 | HasVersion/IsVersionOf/IsNewVersionOf/IsPreviousVersionOf | Implementado local: sincroniza IsPreviousVersionOf del predecesor gestionado, preserva sucesores y relaciones locales y permite reintento sin publicar localmente antes de confirmar. Pruebas mock aprobadas; DataCite real pendiente. |
| D07 | reserve/publish/updateMetadata/get/createVersion/updateUrl | Base local: operaciones existentes y mantenimiento admin de URLs con vista previa/confirmación contra origen configurado y auditoría. Cuatro pruebas nuevas mock aprobadas; validación institucional y auditoría contractual pendientes. |
| D08 | Estado, fechas, última sincronización, URL, versión metadatos e historial local | Base local/parcial: registros/eventos DOI; contrastar todos los campos sugeridos y semántica de retirada. |
| D09 | Secretos solo backend, nunca JS/Git | Parcial: gate readonly sin valores en salida; árbol actual sin hallazgos, historial con propiedades literales por evaluar y sin reescritura automática. Artefactos/releases reales y rotación institucional pendientes. |
| D10 | Sufijo generado por DataCite recomendado | Desviación documentada: sufijo UUID estable local para idempotencia; recomendación no obligatoria, requiere transparencia institucional. |
| D11 | Cuenta Repository, prefijo, credenciales y validación Test→staging→production | Institucional: no resuelto únicamente con código; no dar integración externa por probada. |

## Próximas implementaciones confirmadas

1. Terminar ayuda pública y navegación, distinguir guías técnicas de políticas aprobadas.
2. Colecciones temáticas/institucionales y métricas públicas reales con definición.
3. Resumen/cobertura, metadatos multilingües y vista previa de cita en creación.
4. Completar relaciones DOI de predecesores y mantenimiento seguro de URL.
5. Auditar OpenAPI, perfiles curatoriales, datos sensibles e internacionalización.
6. Preparar evidencias institucionales de preservación/medición y aceptación humana.

Este orden no elimina las demás filas ni sustituye la evaluación requerida.
