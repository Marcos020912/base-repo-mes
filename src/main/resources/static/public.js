const state = { page: 0, pages: 0 };
const q = document.querySelector('#public-q');
const type = document.querySelector('#public-type');
const discipline = document.querySelector('#public-discipline'); const institution = document.querySelector('#public-institution'); const language = document.querySelector('#public-language'); const format = document.querySelector('#public-format'); const access = document.querySelector('#public-access'); const sort = document.querySelector('#public-sort');
const results = document.querySelector('#public-results');
const pagination = document.querySelector('#public-pagination');
const status = document.querySelector('#public-status');
const facetPanel = document.createElement('aside');
facetPanel.className = 'public-facet-panel';
facetPanel.setAttribute('aria-label', 'Facetas del catálogo');
const facetLayout = document.createElement('div');
facetLayout.className = 'public-facet-layout';
const facetResults = document.createElement('div');
results.before(facetLayout);
facetResults.append(results, pagination);
facetLayout.append(facetPanel, facetResults);
let yearFilter = '', licenseFilter = '', doiFilter = '';
let pendingRequest = 0;
const params = new URLSearchParams(location.search);
q.value = params.get('q') || ''; type.value = params.get('type') || '';
for (const [key, control] of [['discipline',discipline],['institution',institution],['language',language],['format',format],['access',access],['sort',sort]]) if(params.has(key)) control.value=params.get(key);
state.page = Math.max(0, Number.parseInt(params.get('page') || '0', 10) || 0);
yearFilter = params.get('year') || ''; licenseFilter = params.get('license') || '';
doiFilter = params.get('hasDoi') === 'true' ? 'true' : params.get('withoutDoi') === 'true' ? 'false' : '';
function text(tag, value, className) { const node = document.createElement(tag); node.textContent = value; if (className) node.className = className; return node; }
const facetNames = {type:'Tipo',access:'Acceso',year:'Año',license:'Licencia',discipline:'Disciplina',institution:'Institución',language:'Idioma',hasDoi:'DOI'};
const facetLabels = {OPEN:'Abierto',RESTRICTED:'Restringido',EMBARGOED:'Embargo',true:'Con DOI',false:'Sin DOI'};
function selectedFacet(name) {
  return ({type:type.value,access:access.value,year:yearFilter,license:licenseFilter,
    discipline:discipline.value,institution:institution.value,language:language.value,
    hasDoi:doiFilter})[name];
}
function chooseFacet(name, value) {
  const next = selectedFacet(name) === value ? '' : value;
  if (name === 'type') type.value = next;
  else if (name === 'access') access.value = next;
  else if (name === 'year') yearFilter = next;
  else if (name === 'license') licenseFilter = next;
  else if (name === 'discipline') discipline.value = next;
  else if (name === 'institution') institution.value = next;
  else if (name === 'language') language.value = next;
  else if (name === 'hasDoi') doiFilter = next;
  state.page = 0; load();
}
function renderFacets(data) {
  facetPanel.replaceChildren(text('h2','Filtrar resultados'));
  for (const [name, label] of Object.entries(facetNames)) {
    const values = data[name] || []; if (!values.length) continue;
    const group = document.createElement('section'); group.className = 'public-facet-group';
    group.append(text('h3', label));
    for (const option of values) {
      const button = text('button', `${facetLabels[option.value] || option.value} (${option.count})`, 'public-facet-option');
      button.type = 'button'; button.setAttribute('aria-pressed', selectedFacet(name) === option.value ? 'true' : 'false');
      button.addEventListener('click', () => chooseFacet(name, option.value)); group.append(button);
    }
    facetPanel.append(group);
  }
}
async function load() {
  const requestId = ++pendingRequest;
  const search = new URLSearchParams({ q: q.value.trim(), type: type.value, year:yearFilter, license:licenseFilter,
    discipline: discipline.value.trim(), institution: institution.value.trim(), language: language.value.trim(),
    format: format.value.trim(), access: access.value, hasDoi:String(doiFilter === 'true'),
    withoutDoi:String(doiFilter === 'false'), sort: sort.value,
    page: String(state.page), size: '20' });
  history.replaceState(null, '', `${location.pathname}?${search}`);
  status.textContent = 'Buscando…';
  try {
    const response = await fetch(`/api/v1/public/catalog?${search}`);
    if (response.status === 403) throw new Error('No tienes permiso para consultar este catálogo.');
    if (!response.ok) throw new Error('El servicio de búsqueda no está disponible en este momento.');
    const data = await response.json(); if (requestId !== pendingRequest) return;
    state.page = data.page; state.pages = data.pages;
    results.replaceChildren(); pagination.replaceChildren();
    if (!data.items.length) {
      const filtered = [q.value, type.value, yearFilter, licenseFilter, discipline.value, institution.value,
        language.value, format.value, access.value].some(Boolean) || doiFilter;
      const empty = document.createElement('div'); empty.className = 'panel empty';
      empty.append(text('p', filtered ? 'No hay resultados para estos filtros.' : 'Todavía no existen datasets publicados.'));
      if (filtered) { const clear = text('button', 'Limpiar filtros', 'secondary'); clear.type = 'button';
        clear.onclick = () => { q.value=''; type.value=''; discipline.value=''; institution.value=''; language.value=''; format.value=''; access.value=''; yearFilter=''; licenseFilter=''; doiFilter=''; state.page=0; load(); };
        empty.append(clear); }
      results.append(empty);
    }
    for (const item of data.items) {
      const link = document.createElement('a'); link.className = 'resource-card'; link.href = `/datasets/${encodeURIComponent(item.id)}`;
      link.append(text('span', item.type || 'RECURSO', 'resource-type'), text('h2', item.title || 'Sin título'),
        text('p', item.authors?.join(', ') || 'Autoría no informada', 'card-author'),
        text('p', `${item.publisher || 'Institución no informada'} · ${item.year || 'Año no informado'}`),
        text('small', /^10\.\d{4,9}\/\S+$/.test(item.identifier || '') ? `DOI: ${item.identifier}` : 'DOI en la ficha'),
        text('small', ({OPEN:'Acceso abierto',RESTRICTED:'Acceso restringido',EMBARGOED:'Bajo embargo'})[item.accessLevel] || 'Acceso no informado'));
      results.append(link);
    }
    if (state.pages > 1) {
      const prev = text('button', '← Anterior', 'secondary'); prev.disabled = state.page === 0; prev.onclick = () => { state.page--; load(); };
      const next = text('button', 'Siguiente →', 'secondary'); next.disabled = state.page + 1 >= state.pages; next.onclick = () => { state.page++; load(); };
      pagination.append(prev, text('span', `Página ${state.page + 1} de ${state.pages}`), next);
    }
    status.textContent = `${data.total} recursos publicados`;
    try {
      const facetSearch = new URLSearchParams(search); facetSearch.delete('sort'); facetSearch.delete('page'); facetSearch.delete('size');
      const facetResponse = await fetch(`/api/v1/public/catalog/facets?${facetSearch}`);
      if (facetResponse.ok) { const facets = await facetResponse.json(); if (requestId === pendingRequest) renderFacets(facets); }
      else if (requestId === pendingRequest) facetPanel.textContent = 'No se pudieron cargar las facetas.';
    } catch { if (requestId === pendingRequest) facetPanel.textContent = 'No se pudieron cargar las facetas.'; }
  } catch (error) { if (requestId !== pendingRequest) return; status.textContent = error.message; results.replaceChildren(); pagination.replaceChildren(); facetPanel.replaceChildren(); }
}
let timer; q.addEventListener('input', () => { state.page = 0; clearTimeout(timer); timer = setTimeout(load, 300); });
for(const control of [discipline,institution,language,format]) control.addEventListener('input', () => { state.page=0; clearTimeout(timer); timer=setTimeout(load,300); });
for(const control of [type,access,sort]) control.addEventListener('change', () => { state.page=0; load(); }); load();
