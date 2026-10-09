(() => {
  const id = new URLSearchParams(location.search).get('id');
  if (!id) return;
  const callbackResult = new URLSearchParams(location.search).get('orcid');
  if (callbackResult) {
    if (callbackResult === 'authenticated') toast.successKey('creators.authenticated');
    else toast.errorKey('creators.authFailure');
    const url = new URL(location.href); url.searchParams.delete('orcid'); history.replaceState(null, '', url);
  }
  const panel = document.createElement('section'); panel.className = 'panel'; panel.id='authors-section';
  const heading = document.createElement('div'); heading.className = 'section-heading';
  const title = document.createElement('h2'); uiI18n.set(title, 'creators.heading');
  const edit = document.createElement('button'); edit.type = 'button'; edit.className = 'secondary';
  uiI18n.set(edit, 'creators.edit'); edit.hidden = true; heading.append(title, edit);
  const listing = document.createElement('div'); listing.className = 'creator-identity-list';
  listing.setAttribute('aria-live', 'polite'); panel.append(heading, listing);
  document.querySelector('#history-panel').before(panel);

  const dialog = document.createElement('dialog'); dialog.className = 'modal creator-identity-modal';
  dialog.setAttribute('aria-labelledby', 'creator-modal-title');
  const form = document.createElement('form');
  const dialogTitle = document.createElement('h2'); dialogTitle.id = 'creator-modal-title'; uiI18n.set(dialogTitle, 'creators.modal');
  const hint = document.createElement('p'); uiI18n.set(hint, 'creators.hint');
  const rows = document.createElement('div'); rows.className = 'creator-identity-editor';
  const actions = document.createElement('footer'); actions.className = 'button-row';
  const cancel = document.createElement('button'); cancel.type = 'button'; cancel.className = 'secondary';
  uiI18n.set(cancel, 'creators.cancel'); cancel.onclick = () => dialog.close();
  const save = document.createElement('button'); save.type = 'submit'; save.className = 'primary';
  uiI18n.set(save, 'creators.save'); actions.append(cancel, save);
  const header = document.createElement('header'); header.append(dialogTitle);
  const body = document.createElement('div'); body.className = 'modal-body'; body.append(hint, rows);
  form.append(header, body, actions); dialog.append(form); document.body.append(dialog);
  let current = [];

  function labelled(key, value, placeholder, name) {
    const wrapper = document.createElement('label'); const label = document.createElement('span'); uiI18n.set(label, key); wrapper.append(label);
    const input = document.createElement('input'); input.value = value || ''; input.maxLength = 255;
    input.placeholder = placeholder || ''; input.name = name; wrapper.append(input);
    return [wrapper, input];
  }
  function addAffiliation(container, data = {}) {
    if (container.childElementCount >= 10) return;
    const row = document.createElement('div'); row.className = 'creator-affiliation-row';
    const [nameLabel, name] = labelled('creators.institution', data.institution, '', 'institution'); name.required = true;
    const [rorLabel, ror] = labelled('creators.ror', data.ror, 'https://ror.org/...', 'ror');
    const lookup = document.createElement('button'); lookup.type = 'button'; lookup.className = 'secondary';
    uiI18n.set(lookup, 'creators.lookup');
    const suggestions = document.createElement('div'); suggestions.className = 'ror-suggestions';
    suggestions.setAttribute('aria-live', 'polite');
    lookup.onclick = async () => {
      uiI18n.plain(suggestions, ''); suggestions.replaceChildren();
      if (name.value.trim().length < 2) {
        uiI18n.set(suggestions, 'creators.minQuery'); return;
      }
      lookup.disabled = true; uiI18n.set(suggestions, 'creators.loading');
      try {
        const response = await fetch(`/api/v1/scientific/ror/search?q=${encodeURIComponent(name.value.trim())}`,
          {headers: auth.headers()});
        if (!response.ok) throw uiI18n.error('creators.rorFailure');
        const matches = await response.json(); uiI18n.plain(suggestions, ''); suggestions.replaceChildren();
        if (!matches.length) uiI18n.set(suggestions, 'creators.empty');
        for (const item of matches) {
          const option = document.createElement('button'); option.type = 'button'; option.className = 'ror-option';
          option.textContent = `${item.name} · ${item.ror}`;
          option.onclick = () => { name.value = item.name; ror.value = item.ror; uiI18n.plain(suggestions, ''); suggestions.replaceChildren(); };
          suggestions.append(option);
        }
      } catch (error) { uiI18n.showError(suggestions, error); } finally { lookup.disabled = false; }
    };
    const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'danger-outline';
    uiI18n.set(remove, 'creators.remove'); remove.onclick = () => row.remove();
    row.append(nameLabel, lookup, suggestions, rorLabel, remove); container.append(row);
  }
  edit.onclick = () => {
    rows.replaceChildren();
    for (const author of current) {
      const row = document.createElement('fieldset'); row.className = 'creator-identity-row';
      row.dataset.creatorId = author.creatorId;
      const legend = document.createElement('legend');
      const authorName = [author.givenName, author.familyName].filter(Boolean).join(' ');
      if (authorName) legend.textContent = authorName; else uiI18n.set(legend, 'creators.author', {id: author.creatorId});
      const [orcidLabel] = labelled('creators.orcid', author.orcid, '0000-0000-0000-0000', 'orcid');
      const affiliations = document.createElement('div'); affiliations.className = 'creator-affiliations';
      const known = Array.isArray(author.affiliations) ? author.affiliations :
        (author.institution ? [{institution: author.institution, ror: author.ror}] : []);
      known.forEach(item => addAffiliation(affiliations, item));
      const add = document.createElement('button'); add.type = 'button'; add.className = 'secondary';
      uiI18n.set(add, 'creators.add'); add.onclick = () => addAffiliation(affiliations);
      row.append(legend, orcidLabel, affiliations, add); rows.append(row);
    }
    dialog.showModal();
  };
  form.addEventListener('submit', async event => {
    event.preventDefault(); save.disabled = true;
    const values = [...rows.children].map(row => ({
      creatorId: Number(row.dataset.creatorId), orcid: row.querySelector('[name="orcid"]').value.trim(),
      affiliations: [...row.querySelectorAll('.creator-affiliation-row')].map(affiliation => ({
        institution: affiliation.querySelector('[name="institution"]').value.trim(),
        ror: affiliation.querySelector('[name="ror"]').value.trim()
      }))
    }));
    try {
      const response = await fetch(`/api/v1/scientific/${encodeURIComponent(id)}/creators`, {
        method: 'PUT', headers: auth.headers({'Content-Type': 'application/json'}), body: JSON.stringify(values)
      });
      if (!response.ok) { const error = await response.json().catch(() => ({}));
        throw error.detail || error.message ? new Error(error.detail || error.message) : uiI18n.error('creators.saveFailure'); }
      dialog.close(); toast.successKey('creators.success'); await load();
    } catch (error) { toast.errorObject(error); } finally { save.disabled = false; }
  });
  function externalLink(prefix, identifier, label) {
    const link = document.createElement('a'); link.href = `${prefix}${identifier.replace(/^https:\/\/[^/]+\//, '')}`;
    link.textContent = label; link.rel = 'noopener noreferrer'; return link;
  }
  async function load() {
    try {
      const [response, scienceResponse, mineResponse, orcidResponse] = await Promise.all([
        fetch(`/api/v1/scientific/${encodeURIComponent(id)}/creators`, {headers: auth.headers()}),
        fetch(`/api/v1/scientific/${encodeURIComponent(id)}`, {headers: auth.headers()}),
        fetch('/api/v1/my-dataresources', {headers: auth.headers()}),
        fetch('/api/v1/scientific/orcid/status', {headers: auth.headers()})
      ]);
      if (!response.ok || !scienceResponse.ok || !mineResponse.ok) { panel.hidden = true; return; }
      current = await response.json(); panel.hidden = false; listing.replaceChildren();
      const science = await scienceResponse.json(), mine = await mineResponse.json();
      const orcidAvailable = orcidResponse.ok && (await orcidResponse.json()).enabled;
      const canEdit = science.status === 'DRAFT' && Array.isArray(mine) && mine.some(item => item.id === id);
      for (const author of current) {
        const row = document.createElement('p'); row.className = 'author-identity';
        const name = document.createElement('strong');
        const authorName = [author.givenName, author.familyName].filter(Boolean).join(' ');
        if (authorName) name.textContent = authorName; else uiI18n.set(name, 'creators.unnamed');
        row.append(name);
        if (author.orcid) {
          row.append(' · ', externalLink('https://orcid.org/', author.orcid, `ORCID ${author.orcid}`));
          const label = document.createElement('small');
          uiI18n.set(label, author.orcidAuthenticated ? 'creators.controlled' : 'creators.declared');
          row.append(label);
        }
        for (const affiliation of author.affiliations || []) {
          row.append(` · ${affiliation.institution}`);
          if (affiliation.ror) row.append(' (', externalLink('https://ror.org/', affiliation.ror, 'ROR'), ')');
        }
        if (canEdit && orcidAvailable) {
          const verify = document.createElement('button'); verify.type = 'button'; verify.className = 'secondary';
          uiI18n.set(verify, 'creators.verify');
          uiI18n.attribute(verify, 'aria-label', 'creators.verifyHint');
          verify.onclick = async () => {
            verify.disabled = true;
            try {
              const request = await fetch(`/api/v1/scientific/${encodeURIComponent(id)}/creators/${author.creatorId}/orcid/start`,
                {method: 'POST', headers: auth.headers()});
              if (!request.ok) throw uiI18n.error('creators.startFailure');
              const payload = await request.json(); location.assign(payload.authorizeUrl);
            } catch (failure) { toast.errorObject(failure); verify.disabled = false; }
          };
          row.append(' ', verify);
        }
        listing.append(row);
      }
      edit.hidden = !canEdit;
    } catch { panel.hidden = true; }
  }
  load();
})();
