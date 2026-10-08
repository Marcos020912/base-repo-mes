/* Public immutable-version lineage. No editorial audit notes or account identities. */
(() => {
  const queryId = new URLSearchParams(location.search).get('id');
  const resourceId = queryId || decodeURIComponent(location.pathname.match(/^\/datasets\/([^/]+)$/)?.[1] || '');
  const status = document.querySelector('#versions-status');
  const list = document.querySelector('#versions-list');
  const pagination = document.querySelector('#versions-pagination');
  let page = 0, request = 0;
  function raw(tag, value = '') { const node = document.createElement(tag); node.textContent = value; return node; }
  function label(tag, key, params = {}) { const node = raw(tag); uiI18n.set(node, key, params); return node; }
  function render(body) {
    list.replaceChildren(); pagination.replaceChildren();
    if (body.newerPublicationAvailable && body.latestPublishedId) {
      const links = document.querySelector('#version-links');
      links.querySelectorAll('.legacy-version-warning,#family-version-warning').forEach(node => node.remove());
      const warning = raw('p'); warning.id = 'family-version-warning'; warning.className = 'version-warning';
      const newer = label('a', 'versions.latest'); newer.href = '/datasets/' + encodeURIComponent(body.latestPublishedId);
      warning.append(label('span', 'versions.newer'), raw('span', ' '), newer); links.append(warning);
    }
    for (const version of body.items) {
      const item = raw('li');
      const link = version.version
        ? label('a', version.current ? 'versions.currentNamed' : 'versions.named', { version: version.version })
        : label('a', version.current ? 'versions.currentUnnamed' : 'versions.unnamed');
      link.href = '/datasets/' + encodeURIComponent(version.id);
      if (version.current) link.setAttribute('aria-current', 'page');
      const metadata = raw('p');
      metadata.append(label('span', version.status === 'WITHDRAWN' ? 'versions.withdrawn' : 'versions.published'), raw('span', ' · '),
        version.publishedAt ? raw('span', version.publishedAt) : label('span', 'versions.noDate'), raw('span', ' · DOI: '),
        version.doi ? raw('span', version.doi) : label('span', 'versions.noDoi'));
      item.append(link, metadata); list.append(item);
    }
    uiI18n.set(status, body.total ? 'versions.total' : 'versions.empty', { total: body.total, page: body.page + 1, pages: body.pages });
    if (body.pages > 1) {
      const previous = label('button', 'versions.previous'); previous.className = 'secondary'; previous.disabled = page === 0;
      previous.onclick = () => { page--; load(); };
      const next = label('button', 'versions.next'); next.className = 'secondary'; next.disabled = page + 1 >= body.pages;
      next.onclick = () => { page++; load(); }; pagination.append(previous, next);
    }
  }
  async function load() {
    const current = ++request; uiI18n.set(status, 'versions.loading');
    try {
      const response = await fetch(`/api/v1/public/resources/${encodeURIComponent(resourceId)}/versions?page=${page}&size=20`);
      if (!response.ok) throw Error('Unavailable');
      const body = await response.json(); if (current !== request) return;
      if (!Array.isArray(body.items)) throw Error('Invalid response');
      render(body);
    } catch (error) {
      if (current !== request) return;
      uiI18n.set(status, 'versions.failure'); list.replaceChildren(); pagination.replaceChildren();
      const retry = label('button', 'versions.retry'); retry.className = 'secondary'; retry.onclick = load; pagination.append(retry);
    }
  }
  if (resourceId) load();
})();
