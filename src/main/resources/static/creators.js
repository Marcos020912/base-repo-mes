(() => {
  const id = new URLSearchParams(location.search).get('id');
  if (!id) return;
  const callbackResult = new URLSearchParams(location.search).get('orcid');
  if (callbackResult) {
    if (callbackResult === 'authenticated') toast.success('Cuenta ORCID autenticada y asociada al autor seleccionado.');
    else toast.error('No se pudo completar la autenticación ORCID. Inténtalo de nuevo.');
    const url = new URL(location.href); url.searchParams.delete('orcid'); history.replaceState(null, '', url);
  }
  const panel = document.createElement('section'); panel.className = 'panel';
  const heading = document.createElement('div'); heading.className = 'section-heading';
  const title = document.createElement('h2'); title.textContent = 'Autores e instituciones';
  const edit = document.createElement('button'); edit.type = 'button'; edit.className = 'secondary';
  edit.textContent = 'Editar identidades'; edit.hidden = true; heading.append(title, edit);
  const listing = document.createElement('div'); listing.className = 'creator-identity-list';
  listing.setAttribute('aria-live', 'polite'); panel.append(heading, listing);
  document.querySelector('#history-panel').before(panel);

  const dialog = document.createElement('dialog'); dialog.className = 'modal';
  const form = document.createElement('form');
  const dialogTitle = document.createElement('h2'); dialogTitle.textContent = 'Identidad de autores';
  const hint = document.createElement('p'); hint.textContent = 'Asigna ORCID y hasta diez instituciones con ROR a cada autor. Un ORCID escrito manualmente no está autenticado; guarda primero y luego usa el botón ORCID junto al autor que eres tú.';
  const rows = document.createElement('div'); rows.className = 'creator-identity-editor';
  const actions = document.createElement('div'); actions.className = 'button-row';
  const cancel = document.createElement('button'); cancel.type = 'button'; cancel.className = 'secondary';
  cancel.textContent = 'Cancelar'; cancel.onclick = () => dialog.close();
  const save = document.createElement('button'); save.type = 'submit'; save.className = 'primary';
  save.textContent = 'Guardar identidades'; actions.append(cancel, save);
  form.append(dialogTitle, hint, rows, actions); dialog.append(form); document.body.append(dialog);
  let current = [];

  function labelled(label, value, placeholder, name) {
    const wrapper = document.createElement('label'); wrapper.textContent = label;
    const input = document.createElement('input'); input.value = value || ''; input.maxLength = 255;
    input.placeholder = placeholder || ''; input.name = name; wrapper.append(input);
    return [wrapper, input];
  }
  function addAffiliation(container, data = {}) {
    if (container.childElementCount >= 10) return;
    const row = document.createElement('div'); row.className = 'creator-affiliation-row';
    const [nameLabel, name] = labelled('Institución', data.institution, '', 'institution'); name.required = true;
    const [rorLabel, ror] = labelled('ROR institucional', data.ror, 'https://ror.org/...', 'ror');
    const lookup = document.createElement('button'); lookup.type = 'button'; lookup.className = 'secondary';
    lookup.textContent = 'Buscar en ROR';
    const suggestions = document.createElement('div'); suggestions.className = 'ror-suggestions';
    suggestions.setAttribute('aria-live', 'polite');
    lookup.onclick = async () => {
      suggestions.replaceChildren();
      if (name.value.trim().length < 2) {
        suggestions.textContent = 'Escribe al menos dos caracteres del nombre institucional.'; return;
      }
      lookup.disabled = true; suggestions.textContent = 'Consultando ROR…';
      try {
        const response = await fetch(`/api/v1/scientific/ror/search?q=${encodeURIComponent(name.value.trim())}`,
          {headers: auth.headers()});
        if (!response.ok) throw new Error('ROR no está disponible. Puedes introducir el identificador manualmente.');
        const matches = await response.json(); suggestions.replaceChildren();
        if (!matches.length) suggestions.textContent = 'No se encontraron instituciones.';
        for (const item of matches) {
          const option = document.createElement('button'); option.type = 'button'; option.className = 'ror-option';
          option.textContent = `${item.name} · ${item.ror}`;
          option.onclick = () => { name.value = item.name; ror.value = item.ror; suggestions.replaceChildren(); };
          suggestions.append(option);
        }
      } catch (error) { suggestions.textContent = error.message; } finally { lookup.disabled = false; }
    };
    const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'danger-outline';
    remove.textContent = 'Quitar institución'; remove.onclick = () => row.remove();
    row.append(nameLabel, lookup, suggestions, rorLabel, remove); container.append(row);
  }
  edit.onclick = () => {
    rows.replaceChildren();
    for (const author of current) {
      const row = document.createElement('fieldset'); row.className = 'creator-identity-row';
      row.dataset.creatorId = author.creatorId;
      const legend = document.createElement('legend');
      legend.textContent = [author.givenName, author.familyName].filter(Boolean).join(' ') || `Autor ${author.creatorId}`;
      const [orcidLabel] = labelled('ORCID', author.orcid, '0000-0000-0000-0000', 'orcid');
      const affiliations = document.createElement('div'); affiliations.className = 'creator-affiliations';
      const known = Array.isArray(author.affiliations) ? author.affiliations :
        (author.institution ? [{institution: author.institution, ror: author.ror}] : []);
      known.forEach(item => addAffiliation(affiliations, item));
      const add = document.createElement('button'); add.type = 'button'; add.className = 'secondary';
      add.textContent = '+ Añadir institución'; add.onclick = () => addAffiliation(affiliations);
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
        throw new Error(error.detail || error.message || 'No se pudieron guardar las identidades.'); }
      dialog.close(); toast.success('Identidades de autores actualizadas.'); await load();
    } catch (error) { toast.error(error.message); } finally { save.disabled = false; }
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
        name.textContent = [author.givenName, author.familyName].filter(Boolean).join(' ') || 'Autor sin nombre';
        row.append(name);
        if (author.orcid) {
          row.append(' · ', externalLink('https://orcid.org/', author.orcid, `ORCID ${author.orcid}`));
          const label = document.createElement('small');
          label.textContent = author.orcidAuthenticated ? ' · ORCID autenticado por el depositante (autoría no contrastada)' : ' · ORCID declarado, sin autenticar';
          row.append(label);
        }
        for (const affiliation of author.affiliations || []) {
          row.append(` · ${affiliation.institution}`);
          if (affiliation.ror) row.append(' (', externalLink('https://ror.org/', affiliation.ror, 'ROR'), ')');
        }
        if (canEdit && orcidAvailable) {
          const verify = document.createElement('button'); verify.type = 'button'; verify.className = 'secondary';
          verify.textContent = 'Autenticar mi ORCID para este autor';
          verify.title = 'Úsalo solo si este autor eres tú; autentica el control de la cuenta ORCID, no comprueba el nombre.';
          verify.onclick = async () => {
            verify.disabled = true;
            try {
              const request = await fetch(`/api/v1/scientific/${encodeURIComponent(id)}/creators/${author.creatorId}/orcid/start`,
                {method: 'POST', headers: auth.headers()});
              if (!request.ok) throw new Error('No se pudo iniciar ORCID. Comprueba que el depósito siga en borrador.');
              const payload = await request.json(); location.assign(payload.authorizeUrl);
            } catch (failure) { toast.error(failure.message); verify.disabled = false; }
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
