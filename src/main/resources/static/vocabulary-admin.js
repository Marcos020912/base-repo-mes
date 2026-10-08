(async () => {
  auth.requireLogin(); const user = auth.user(); if (!auth.token() || !user) return;
  if (user.role !== 'ADMINISTRATOR') { location.replace('index.html'); return; }
  document.querySelector('#current-user').textContent = user.username;
  document.querySelector('[data-logout]').onclick = auth.logout;
  const root = '/api/v1/scientific/vocabularies/administration';
  const status = document.querySelector('#vocabulary-status'); let selected = null, requestId = 0;
  function node(tag, value = '') { const element = document.createElement(tag); element.textContent = value; return element; }
  function label(tag, key, params = {}) { const element = node(tag); uiI18n.set(element, key, params); return element; }
  async function request(path = '', options = {}) {
    const response = await fetch(root + path, {...options, headers: auth.headers({'Content-Type': 'application/json'}), cache: 'no-store'});
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw body.detail ? Error(body.detail) : uiI18n.error('vocabulary.failure');
    return body;
  }
  async function load() {
    const id = ++requestId; uiI18n.set(status, 'vocabulary.loading');
    const container = document.querySelector('#vocabulary-lists'); container.replaceChildren();
    try {
      const lists = await request(); if (id !== requestId) return;
      for (const entry of lists) {
        const card = node('section'); card.className = 'panel';
        card.append(entry.kind === 'LICENSE' ? label('h2', 'vocabulary.licenses') : entry.kind === 'DISCIPLINE' ? label('h2', 'vocabulary.disciplines') : node('h2', entry.kind));
        const scope = node('p'); scope.append(label('span', 'vocabulary.source'), node('span', ': '),
          label('span', entry.source === 'APPROVED_REGISTRY' ? 'vocabulary.registry' : 'vocabulary.configuration'), node('span', ' · '),
          label('span', 'vocabulary.strict'), node('span', ': '), label('span', entry.strict ? 'vocabulary.yes' : 'vocabulary.no'));
        card.append(scope); const active = node('ul');
        for (const value of entry.effectiveValues) active.append(node('li', value)); card.append(active);
        if (entry.approvedAt) card.append(label('p', 'vocabulary.approvedAt', {date: entry.approvedAt, actor: entry.approvedBy}));
        const propose = label('button', 'vocabulary.propose'); propose.className = 'secondary';
        propose.onclick = () => {
          selected = entry; const form = document.querySelector('#vocabulary-form');
          form.elements.values.value = (entry.proposedValues.length ? entry.proposedValues : entry.effectiveValues).join('\n');
          form.elements.note.value = entry.proposedValues.length ? entry.proposalNote || '' : '';
          uiI18n.set(document.querySelector('#vocabulary-modal-title'), entry.kind === 'LICENSE' ? 'vocabulary.proposeLicenses' : entry.kind === 'DISCIPLINE' ? 'vocabulary.proposeDisciplines' : 'vocabulary.proposeGeneric');
          document.querySelector('#vocabulary-modal').showModal();
        }; card.append(propose);
        if (entry.proposedValues.length) {
          card.append(label('h3', 'vocabulary.pending'), node('p', entry.proposalNote),
            label('p', 'vocabulary.proposedAt', {date: entry.proposedAt, actor: entry.proposedBy}));
          const pending = node('ul'); for (const value of entry.proposedValues) pending.append(node('li', value)); card.append(pending);
          const approve = label('button', 'vocabulary.review'); approve.className = 'primary';
          approve.onclick = () => {
            selected = entry; document.querySelector('#vocabulary-approval-values').replaceChildren(...entry.proposedValues.map(value => node('li', value)));
            document.querySelector('#vocabulary-approval-modal').showModal();
          }; card.append(approve);
        }
        container.append(card);
      }
      uiI18n.set(status, 'vocabulary.loaded');
    } catch (error) { if (id === requestId) { uiI18n.showError(status, error); toast.errorObject(error); } }
  }
  document.querySelector('#refresh-vocabulary').onclick = load;
  document.querySelectorAll('[data-close-vocabulary]').forEach(button => button.onclick = () => button.closest('dialog').close());
  document.querySelector('#vocabulary-form').onsubmit = async event => {
    event.preventDefault(); const form = event.currentTarget, button = form.querySelector('[type=submit]'); button.disabled = true;
    try {
      await request('/' + selected.kind, {method: 'PUT', body: JSON.stringify({values: form.elements.values.value.split(/\r?\n/).map(value => value.trim()).filter(Boolean), note: form.elements.note.value, revision: selected.revision})});
      document.querySelector('#vocabulary-modal').close(); toast.successKey('vocabulary.saved'); await load();
    } catch (error) { toast.errorObject(error); } finally { button.disabled = false; }
  };
  document.querySelector('#vocabulary-approval-form').onsubmit = async event => {
    event.preventDefault(); const button = event.currentTarget.querySelector('[type=submit]'); button.disabled = true;
    try {
      await request('/' + selected.kind + '/approve', {method: 'POST', body: JSON.stringify({revision: selected.revision})});
      document.querySelector('#vocabulary-approval-modal').close(); toast.successKey('vocabulary.approved'); await load();
    } catch (error) { toast.errorObject(error); } finally { button.disabled = false; }
  };
  await load();
})();
