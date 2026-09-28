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
como máximo 200 recursos; sus filtros y paginación siguen siendo locales. La
cita no representa todavía un formato bibliográfico certificado ni una versión
publicada inmutable. Este incremento no implementa DOI, licencias o flujo de
publicación.

## Próximos incrementos propuestos

1. Seguridad y calidad: rotar la credencial utilizada en la revisión, retirar
   secretos predeterminados, pruebas de autorización por recurso, rate limiting,
   auditoría y suite automatizada.
2. Modelo de depósito: estados de borrador, revisión, publicación, restricción
   y retirada; versión publicada inmutable; nueva versión para corregir datos
   publicados; políticas de eliminación y tombstone.
3. Identidad científica: DOI conceptual y de versión, autores/ORCID,
   organizaciones/ROR, licencia, acceso, fechas, idioma, disciplina y
   relaciones con publicaciones y software. Validar cada dato en backend.
4. Citación: generación por versión, estilos bibliográficos y exportaciones
   BibTeX, RIS y CSL-JSON; descarga de metadatos y advertencia de versiones
   antiguas.
5. Descubrimiento: API de búsqueda paginada en servidor con facetas, orden y
   filtros reflejados en URL. Diferenciar catálogo vacío, cero coincidencias,
   falta de permisos y fallo de búsqueda.
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
