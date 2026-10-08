const status = document.querySelector('#review-status');
const token = new URLSearchParams(location.hash.slice(1)).get('token');
// Remove the bearer secret from browser history; never persist it in UI preferences.
history.replaceState(null, '', location.pathname);
const headers = token ? {'X-Review-Token': token, Accept: 'application/json'} : {};
function node(tag, value = '', className) {
  const element = document.createElement(tag); element.textContent = value;
  if (className) element.className = className; return element;
}
function label(tag, key, params = {}, className) {
  const element = node(tag, '', className); uiI18n.set(element, key, params); return element;
}
async function request(path) {
  const response = await fetch(path, {headers, cache: 'no-store'});
  if (!response.ok) throw uiI18n.error('reviewAccess.invalid');
  return response.json();
}
let filesRequest = 0;
async function files(page = 0) {
  const current = ++filesRequest;
  const list = document.querySelector('#review-files'), pager = document.querySelector('#review-pagination');
  try {
    const data = await request(`/api/v1/reviewer/files?page=${page}`);
    if (current !== filesRequest) return;
    list.replaceChildren(); pager.replaceChildren();
    if (!data.files.length) list.append(label('p', 'reviewAccess.empty'));
    for (const file of data.files) {
      const row = node('div', '', 'file-row');
      const info = node('span'); info.append(node('strong', file.path));
      const size = node('small'); size.append(node('span', `${file.size} bytes · `),
        file.mediaType ? node('span', file.mediaType) : label('span', 'reviewAccess.file'));
      info.append(size);
      if (file.sha256) info.append(label('small', 'reviewAccess.checksum', {checksum: file.sha256}));
      const button = label('button', 'reviewAccess.download', {}, 'secondary'); button.type = 'button';
      button.onclick = async () => {
        button.disabled = true;
        try {
          const result = await transfers.download(`/api/v1/reviewer/file?path=${encodeURIComponent(file.path)}`,
            file.path.split('/').pop(), file.path, {headers, cache: 'no-store', errorMessage: uiI18n.t('reviewAccess.downloadFailure')});
          uiI18n.set(status, result?.savedToDisk ? 'reviewAccess.saved' : 'reviewAccess.prepared');
        } catch (error) {
          if (error.name === 'AbortError') uiI18n.set(status, 'reviewAccess.cancelled');
          else uiI18n.showError(status, error);
        } finally { button.disabled = false; }
      };
      row.append(info, button); list.append(row);
    }
    if (data.pages > 1) {
      const previous = label('button', 'reviewAccess.previous', {}, 'secondary'); previous.type = 'button'; previous.disabled = page === 0; previous.onclick = () => files(page - 1);
      const next = label('button', 'reviewAccess.next', {}, 'secondary'); next.type = 'button'; next.disabled = page + 1 >= data.pages; next.onclick = () => files(page + 1);
      pager.append(previous, label('span', 'reviewAccess.page', {page: page + 1, pages: data.pages}), next);
    }
    uiI18n.plain(status, '');
  } catch (error) {
    if (current !== filesRequest) return;
    list.replaceChildren(); pager.replaceChildren(); uiI18n.showError(status, error);
  }
}
async function load() {
  if (!token) { uiI18n.set(status, 'reviewAccess.missing'); return; }
  try {
    const data = await request('/api/v1/reviewer/metadata');
    document.querySelector('#review-title').textContent = data.title;
    const byline = document.querySelector('#review-byline');
    byline.append(data.authors?.length ? node('span', data.authors.join(', ')) : label('span', 'reviewAccess.noAuthors'), node('span', ' · '),
      data.publisher ? node('span', data.publisher) : label('span', 'reviewAccess.noInstitution'), node('span', ' · '),
      data.year ? node('span', data.year) : label('span', 'reviewAccess.noYear'));
    document.querySelector('#review-markdown').textContent = data.markdown;
    await files(); document.querySelector('#review-content').hidden = false;
  } catch (error) { uiI18n.showError(status, error); }
}
load();
