# Asistente de depósito — nueve etapas

1. Identificación, tipo, versión y perfil opcional aprobado.
2. Autoría e institución; identificadores ORCID/ROR sin atribuir identidad verificada por nombre.
3. Resumen, metodología, procedencia, traducciones y cobertura.
4. Descripción y archivos: selección múltiple o paquete ZIP.
5. Licencia, acceso, embargo y declaración de privacidad.
6. Artículos, software, datasets y proyectos mediante relaciones DataCite tipadas (DOI o URL HTTPS), además de publicaciones y financiación.
7. Precomprobación **local**: campos presentes y requisitos del perfil congelado.
8. Vista previa de metadatos, descripción seleccionada, archivos y cita provisional.
9. Cierre: guardar borrador o confirmar revisión para solicitar curación.

Las comprobaciones locales no certifican identidad, integridad del ZIP, calidad científica ni aprobación jurídica. El servidor valida permisos, estado, extensiones, extracción y requisitos antes de completar el flujo. Los ZIP exigen revisar la ficha real después de guardarlos antes de enviarlos a curación. No se inventa un DOI para la cita provisional.

El guardado local recupera metadatos; el navegador no conserva archivos seleccionados. Se solicita seleccionarlos de nuevo. La navegación enfoca el encabezado de cada etapa y la vista previa debe terminar antes de avanzar al cierre.

## Evidencia local

- JAR Java 21 compilado en perfil minimal.
- E2E aislado con PostgreSQL, SMTP y Chrome: Markdown, ZIP de descripción, paquete completo, subida interrumpida y nueva versión; restauración de base de datos y archivos.
- Axe: 66 estados, cero infracciones automáticas. Esto no sustituye revisión manual WCAG.
- Relaciones tipadas de creación: validación local de DOI/HTTPS y duplicados; API de autor valida de nuevo antes de persistir. Se conservan en la recuperación local y se proponen desde la versión anterior, sin modificarla.
