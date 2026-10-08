(() => {
  const id = new URLSearchParams(location.search).get('id');
  if (!id) return;
  const anchor = document.querySelector('#history-panel');
  if (!anchor) return;
  const panel = document.createElement('section'); panel.className = 'panel';
  const head = document.createElement('div'); head.className = 'section-heading';
  const title = document.createElement('h2'); uiI18n.set(title, 'funding.heading');
  const edit = document.createElement('button'); edit.type = 'button'; edit.className = 'secondary';
  uiI18n.set(edit, 'funding.edit'); edit.hidden = true;
  head.append(title, edit);
  const listing = document.createElement('div'); listing.className = 'related-list'; listing.setAttribute('aria-live', 'polite');
  panel.append(head, listing); anchor.before(panel);

  const dialog = document.createElement('dialog'); dialog.className = 'modal';
  const form = document.createElement('form');
  const heading = document.createElement('h2'); uiI18n.set(heading, 'funding.modal');
  const hint = document.createElement('p'); uiI18n.set(hint, 'funding.hint');
  const rows = document.createElement('div'); rows.className = 'related-editor';
  const add = document.createElement('button'); add.type = 'button'; add.className = 'secondary'; uiI18n.set(add, 'funding.add');
  const actions = document.createElement('div'); actions.className = 'button-row';
  const cancel = document.createElement('button'); cancel.type = 'button'; cancel.className = 'secondary'; uiI18n.set(cancel, 'funding.cancel'); cancel.onclick = () => dialog.close();
  const save = document.createElement('button'); save.type = 'submit'; save.className = 'primary'; uiI18n.set(save, 'funding.save');
  actions.append(cancel, save); form.append(heading, hint, rows, add, actions); dialog.append(form); document.body.append(dialog);
  let current = [];
  function input(name, key, value, max, required = false) {
    const wrapper = document.createElement('label'); const label = document.createElement('span'); uiI18n.set(label, key); wrapper.append(label);
    const control = document.createElement('input'); control.name = name; control.value = value || '';
    control.maxLength = max; control.required = required; wrapper.append(control); return wrapper;
  }
  function addRow(item = {}) {
    const row = document.createElement('div'); row.className = 'related-editor-row';
    row.append(input('funderName', 'funding.funder', item.funderName, 255, true),
      input('funderRor', 'funding.ror', item.funderRor, 255),
      input('awardNumber', 'funding.awardNumber', item.awardNumber, 100),
      input('awardTitle', 'funding.awardTitle', item.awardTitle, 500));
    const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'danger-outline';
    uiI18n.set(remove, 'funding.remove'); remove.onclick = () => row.remove(); row.append(remove); rows.append(row);
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
      if (!response.ok) { const error = await response.json().catch(() => ({})); throw error.detail ? new Error(error.detail) : uiI18n.error('funding.failure'); }
      dialog.close(); toast.successKey('funding.success'); await load();
    } catch (error) { toast.errorObject(error); } finally { save.disabled = false; }
  });
  async function load() {
    try {
      const response = await fetch(`/api/v1/scientific/${encodeURIComponent(id)}/funding`, {headers: auth.headers()});
      if (!response.ok) { panel.hidden = true; return; }
      current = await response.json(); panel.hidden = false; listing.replaceChildren();
      if (!current.length) { const empty = document.createElement('p'); uiI18n.set(empty, 'funding.empty'); listing.append(empty); }
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
