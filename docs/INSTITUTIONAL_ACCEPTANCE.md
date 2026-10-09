# Datos necesarios para aceptar y activar el despliegue

Trabajo de desarrollo en `develop-reduniv`. Este documento no autoriza publicar,
fusionar ramas, cambiar contraseñas ni ejecutar operaciones en producción.
No enviar secretos por chat, correo ordinario ni Git.

| Responsable | Entrega concreta | Qué permite cerrar |
|---|---|---|
| Infraestructura/identidad | Confirmar si hay inicio de sesión institucional. Si existe: proveedor, URL de descubrimiento OIDC, cliente y callback autorizados por canal seguro; reglas de vinculación de cuentas. Si no existe: aprobar login local. | Integración y ensayo de acceso institucional, sin confundir ORCID con login. |
| Responsable DOI | Cuenta/prefijo y configuración privada Test/producción; autorización para reserva/publicación/versiones de un dataset sintético; dominio permanente aprobado. | Ensayo externo Test→staging→producción sin acuñar DOI reales a ciegas. |
| Responsable estadísticas | ID DataCite `da-...`, aprobación de la transferencia a terceros y permiso para emitir eventos de prueba humana. | Activar tracker; comprobar recepción y reporte mensual siguiente. No otorga certificación por sí solo. |
| Infraestructura | VM de staging, configuración HAProxy/DNS/TLS, nombres/CA SMTP y cuenta destinataria autorizada; límites y volumen representativo. | Comprobar registro, entrega, carga/búsqueda/descarga, tiempos y capacidad reales. |
| Dirección/curación | Responsable y aprobación de políticas de acceso, privacidad, conservación, retirada y perfiles de archivo. | Convertir propuesta técnica y expediente de autoevaluación en decisiones institucionales. |
| Biblioteca/usuarios | Personas designadas para revisar citas e importación, teclado/lector de pantalla, terminología es/en y tareas de investigación/curación. | Aceptación humana; pruebas automáticas no la sustituyen. |
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
