const state = { page: 0, pages: 0 };
const q = document.querySelector('#public-q');
const type = document.querySelector('#public-type');
const results = document.querySelector('#public-results');
const pagination = document.querySelector('#public-pagination');
const status = document.querySelector('#public-status');
const params = new URLSearchParams(location.search);
q.value = params.get('q') || ''; type.value = params.get('type') || '';
state.page = Math.max(0, Number.parseInt(params.get('page') || '0', 10) || 0);
function text(tag, value, className) { const node = document.createElement(tag); node.textContent = value; if (className) node.className = className; return node; }
async function load() {
  const search = new URLSearchParams({ q: q.value.trim(), type: type.value, page: String(state.page), size: '20' });
  history.replaceState(null, '', `${location.pathname}?${search}`);
  status.textContent = 'Buscando…';
  try {
    const response = await fetch(`/api/v1/public/catalog?${search}`);
    if (!response.ok) throw new Error('El catálogo no está disponible en este momento.');
    const data = await response.json(); state.page = data.page; state.pages = data.pages;
    results.replaceChildren(); pagination.replaceChildren();
    if (!data.items.length) results.append(text('p', 'No hay recursos publicados que coincidan con estos filtros.', 'panel empty'));
    for (const item of data.items) {
      const link = document.createElement('a'); link.className = 'resource-card'; link.href = `public-resource.html?id=${encodeURIComponent(item.id)}`;
      link.append(text('span', item.type || 'RECURSO', 'resource-type'), text('h2', item.title || 'Sin título'),
        text('p', item.authors?.join(', ') || 'Autoría no informada', 'card-author'),
        text('p', `${item.publisher || 'Institución no informada'} · ${item.year || 'Año no informado'}`),
        text('small', /^10\.\d{4,9}\/\S+$/.test(item.identifier || '') ? `DOI: ${item.identifier}` : 'DOI en la ficha'));
      results.append(link);
    }
    if (state.pages > 1) {
      const prev = text('button', '← Anterior', 'secondary'); prev.disabled = state.page === 0; prev.onclick = () => { state.page--; load(); };
      const next = text('button', 'Siguiente →', 'secondary'); next.disabled = state.page + 1 >= state.pages; next.onclick = () => { state.page++; load(); };
      pagination.append(prev, text('span', `Página ${state.page + 1} de ${state.pages}`), next);
    }
    status.textContent = `${data.total} recursos publicados`;
  } catch (error) { status.textContent = error.message; results.replaceChildren(); pagination.replaceChildren(); }
}
let timer; q.addEventListener('input', () => { state.page = 0; clearTimeout(timer); timer = setTimeout(load, 300); });
type.addEventListener('change', () => { state.page = 0; load(); }); load();
