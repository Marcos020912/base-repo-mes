# Evolución del repositorio científico RedUniv

Esta rama (`develop-reduniv`) inicia la implementación del informe
`INFORME_MEJORAS_REPOSITORIO_CIENTIFICO.pdf` y del prototipo HTML entregados por
el equipo. El prototipo contiene datos ficticios: la aplicación solo debe
mostrar valores presentes en los metadatos reales. No se debe fusionar esta
rama con `main` sin aprobación explícita y pruebas funcionales.

## Primer incremento en esta rama

- Identidad «Datos RedUniv», portada de catálogo con búsqueda prominente y
  filtros de categoría y autor visibles.
- Tarjetas con autoría y DOI solo si existe un DOI válido; los faltantes se
  declaran expresamente.
- Bloque de identidad científica y cita preliminar copiable en la ficha. El
  estado `VOLATILE` se identifica como borrador, no como publicado.
- Mensajes emergentes construidos como nodos de texto para evitar inyectar HTML
  procedente de respuestas de error.

## Incrementos científicos en esta rama

- `ScientificRecord` almacena estado de borrador/revisión/publicación/retirada,
  versión, DOI suministrado, licencia, acceso, institución, ORCID, ROR,
  metodología y relaciones. La versión publicada queda bloqueada para cambios;
  una nueva versión se crea como otro recurso enlazado al anterior; la ficha pública avisa cuando existe una sucesora publicada.
- Cola de revisión para curadores y administradores con vista segura de metadatos, descripción y descarga de archivos (independiente de ACL heredadas). Con DataCite desactivado se exige DOI registrado externamente; con DataCite activado el flujo reserva y publica los DOI automáticamente tras la revisión.
- Catálogo autenticado y público con búsqueda y paginación en servidor, orden
  por actualización/año y filtros reproducibles en URL (autor, tipo, año,
  licencia, disciplina, institución, idioma, acceso, formato y DOI); catálogo y ficha públicos limitados a versiones
  `PUBLISHED`. Los metadatos de versiones restringidas o embargadas son
  visibles, pero la descarga pública de archivos solo se permite para acceso
  abierto o al expirar el embargo. La retirada presenta un tombstone HTTP 410.
- Exportaciones de cita APA, Vancouver, Chicago, IEEE, BibTeX, RIS y CSL-JSON
  para versiones publicadas. Son preliminares y requieren validación
  bibliotecaria y comprobación del orden de autoría antes de producción.
- Lista automática de calidad con porcentaje, requisitos de metadatos, description.md y al menos un archivo; el envío a curación se bloquea si faltan requisitos. Esta validación no sustituye la curación humana.
- Huellas SHA-256 calculadas al subir por los endpoints web y visibles en la
  ficha. Una comprobación manual independiente permite detectar cambios y una
  auditoría semanal es opcional y está desactivada por defecto. Archivos
  anteriores o subidos por otros endpoints pueden no tener huella de referencia.
- El ZIP curatorial de preservación incluye un RO-Crate 1.2 adjunto con JSON-LD
  de dataset y archivos. Su `sha256` describe los bytes empaquetados en ese
  momento; el manifiesto de ingreso se mantiene separado para no confundir la
  huella histórica con la actual. Falta validación externa del paquete y los
  perfiles OAIS/PROV completos.
- Historial editorial persistente de cambios de ficha, envío, devolución, publicación, retirada y derivación, visible para autor y curación; los DOI públicos se presentan como enlaces resolubles.
- Integración DataCite opt-in con reserva Draft conceptual/versión, estado e
  historial persistentes, publicación Findable por curador, reconciliación y
  landing permanente. Probada sin credenciales reales. **No activar en
  producción hasta validar DataCite Test, migración y DOI manuales existentes.**
- Asistente de creación en cuatro pasos con comprobación previa de metadatos,
  descripción y archivos; permite conservar borrador o enviarlo a revisión.
  La ficha del autor muestra una vista previa real y el checklist de calidad
  antes de confirmar ese envío. Si falla una carga parcial, el borrador no se
  envía y puede corregirse desde su ficha.
- El asistente conserva localmente metadatos y autores por 14 días para poder
  reanudar un formulario interrumpido; por seguridad, los archivos se
  seleccionan de nuevo. El catálogo público muestra facetas agregadas con
  recuentos y estados diferenciados de vacío, error y cero coincidencias.
