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
- Cola de revisión para curadores y administradores con vista segura de metadatos, descripción y descarga de archivos (independiente de ACL heredadas). Publicar exige versión,
  licencia, DOI de versión y confirmación manual de que el DOI ya fue registrado
  fuera de la aplicación. **No hay integración automática con DataCite**.
- Catálogo autenticado y público con búsqueda y paginación en servidor, orden
  por actualización/año y filtros reproducibles en URL (autor, tipo, año,
  licencia, disciplina, institución, idioma, acceso, formato y DOI); catálogo y ficha públicos limitados a versiones
  `PUBLISHED`. Los metadatos de versiones restringidas o embargadas son
  visibles, pero la descarga pública de archivos solo se permite para acceso
  abierto o al expirar el embargo. La retirada presenta un tombstone HTTP 410.
- Exportaciones de cita APA preliminar, BibTeX, RIS y CSL-JSON para versiones
  publicadas. Requieren validación bibliotecaria antes de producción.
- Lista automática de calidad con porcentaje, requisitos de metadatos, description.md y al menos un archivo; el envío a curación se bloquea si faltan requisitos. Esta validación no sustituye la curación humana.
- Huellas SHA-256 calculadas al subir por los endpoints web y visibles en la
  ficha. Una comprobación manual independiente permite detectar cambios y una
  auditoría semanal es opcional y está desactivada por defecto. Archivos
  anteriores o subidos por otros endpoints pueden no tener huella de referencia.
- Historial editorial persistente de cambios de ficha, envío, devolución, publicación, retirada y derivación, visible para autor y curación; los DOI públicos se presentan como enlaces resolubles.
- El registro por correo deja recuperar el envío cuando SMTP falla, y las rutas de verificación/reenvío son accesibles antes del primer login.
- Cambio de contraseña propia, revocación de JWT anteriores, comprobación en
  cada solicitud de rol/estado/validación de la cuenta, contraseña de arranque
  no predeterminada y secreto JWT único generado por el despliegue nuevo.

### Migración y pruebas

Antes de desplegar esta rama: respaldar PostgreSQL y archivos, revisar
`docs/migrations/2026-09-scientific-records.sql`, aplicarlo en mantenimiento y
probar restauración. No desplegar esta rama directamente en producción.
Las pruebas focalizadas de flujo, búsqueda pública, acceso restringido, calidad y SHA-256 pasan. La suite completa pasa con 378 pruebas y 0 fallos (28 de septiembre de 2026); el JAR también compila sin red. Esto no sustituye las pruebas funcionales, de seguridad ni la aprobación para fusión.

El estado detallado y actualizado de cada ítem está en
[`REDUNIV_IMPLEMENTATION_CHECKLIST.md`](REDUNIV_IMPLEMENTATION_CHECKLIST.md).

## Próximos incrementos propuestos

1. Seguridad y calidad: rotar efectivamente las credenciales ya utilizadas en
   producción, rate limiting distribuido, auditoría y pruebas de seguridad funcionales. La suite heredada ya se adecuó al comportamiento 401 sin debilitar la autorización.
2. Modelo de depósito: enlaces privados temporales para revisores externos, pruebas de
   concurrencia/transiciones y evaluación curatorial de formatos/datos sensibles. Una versión nueva requiere cargar sus archivos.
3. Identidad científica: integrar registro DOI con el proveedor institucional,
   verificación ORCID/ROR, autores e instituciones múltiples, vocabularios de
   licencia/disciplinas y relaciones tipadas procesables por máquinas.
4. Citación: validar estilos con bibliotecarios, agregar Vancouver/Chicago/IEEE,
   descarga completa de metadatos y validación bibliográfica de citas.
5. Descubrimiento: facetas agregadas y estados de catálogo
   vacío, cero coincidencias, falta de permisos y fallo de búsqueda.
6. Depósito guiado: asistente con guardado de borrador, metadatos, archivos,
   documentación, licencia, privacidad, relaciones, revisión de calidad y
   vista previa antes de enviar a curación.
7. Curación y preservación: cola de revisión, vocabularios controlados,
   checksum SHA-256, operación de la verificación periódica, procedencia de archivos/datos,
   preservación y métricas definidas.
8. Interfaz: rutas públicas solo para objetos publicados, separación de áreas
   investigador/curador/admin, componentes semánticos, accesibilidad WCAG 2.2
   AA, internacionalización y pruebas con usuarios reales.

Los cambios de esquema y API deben migrarse sin perder datos y aprobarse antes
de producción. No activar exposición pública solo mediante cambios de CSS o
HTML: requiere autorización y estados de publicación en el backend.
