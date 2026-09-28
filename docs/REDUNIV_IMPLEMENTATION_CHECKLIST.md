# Seguimiento de implementación RedUniv

Estado de `develop-reduniv` respecto al informe y prototipo. Una casilla marcada
significa implementada y probada en desarrollo, **no** autorizada para producción.
No fusionar con `main` sin aprobación. Actualizar esta lista al terminar cada ítem.

## Implementado en la rama

- [x] Identidad visual, portada científica, ficha con metadatos reales y DOI solo cuando existe.
- [x] Catálogo público separado de borradores, paginación, búsqueda y filtros en servidor.
- [x] Estados de borrador, revisión, publicación y retirada; cola y vista de curación.
- [x] Control de acceso a archivos restringidos o embargados y aviso de versión sucesora.
- [x] Checklist de calidad antes de enviar a revisión e historial editorial.
- [x] Versiones enlazadas y bloqueo de edición de la versión publicada.
- [x] Exportación preliminar APA, BibTeX, RIS y CSL-JSON.
- [x] SHA-256 al cargar por la interfaz web, comprobación manual por curador y
      estado independiente visible en la ficha pública. Auditoría programada
      **opcional** (desactivada por defecto).
- [x] Cliente backend DataCite y mapeo de metadatos obligatorios y opcionales
      seguros (licencia, materias, institución/ROR y ORCID cuando hay un solo
      autor), probados contra HTTP simulado; desactivados por defecto y sin
      credenciales en Git.
- [x] Reserva DOI Draft conceptual y por versión, registro persistente,
      reintentos/reconciliación, publicación Findable por curador y landing
      permanente; despliegue pregunta la configuración DOI sin mostrar secretos.
      Una URL base HTTPS válida es requisito antes de la reserva.
- [x] Recuperación del registro cuando falla SMTP y rutas de verificación accesibles.
- [x] Cambio de contraseña propia, revocación de tokens previos y respuesta 401 anónima.

## Pendiente o sujeto a validación

- [ ] Validar migración/retroceso con copia de PostgreSQL y archivos en entorno de prueba.
- [ ] Probar funcionalmente con usuarios reales y revisar accesibilidad WCAG 2.2 AA.
- [ ] Rotar credenciales de producción; pruebas de seguridad y limitación distribuida de solicitudes.
- [ ] Enlaces privados temporales para revisores externos y pruebas de concurrencia editorial.
- [ ] Validar el flujo DOI real con cuenta Repository, prefijo y credenciales
      de DataCite Test/Production; probar resolución pública y conciliar DOI
      manuales existentes antes de activar el modo automático en producción.
- [ ] Verificar ORCID/ROR y admitir múltiples autores e instituciones.
- [ ] Vocabularios controlados de licencia/disciplinas y relaciones tipadas.
- [ ] Validación bibliotecaria de citas y estilos adicionales.
- [ ] Facetas agregadas y estados explícitos de resultados vacíos/fallo de búsqueda.
- [ ] Asistente de depósito guiado y vista previa integral antes de envío.
- [ ] Definir operación de auditoría SHA-256 (I/O, calendario, alertas y bloqueo
      distribuido si hay varias instancias); gestionar archivos antiguos sin huella.
- [ ] Paquetes de preservación/procedencia, métricas e internacionalización.

## Operación de la auditoría SHA-256

La verificación manual usa `POST /api/v1/scientific/{id}/fixity?path=...` y exige
rol CURATOR o ADMINISTRATOR. Compara el archivo actual con la huella registrada
al subirlo, sin modificar esa referencia. Posibles estados: `MATCH`, `MISMATCH`,
`MISSING_FILE`, `NO_BASELINE`, `UNSUPPORTED_URI`, `READ_ERROR`.

Después de aplicar la migración, la comprobación semanal puede activarse en una
instancia con `repo.fixity.enabled=true`; su cron por defecto es
`0 0 3 * * SUN` y puede cambiarse con `repo.fixity.cron`. Antes de activarla,
medir el impacto de lectura de todos los archivos y definir alertas. No habilitar
simultáneamente en múltiples instancias sin un bloqueo distribuido.