- Los recursos científicos pueden declarar relaciones tipadas DOI/URL que se
  muestran públicamente y se transmiten a DataCite. ORCID e institución/ROR
  pueden asignarse a cada creador por separado, con múltiples afiliaciones
  ordenadas por autor. OAuth ORCID opcional autentica el control de la cuenta
  vinculada a un autor seleccionado; falta validación Sandbox real y una
  política para coautores. No certifica la correspondencia del nombre.
- Cada versión puede registrar financiadores y proyectos estructurados; la
  ficha pública, las facetas del catálogo y `fundingReferences` de DataCite
  usan esos datos. Falta validar con DataCite Test y medir las consultas de
  facetas en PostgreSQL con volumen real.
- La curación puede emitir y revocar enlaces privados de revisión externos,
  con duración limitada y lectura/descarga de archivos solo mientras el
  depósito siga `IN_REVIEW`. El token no se guarda en claro.
- El registro por correo deja recuperar el envío cuando SMTP falla, y las rutas de verificación/reenvío son accesibles antes del primer login.
- Cambio de contraseña propia, revocación de JWT anteriores, comprobación en
  cada solicitud de rol/estado/validación de la cuenta, contraseña de arranque
  no predeterminada y secreto JWT único generado por el despliegue nuevo.
- El arranque ya no registra valores arbitrarios de propiedades: el antiguo
  volcado podía incluir `repo.auth.jwtSecret` y otros secretos sin `password`
  en su nombre. Revisar y rotar secretos expuestos en logs anteriores.
- Los endpoints de autenticación tienen cuotas compartidas por PostgreSQL y
  devuelven `Retry-After` al agotarlas. El HAProxy de referencia reemplaza
  `X-Forwarded-For`/`Forwarded`; no exponer el backend directamente. La
  plantilla no incorpora un secreto JWT válido: despliegue genera uno único.

### Migración y pruebas

Antes de desplegar esta rama: respaldar PostgreSQL y archivos, revisar
`docs/migrations/2026-09-scientific-records.sql`, aplicarlo en mantenimiento y
probar restauración. No desplegar esta rama directamente en producción.
Las pruebas focalizadas de flujo, búsqueda pública, acceso restringido, calidad, SHA-256, DataCite, ORCID y RO-Crate pasan. La suite completa pasó con 432 pruebas y 0 fallos (8 de octubre de 2026, JDK 21 y perfil `complete`, con procesos de prueba reiniciados cada cinco clases para evitar falta de memoria local); el JAR también compila sin red. Esto no sustituye las pruebas funcionales, de seguridad ni la aprobación para fusión.

El estado detallado y actualizado de cada ítem está en
[`REDUNIV_IMPLEMENTATION_CHECKLIST.md`](REDUNIV_IMPLEMENTATION_CHECKLIST.md).

## Próximos incrementos propuestos

1. Seguridad y calidad: rotar efectivamente las credenciales ya utilizadas en
   producción; validar el limitador distribuido con HAProxy y carga real,
   auditoría y pruebas de seguridad funcionales. La suite heredada ya se adecuó
   al comportamiento 401 sin debilitar la autorización.
2. Modelo de depósito: validar enlaces privados temporales con revisores
   externos reales, pruebas de concurrencia/transiciones y evaluación curatorial
   de formatos/datos sensibles. Una versión nueva requiere cargar sus archivos.
3. Identidad científica: validar integración DOI con el proveedor institucional,
   OAuth ORCID en Sandbox y consulta ROR, validación de múltiples afiliaciones y
   aprobación de vocabularios de licencia/disciplinas. Las relaciones tipadas
   y el mapeo por creador ya están implementados localmente.
4. Citación: validar todos los estilos con bibliotecarios y el orden de
   autoría; completar descarga de metadatos y pruebas bibliográficas.
5. Descubrimiento: medir en PostgreSQL las facetas, incluidas las de
   financiación/proyecto; el filtrado y estados explícitos ya están implementados localmente.
6. Curación y preservación: cola de revisión, vocabularios controlados,
   checksum SHA-256, operación de la verificación periódica, procedencia de archivos/datos,
   preservación y métricas definidas.
7. Interfaz: rutas públicas solo para objetos publicados, separación de áreas
   investigador/curador/admin, componentes semánticos, accesibilidad WCAG 2.2
   AA, internacionalización y pruebas con usuarios reales.

Los cambios de esquema y API deben migrarse sin perder datos y aprobarse antes
de producción. No activar exposición pública solo mediante cambios de CSS o
HTML: requiere autorización y estados de publicación en el backend.
