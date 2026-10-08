(() => {
  const id = new URLSearchParams(location.search).get('id');
  if (!id) return;
  const panel = document.createElement('section'); panel.className = 'panel';
  const head = document.createElement('div'); head.className = 'section-heading';
  const title = document.createElement('h2'); uiI18n.set(title, 'relations.heading');
  const edit = document.createElement('button'); edit.type = 'button'; edit.className = 'secondary'; uiI18n.set(edit, 'relations.edit'); edit.hidden = true;
  head.append(title, edit);
  const listing = document.createElement('div'); listing.className = 'related-list'; listing.setAttribute('aria-live','polite');
  panel.append(head, listing);
  document.querySelector('#history-panel').before(panel);

  const dialog = document.createElement('dialog'); dialog.className = 'modal';
  const form = document.createElement('form');
  const heading = document.createElement('h2'); uiI18n.set(heading, 'relations.modal');
  const hint = document.createElement('p'); uiI18n.set(hint, 'relations.hint');
  const rows = document.createElement('div'); rows.className = 'related-editor';
  const add = document.createElement('button'); add.type = 'button'; add.className = 'secondary'; uiI18n.set(add, 'relations.add');
  const actions = document.createElement('div'); actions.className = 'button-row';
  const cancel = document.createElement('button'); cancel.type = 'button'; cancel.className = 'secondary'; uiI18n.set(cancel, 'relations.cancel'); cancel.onclick = () => dialog.close();
  const save = document.createElement('button'); save.type = 'submit'; save.className = 'primary'; uiI18n.set(save, 'relations.save');
  actions.append(cancel, save); form.append(heading, hint, rows, add, actions); dialog.append(form); document.body.append(dialog);
  const kinds = ['ARTICLE','SOFTWARE','DATASET','PROJECT','OTHER'];
  const relationTypes = ['IsCitedBy','Cites','IsSupplementTo','IsSupplementedBy','IsReferencedBy','References','IsDocumentedBy','Documents','IsDerivedFrom','IsSourceOf','IsPartOf','HasPart'];
  let current = [];
  function select(name, values, selected) {
    const control = document.createElement('select'); control.name = name;
    for (const value of values) { const option = document.createElement('option'); option.value = value; option.textContent = value; control.append(option); }
    control.value = selected || values[0]; return control;
  }
  function labelled(key, control) { const wrapper = document.createElement('label'); const label = document.createElement('span'); uiI18n.set(label, key); wrapper.append(label, control); return wrapper; }
  function addRow(data = {}) {
    const row = document.createElement('div'); row.className = 'related-editor-row';
    const kind = select('kind', kinds, data.kind);
    const identifierType = select('identifierType', ['DOI','URL'], data.identifierType);
    const relationType = select('relationType', relationTypes, data.relationType);
    const identifier = document.createElement('input'); identifier.name = 'identifier'; identifier.required = true; identifier.maxLength = 500; identifier.value = data.identifier || '';
    const title = document.createElement('input'); title.name = 'title'; title.maxLength = 255; title.value = data.title || '';
    const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'danger-outline'; uiI18n.set(remove, 'relations.remove'); remove.onclick = () => row.remove();
    row.append(labelled('relations.kind',kind), labelled('relations.identifierType',identifierType),
      labelled('relations.type',relationType), labelled('relations.identifier',identifier), labelled('relations.title',title), remove);
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
      if (!response.ok) { const error = await response.json().catch(() => ({})); throw error.detail || error.message ? new Error(error.detail || error.message) : uiI18n.error('relations.failure'); }
      dialog.close(); toast.successKey('relations.success'); await load();
    } catch (error) { toast.errorObject(error); } finally { save.disabled = false; }
  });
  async function load() {
    try {
      const response = await fetch(`/api/v1/scientific/${encodeURIComponent(id)}/relations`, {headers:auth.headers()});
      if (!response.ok) { panel.hidden = true; return; }
      current = await response.json(); panel.hidden = false; listing.replaceChildren();
      if (!current.length) { const empty = document.createElement('p'); uiI18n.set(empty, 'relations.empty'); listing.append(empty); }
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
