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

**Límite actual:** el catálogo sigue protegido por autenticación y consulta
como máximo 200 recursos en la primera entrega; ese límite se abordó en el
segundo incremento descrito a continuación.

## Segundo incremento en desarrollo

- `ScientificRecord` almacena estado de borrador/revisión/publicación/retirada,
  versión, DOI suministrado, licencia, acceso, institución, ORCID, ROR,
  metodología y relaciones. La versión publicada queda bloqueada para cambios;
  una nueva versión se crea como otro recurso enlazado al anterior.
- Cola de revisión para curadores y administradores con vista segura de metadatos, descripción y descarga de archivos (independiente de ACL heredadas). Publicar exige versión,
  licencia, DOI de versión y confirmación manual de que el DOI ya fue registrado
  fuera de la aplicación. **No hay integración automática con DataCite**.
- Catálogo autenticado con búsqueda y paginación en servidor, filtros
  reproducibles en URL; catálogo y ficha públicos limitados a versiones
  `PUBLISHED`. Los metadatos de versiones restringidas o embargadas son
  visibles, pero la descarga pública de archivos solo se permite para acceso
  abierto o al expirar el embargo. La retirada presenta un tombstone HTTP 410.
- Exportaciones de cita APA preliminar, BibTeX, RIS y CSL-JSON para versiones
  publicadas. Requieren validación bibliotecaria antes de producción.
- Lista automática de calidad con porcentaje, requisitos de metadatos, description.md y al menos un archivo; el envío a curación se bloquea si faltan requisitos. Esta validación no sustituye la curación humana.
- Huellas SHA-256 calculadas al subir por los endpoints web y visibles en la
  ficha. La huella es una referencia de carga, **no** una auditoría periódica
  de integridad. Archivos anteriores o subidos por otros endpoints pueden no
  tenerla.
- Cambio de contraseña propia, revocación de JWT anteriores, comprobación en
  cada solicitud de rol/estado/validación de la cuenta, contraseña de arranque
  no predeterminada y secreto JWT único generado por el despliegue nuevo.

### Migración y pruebas

Antes de desplegar esta rama: respaldar PostgreSQL y archivos, revisar
`docs/migrations/2026-09-scientific-records.sql`, aplicarlo en mantenimiento y
probar restauración. No desplegar esta rama directamente en producción.
Las pruebas focalizadas de flujo, búsqueda pública y SHA-256 pasan. La suite
heredada todavía contiene expectativas de acceso anónimo que no coinciden con
la política actual: 31 de 366 pruebas siguen fallando; no se consideran
aprobadas para fusión.

## Próximos incrementos propuestos

1. Seguridad y calidad: rotar efectivamente las credenciales ya utilizadas en
   producción, rate limiting distribuido, auditoría y adecuar la suite heredada
   sin debilitar la política de autorización.
2. Modelo de depósito: enlaces privados temporales para revisores externos, pruebas de
   concurrencia/transiciones y evaluación curatorial de formatos/datos sensibles. Una versión nueva requiere cargar sus archivos.
3. Identidad científica: integrar registro DOI con el proveedor institucional,
   verificación ORCID/ROR, autores e instituciones múltiples, vocabularios de
   licencia/disciplinas y relaciones tipadas procesables por máquinas.
4. Citación: validar estilos con bibliotecarios, agregar Vancouver/Chicago/IEEE,
   descarga completa de metadatos y advertencias de versiones antiguas.
5. Descubrimiento: facetas agregadas, orden configurable y estados de catálogo
   vacío, cero coincidencias, falta de permisos y fallo de búsqueda.
6. Depósito guiado: asistente con guardado de borrador, metadatos, archivos,
   documentación, licencia, privacidad, relaciones, revisión de calidad y
   vista previa antes de enviar a curación.
7. Curación y preservación: cola de revisión, vocabularios controlados,
   checksum SHA-256, verificación periódica, procedencia, historial,
   preservación y métricas definidas.
8. Interfaz: rutas públicas solo para objetos publicados, separación de áreas
   investigador/curador/admin, componentes semánticos, accesibilidad WCAG 2.2
   AA, internacionalización y pruebas con usuarios reales.

Los cambios de esquema y API deben migrarse sin perder datos y aprobarse antes
de producción. No activar exposición pública solo mediante cambios de CSS o
HTML: requiere autorización y estados de publicación en el backend.
