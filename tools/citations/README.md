# Validación independiente de exportaciones bibliográficas

Estas dependencias se usan **solo para pruebas**, no para arrancar ni desplegar Base Repo.
Los archivos de entrada los genera el controlador real en una prueba Java; no son una
segunda implementación del exportador.

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew --offline --no-daemon test --tests '*ScientificCitationControllerTest' --rerun-tasks
python3 -m venv /tmp/reduniv-citation-venv
/tmp/reduniv-citation-venv/bin/pip install -r tools/citations/requirements.txt
/tmp/reduniv-citation-venv/bin/python tools/citations/check_roundtrip.py
/tmp/reduniv-citation-venv/bin/python -m unittest discover -s tools/citations -p 'test_*.py'
```

Si las dependencias ya están instaladas no hay que descargarlas otra vez. Los fixtures
se escriben en `build/citation-fixtures`, ignorado por Git. Después de `clean` hay que
repetir la prueba Java. No usar datos de producción para esta comprobación.

## Qué se comprueba

- Pybtex interpreta dataset, persona con nombre compuesto y organización literal sin
  separar «Research and Development» en dos autores; DOI, versión, año y editor exactos.
- Bibtexparser conserva todos los campos y autores en lectura/escritura/lectura, incluidos
  los escapes TeX de llaves, porcentaje, ampersand, barra invertida y demás símbolos.
- Rispy conserva título UTF-8 original, autores, editor, DOI y versión (`ET`) al volver
  a escribir y leer RIS.
- CSL-JSON coincide con la versión y metadatos de origen, personas y organización literal.
- Cuatro pruebas del verificador: exportación válida y rechazo de versión RIS incorrecta,
  organización BibTeX separada y DOI CSL incorrecto.

Pybtex devuelve nombres compuestos separados en `first_names` y `middle_names`.
Su writer vuelve a codificar campos TeX ya escapados; por eso la comprobación exacta de
round-trip BibTeX utiliza Bibtexparser, sin parchear bibliotecas ni ocultar diferencias.
Bibtexparser 1.4.3 emite avisos de deprecación con Pyparsing reciente: no se silencian.

Referencias de los proyectos: [Pybtex](https://docs.pybtex.org/api/parsing.html),
[Rispy](https://github.com/MrTango/rispy),
[Bibtexparser 1.x](https://bibtexparser.readthedocs.io/en/v1.4.0/).

## Límites

No sustituye la aceptación de una biblioteca institucional ni acredita conformidad
formal de los estilos APA/IEEE/Chicago/Vancouver. Tampoco prueba importación visual
por cada versión de Zotero/Mendeley/EndNote ni la ergonomía de dos acciones en todas
las vistas. F05 sigue parcial hasta completar esas verificaciones.
