const status = document.querySelector('#review-status');
const token = new URLSearchParams(location.hash.slice(1)).get('token');
history.replaceState(null, '', location.pathname);
const headers = token ? {'X-Review-Token':token, Accept:'application/json'} : {};
function node(tag, value, className) { const element = document.createElement(tag); element.textContent = value; if (className) element.className = className; return element; }
async function request(path, download = false) {
  const response = await fetch(path, {headers, cache:'no-store'});
  if (!response.ok) throw new Error('El enlace no es válido, venció, fue revocado o la revisión terminó.');
  return download ? response.blob() : response.json();
}
async function files(page = 0) {
  const data = await request(`/api/v1/reviewer/files?page=${page}`);
  const list = document.querySelector('#review-files'); list.replaceChildren();
  if (!data.files.length) list.append(node('p','No hay archivos en esta página.'));
  for (const file of data.files) {
    const row = document.createElement('div'); row.className = 'file-row';
    const info = document.createElement('span'); info.append(node('strong', file.path), node('small', `${file.size} bytes · ${file.mediaType || 'archivo'}`));
    if (file.sha256) info.append(node('small', `SHA-256 al ingreso: ${file.sha256}`));
    const button = node('button','Descargar','secondary'); button.type = 'button';
    button.onclick = async () => {
      button.disabled = true;
      try {
        const blob = await request(`/api/v1/reviewer/file?path=${encodeURIComponent(file.path)}`, true);
        const url = URL.createObjectURL(blob); const link = document.createElement('a');
        link.href = url; link.download = file.path.split('/').pop(); link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
      } catch (error) { status.textContent = error.message; } finally { button.disabled = false; }
    };
    row.append(info, button); list.append(row);
  }
  const pager = document.querySelector('#review-pagination'); pager.replaceChildren();
  if (data.pages > 1) {
    const previous = node('button','← Anterior','secondary'); previous.type = 'button'; previous.disabled = page === 0; previous.onclick = () => files(page - 1);
    const next = node('button','Siguiente →','secondary'); next.type = 'button'; next.disabled = page + 1 >= data.pages; next.onclick = () => files(page + 1);
    pager.append(previous, node('span', `Página ${page + 1} de ${data.pages}`), next);
  }
}
async function load() {
  if (!token) { status.textContent = 'Falta el enlace de revisión.'; return; }
  try {
    const data = await request('/api/v1/reviewer/metadata');
    document.querySelector('#review-title').textContent = data.title;
    document.querySelector('#review-byline').textContent = `${data.authors?.join(', ') || 'Autoría no informada'} · ${data.publisher || 'Institución no informada'} · ${data.year || 'Año no informado'}`;
    document.querySelector('#review-markdown').textContent = data.markdown;
    await files(); document.querySelector('#review-content').hidden = false; status.textContent = '';
  } catch (error) { status.textContent = error.message; }
}
load();
