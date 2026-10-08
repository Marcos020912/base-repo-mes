# Historial público de versiones

La ficha publicada muestra una lista paginada de versiones públicas, identificando la versión exacta abierta, DOI, fecha y condición publicada/retirada. El enlace de una versión retirada conserva su ficha permanente de retirada; no se convierte en descarga de contenido.

La API anónima `GET /api/v1/public/resources/{id}/versions?page=0&size=20` recorre únicamente enlaces explícitos `previousResourceId` hacia padres e hijas. Incluye ramas/hermanas públicas y retiene retiradas. No agrupa registros por coincidir una cadena de DOI conceptual; esa coincidencia por sí sola no demuestra una relación de versiones.

Se excluyen borradores, depósitos en revisión y referencias sin recurso existente. No se atraviesan nodos privados ni se devuelven sus identificadores. Una raíz privada o huérfana responde no encontrada. Los resultados no contienen cuentas, actores, notas de privacidad ni historial editorial privado.

El servidor admite tamaños de 1 a 50 y ordena por publicación descendente, con identificador como desempate. Un conjunto corrupto con ciclos no causa un recorrido infinito. La familia se limita a 500 nodos consultados: superar el límite produce fallo explícito, nunca una lista parcial presentada como completa. La escala institucional requiere medición y, si corresponde, una consulta de linaje más especializada.

Este historial de versiones no sustituye un historial público de diferencias de metadatos. La publicación de eventos editoriales requiere política institucional separada.

El aviso de publicación más reciente se calcula sobre toda la familia, no solo una hija directa, excluye retiradas y exige una fecha estrictamente posterior. Fechas ausentes o empatadas no se usan para adivinar anterioridad por el identificador o etiqueta de versión.
