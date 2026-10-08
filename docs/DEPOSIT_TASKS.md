# Tareas del autor

Mis depósitos incluye una cola paginada de borradores y depósitos en revisión del usuario autenticado. Cada tarjeta muestra título, estado, porcentaje del checklist automático, campos pendientes con su explicación y la próxima acción. Un depósito en revisión permanece bloqueado para edición; el enlace permite consultarlo.

La cola no muestra depósitos de otros autores, registros publicados/retirados ni referencias huérfanas. La API `GET /api/v1/my-deposit-tasks?page=0&size=10` admite tamaños de 1 a 50 y páginas desde cero; no admite elegir otro usuario. El servidor determina el propietario a partir de la sesión. Los publicados continúan visibles en el listado general de Mis depósitos.

La inspección de calidad se ejecuta solo para la página solicitada. Actualmente se obtienen por lotes los metadatos básicos de los depósitos propios para excluir estados y referencias borradas; la escala institucional aún requiere medir rendimiento. No se filtran únicamente los primeros 200 recursos en el navegador.

El porcentaje es una ayuda editorial, no aprobación, certificación, autorización de publicación ni garantía de anonimización. La privacidad y el flujo de publicación mantienen sus guardas independientes. Los errores de consulta se distinguen de una cola sin tareas y permiten reintentar.
