(() => {
  const id = new URLSearchParams(location.search).get('id');
  if (!id) return;
  const anchor = document.querySelector('#history-panel');
  if (!anchor) return;
  const panel = document.createElement('section'); panel.className = 'panel';
  const head = document.createElement('div'); head.className = 'section-heading';
  const title = document.createElement('h2'); title.textContent = 'Financiación y proyectos';
  const edit = document.createElement('button'); edit.type = 'button'; edit.className = 'secondary';
  edit.textContent = 'Editar financiación'; edit.hidden = true;
  head.append(title, edit);
  const listing = document.createElement('div'); listing.className = 'related-list'; listing.setAttribute('aria-live', 'polite');
  panel.append(head, listing); anchor.before(panel);

  const dialog = document.createElement('dialog'); dialog.className = 'modal';
  const form = document.createElement('form');
  const heading = document.createElement('h2'); heading.textContent = 'Financiación del depósito';
  const hint = document.createElement('p'); hint.textContent = 'Registra la entidad financiadora y, si existe, el proyecto o subvención. Se enviarán a DataCite al publicar.';
  const rows = document.createElement('div'); rows.className = 'related-editor';
  const add = document.createElement('button'); add.type = 'button'; add.className = 'secondary'; add.textContent = '+ Añadir financiación';
  const actions = document.createElement('div'); actions.className = 'button-row';
  const cancel = document.createElement('button'); cancel.type = 'button'; cancel.className = 'secondary'; cancel.textContent = 'Cancelar'; cancel.onclick = () => dialog.close();
  const save = document.createElement('button'); save.type = 'submit'; save.className = 'primary'; save.textContent = 'Guardar financiación';
  actions.append(cancel, save); form.append(heading, hint, rows, add, actions); dialog.append(form); document.body.append(dialog);
  let current = [];
  function input(name, label, value, max, required = false) {
    const wrapper = document.createElement('label'); wrapper.textContent = label;
    const control = document.createElement('input'); control.name = name; control.value = value || '';
    control.maxLength = max; control.required = required; wrapper.append(control); return wrapper;
  }
  function addRow(item = {}) {
    const row = document.createElement('div'); row.className = 'related-editor-row';
    row.append(input('funderName', 'Financiador', item.funderName, 255, true),
      input('funderRor', 'ROR del financiador (opcional)', item.funderRor, 255),
      input('awardNumber', 'Número de proyecto/subvención', item.awardNumber, 100),
      input('awardTitle', 'Nombre del proyecto', item.awardTitle, 500));
    const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'danger-outline';
    remove.textContent = 'Quitar'; remove.onclick = () => row.remove(); row.append(remove); rows.append(row);
  }
  add.onclick = () => { if (rows.childElementCount < 20) addRow(); };
  edit.onclick = () => { rows.replaceChildren(); current.forEach(addRow); dialog.showModal(); };
  form.addEventListener('submit', async event => {
    event.preventDefault(); save.disabled = true;
    const values = [...rows.children].map(row => Object.fromEntries([...row.querySelectorAll('[name]')]
      .map(control => [control.name, control.value.trim()])));
    try {
      const response = await fetch(`/api/v1/scientific/${encodeURIComponent(id)}/funding`, {
        method: 'PUT', headers: auth.headers({'Content-Type': 'application/json'}), body: JSON.stringify(values)
      });
      if (!response.ok) { const error = await response.json().catch(() => ({})); throw new Error(error.detail || 'No se pudo guardar la financiación.'); }
      dialog.close(); toast.success('Financiación actualizada.'); await load();
    } catch (error) { toast.error(error.message); } finally { save.disabled = false; }
  });
  async function load() {
    try {
      const response = await fetch(`/api/v1/scientific/${encodeURIComponent(id)}/funding`, {headers: auth.headers()});
      if (!response.ok) { panel.hidden = true; return; }
      current = await response.json(); panel.hidden = false; listing.replaceChildren();
      if (!current.length) { const empty = document.createElement('p'); empty.textContent = 'No hay financiación registrada.'; listing.append(empty); }
      for (const item of current) {
        const row = document.createElement('p'); row.className = 'related-resource';
        const name = document.createElement('strong'); name.textContent = item.funderName; row.append(name);
        if (item.funderRor) {
          const link = document.createElement('a'); link.href = `https://ror.org/${item.funderRor.replace(/^https:\/\/ror\.org\//, '')}`;
          link.textContent = 'ROR'; link.rel = 'noopener noreferrer'; row.append(' · ', link);
        }
        if (item.awardTitle) row.append(` · ${item.awardTitle}`);
        if (item.awardNumber) row.append(` (${item.awardNumber})`);
        listing.append(row);
      }
      const [science, mine] = await Promise.all([
        fetch(`/api/v1/scientific/${encodeURIComponent(id)}`, {headers: auth.headers()}).then(r => r.json()),
        fetch('/api/v1/my-dataresources', {headers: auth.headers()}).then(r => r.json())
      ]);
      edit.hidden = science.status !== 'DRAFT' || !Array.isArray(mine) || !mine.some(item => item.id === id);
    } catch { panel.hidden = true; }
  }
  load();
})();
