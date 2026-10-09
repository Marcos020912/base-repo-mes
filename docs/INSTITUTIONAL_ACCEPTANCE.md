# Datos necesarios para aceptar y activar el despliegue

Trabajo de desarrollo en `develop-reduniv`. Este documento no autoriza publicar,
fusionar ramas, cambiar contraseñas ni ejecutar operaciones en producción.
No enviar secretos por chat, correo ordinario ni Git.

| Responsable | Entrega concreta | Qué permite cerrar |
|---|---|---|
| Acceso — decisión confirmada | Mantener registro y login propios de la plataforma. No solicitar proveedor ni cliente OIDC. | Acceso institucional fuera del alcance actual; no es un pendiente. |
| DOI — confirmado por el usuario | Integración ya resuelta. | No solicitar nuevamente sus credenciales; ensayos locales no publican DOI reales. |
| Estadísticas — decisión confirmada | Conservar únicamente métricas locales; tracker externo desactivado. | No se requiere ID DataCite, transferencia externa ni reporte mensual del proveedor para esta entrega. |
| Pruebas / DevOps | PC del usuario como entorno de pruebas; el usuario coordina DevOps. | Validación local ahora; cambios en producción solo con autorización. |
| Administración | Administrador responsable de las políticas de acceso. | Revisar reglas de la plataforma; no implica certificación externa. |
| Revisión humana | Administrador responsable de revisión funcional, terminología y usabilidad. | Complementar pruebas automáticas; no afirmar certificación de accesibilidad. |
| Seguridad | Responsable/autorización para revisar y rotar credenciales antiguas, analizar artefactos históricos y coordinar historia Git/releases si es necesario. | Cerrar exposición histórica sin romper instalaciones/clones unilateralmente. |
| Responsable de publicación | Autorización de revisión/merge y ventana de mantenimiento; Release/JAR/SHA-256 aprobado. | Entregar los cambios desarrollados a staging y después a producción. |

No hace falta conocer los tecnicismos: reenviar esta tabla a infraestructura y
al responsable del repositorio. Pueden contestar «no existe» o «no se exige»
cuando corresponda; así se fija el alcance en vez de inventar integraciones.

## Propuesta técnica de operación para aprobación

- Publicar solo después de validación científica y editorial; proteger notas privadas.
- Versiones publicadas inmutables; correcciones mediante nueva versión y retirada
  controlada con ficha permanente. Aprobar el texto y alcance de la retirada.
- Licencia/acceso explícitos; revisar datos personales o confidenciales antes de
  publicación. La herramienta no sustituye la evaluación institucional.
- Registrar identidad y procedencia declaradas sin presentar búsquedas por nombre
  como identidad verificada, ni declaraciones de procesamiento como ejecución probada.
- Definir periodicidad/retención de respaldos, copias independientes, responsables,
  capacidad y objetivos de recuperación. No declarar preservación a largo plazo
  por tener un checksum o un ZIP.
- Mapear los paquetes de depósito, preservación y distribución a los perfiles
  institucionales aprobados; BagIt/RO-Crate/PROV existentes no certifican OAIS.
- Contadores locales y DataCite separados; consentimiento y política de terceros
  antes de habilitar estadísticas externas. Expediente interno no es certificación.

Estas son reglas técnicas propuestas, **no políticas institucionales aprobadas**.
El expediente de autoevaluación permite guardar responsables y enlaces a la
resolución/evidencia cuando existan.
