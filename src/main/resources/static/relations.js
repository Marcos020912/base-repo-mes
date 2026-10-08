(() => {
  const id = new URLSearchParams(location.search).get('id');
  if (!id) return;
  const panel = document.createElement('section'); panel.className = 'panel';
  const head = document.createElement('div'); head.className = 'section-heading';
  const title = document.createElement('h2'); title.textContent = 'Recursos relacionados';
  const edit = document.createElement('button'); edit.type = 'button'; edit.className = 'secondary'; edit.textContent = 'Editar relaciones'; edit.hidden = true;
  head.append(title, edit);
  const listing = document.createElement('div'); listing.className = 'related-list'; listing.setAttribute('aria-live','polite');
  panel.append(head, listing);
  document.querySelector('#history-panel').before(panel);

  const dialog = document.createElement('dialog'); dialog.className = 'modal';
  const form = document.createElement('form');
  const heading = document.createElement('h2'); heading.textContent = 'Relaciones científicas';
  const hint = document.createElement('p'); hint.textContent = 'Vincula artículos, software, proyectos u otros datasets mediante DOI o URL HTTPS.';
  const rows = document.createElement('div'); rows.className = 'related-editor';
  const add = document.createElement('button'); add.type = 'button'; add.className = 'secondary'; add.textContent = '+ Añadir relación';
  const actions = document.createElement('div'); actions.className = 'button-row';
  const cancel = document.createElement('button'); cancel.type = 'button'; cancel.className = 'secondary'; cancel.textContent = 'Cancelar'; cancel.onclick = () => dialog.close();
  const save = document.createElement('button'); save.type = 'submit'; save.className = 'primary'; save.textContent = 'Guardar relaciones';
  actions.append(cancel, save); form.append(heading, hint, rows, add, actions); dialog.append(form); document.body.append(dialog);
  const kinds = ['ARTICLE','SOFTWARE','DATASET','PROJECT','OTHER'];
  const relationTypes = ['IsCitedBy','Cites','IsSupplementTo','IsSupplementedBy','IsReferencedBy','References','IsDocumentedBy','Documents','IsDerivedFrom','IsSourceOf','IsPartOf','HasPart'];
  let current = [];
  function select(name, values, selected) {
    const control = document.createElement('select'); control.name = name;
    for (const value of values) { const option = document.createElement('option'); option.value = value; option.textContent = value; control.append(option); }
    control.value = selected || values[0]; return control;
  }
  function labelled(label, control) { const wrapper = document.createElement('label'); wrapper.textContent = label; wrapper.append(control); return wrapper; }
  function addRow(data = {}) {
    const row = document.createElement('div'); row.className = 'related-editor-row';
    const kind = select('kind', kinds, data.kind);
    const identifierType = select('identifierType', ['DOI','URL'], data.identifierType);
    const relationType = select('relationType', relationTypes, data.relationType);
    const identifier = document.createElement('input'); identifier.name = 'identifier'; identifier.required = true; identifier.maxLength = 500; identifier.value = data.identifier || '';
    const title = document.createElement('input'); title.name = 'title'; title.maxLength = 255; title.value = data.title || '';
    const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'danger-outline'; remove.textContent = 'Quitar'; remove.onclick = () => row.remove();
    row.append(labelled('Tipo de recurso',kind), labelled('Identificador',identifierType),
      labelled('Relación DataCite',relationType), labelled('DOI o URL HTTPS',identifier), labelled('Título',title), remove);
    rows.append(row);
  }
  add.onclick = () => { if (rows.childElementCount < 30) addRow(); };
  edit.onclick = () => { rows.replaceChildren(); current.forEach(addRow); dialog.showModal(); };
  form.addEventListener('submit', async event => {
    event.preventDefault(); save.disabled = true;
    const values = [...rows.children].map(row => Object.fromEntries(
      [...row.querySelectorAll('[name]')].map(control => [control.name, control.value])));
    try {
      const response = await fetch(`/api/v1/scientific/${encodeURIComponent(id)}/relations`, {
        method:'PUT', headers:auth.headers({'Content-Type':'application/json'}), body:JSON.stringify(values)
      });
      if (!response.ok) { const error = await response.json().catch(() => ({})); throw new Error(error.detail || error.message || 'No se pudieron guardar las relaciones.'); }
      dialog.close(); toast.success('Relaciones actualizadas.'); await load();
    } catch (error) { toast.error(error.message); } finally { save.disabled = false; }
  });
  async function load() {
    try {
      const response = await fetch(`/api/v1/scientific/${encodeURIComponent(id)}/relations`, {headers:auth.headers()});
      if (!response.ok) { panel.hidden = true; return; }
      current = await response.json(); panel.hidden = false; listing.replaceChildren();
      if (!current.length) { const empty = document.createElement('p'); empty.textContent = 'No hay relaciones registradas.'; listing.append(empty); }
      for (const item of current) {
        const row = document.createElement('p'); row.className = 'related-resource';
        const prefix = document.createElement('strong'); prefix.textContent = `${item.kind} · ${item.relationType}: `;
        const link = document.createElement('a'); link.href = item.identifierType === 'DOI' ? `https://doi.org/${item.identifier.split('/').map(encodeURIComponent).join('/')}` : item.identifier;
        link.textContent = item.title || item.identifier; link.rel = 'noopener noreferrer'; row.append(prefix, link); listing.append(row);
      }
      const [science, mine] = await Promise.all([
        fetch(`/api/v1/scientific/${encodeURIComponent(id)}`, {headers:auth.headers()}).then(r => r.json()),
        fetch('/api/v1/my-dataresources', {headers:auth.headers()}).then(r => r.json())
      ]);
      edit.hidden = science.status !== 'DRAFT' || !Array.isArray(mine) || !mine.some(item => item.id === id);
    } catch { panel.hidden = true; }
  }
  load();
})();
