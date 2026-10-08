# Procedencia científica y eventos técnicos

La ficha de cada versión permite declarar **producción y origen**, **procesamiento** y **herramientas/versiones**. La declaración se edita únicamente por el autor en borrador, se conserva al abrir una nueva versión y se incluye en la vista previa y ficha pública. Al omitir los nuevos campos en una actualización se conservan; una cadena vacía los borra. Límites: 5.000 caracteres por producción/procesamiento y 2.000 para herramientas.

Estos campos son metadatos públicos. No introduzca datos personales, contraseñas ni rutas internas. Los enlaces a scripts y fuentes deben poder compartirse conforme a la licencia y política de acceso. La revisión de privacidad también debe comprobar la descripción y los metadatos, no solo los archivos.

## Dos tipos de evidencia

1. **Declaración del depositante**: explica fuentes, recogida/simulación, selección, limpieza, transformaciones, exclusiones, software, versiones y parámetros necesarios para reproducir los resultados. La plataforma no afirma que haya ejecutado esos pasos ni verificado científicamente sus resultados.
2. **Eventos técnicos registrados**: carga/eliminación de archivos, actor, fecha y huella observados por los endpoints auditados. No permiten inferir cómo se produjeron los datos antes de cargarse.

En los paquetes curatoriales, RO-Crate incluye la declaración como `CreativeWork` contextual enlazado al dataset. PROV-O la representa como entidad descriptiva adicional: **no inventa actividades de procesamiento, actores, fechas ni relaciones `wasGeneratedBy`**. Los eventos de archivos conservan sus actividades técnicas observadas. El JSON de metadatos retiene los tres campos estructurados.

El mapper DataCite incluye la declaración en descripciones `Methods`, explícitamente identificada como declaración no verificada. La integración DataCite real y la validación externa de perfiles de preservación siguen pendientes; exportar RO-Crate/PROV no acredita conformidad OAIS ni CoreTrustSeal.
