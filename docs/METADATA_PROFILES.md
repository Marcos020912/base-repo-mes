# Perfiles de metadatos de depósito

Los administradores proponen, aprueban y activan/desactivan perfiles desde `metadata-profiles-admin.html`. Una propuesta no sustituye la definición aprobada. El identificador estable no se cambia; no hay borrado destructivo del registro. La aprobación es administrativa local, no una certificación institucional.

## Contenido y límites

Un perfil tiene nombre, descripción, valores iniciales opcionales de licencia/idioma/disciplina y una selección de campos adicionales obligatorios. Nunca relaja los requisitos base, inventa DOI/ORCID/autoría, modifica privacidad o escribe declaraciones metodológicas. Los valores iniciales se validan contra los vocabularios cuando está activo el modo estricto. No se reemplazan valores no vacíos declarados por el autor.

Los campos adicionales admitidos son resumen, idioma, disciplina, palabras clave, producción/origen, procesamiento, herramientas/versiones, fechas de cobertura, región y traducciones. Un requisito automático verifica presencia, no calidad científica ni validez jurídica. La aprobación y revisión humana siguen siendo necesarias. El idioma inicial se normaliza como BCP47. Hay un límite administrativo de cien perfiles para evitar crecimiento accidental; las listas no son catálogos externos masivos.

## Aplicación y congelación

El autor aplica o quita un perfil de su DRAFT mediante confirmación explícita. El backend exige la revisión del borrador y la revisión aprobada elegida; un cambio concurrente obliga a recargar. Curadores/administradores no pueden aplicar perfiles a datasets ajenos. IN_REVIEW/PUBLISHED/WITHDRAWN no pueden cambiar de perfil.

Cada dataset guarda identificador, nombre, revisión aprobada y copia de requisitos. Cambiar/desactivar el registro no altera la copia. Quitar un perfil de un borrador no elimina metadatos ya declarados. La calidad y el envío a revisión usan la copia; la publicación manual también comprueba requisitos adicionales. DataCite usa el mismo inspector de calidad.

El asistente permite revisar la selección, completa solo campos vacíos y muestra las reglas antes de guardar. Autoguarda la selección como datos de formulario (no notas privadas). La selección se vuelve a validar en el backend al aplicar; el navegador no es una autoridad. Nuevas versiones derivadas conservan la copia de reglas del predecesor, aunque ese perfil esté desactivado, y pueden cambiarla explícitamente mientras son borradores.

Los perfiles son opcionales en este bloque: no se inventa un perfil institucional obligatorio ni se aplica retroactivamente a depósitos anteriores. La identidad institucional, las listas de autoridad y la aprobación organizativa requieren decisiones externas.

## Persistencia y despliegue

Las tablas de perfiles/reglas y las copias por dataset forman parte del backup PostgreSQL. Migración idempotente en `docs/migrations/2026-09-scientific-records.sql`, sin ejecutar contra producción. No hay FK desde el identificador congelado a la definición viva: la copia debe sobrevivir a cambios administrativos del registro.

Este documento describe el alcance implementado; la evidencia de pruebas se registra en `REDUNIV_EXECUTION_PLAN.md` al completar la validación.
