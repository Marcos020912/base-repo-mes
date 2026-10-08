const id = new URLSearchParams(location.search).get('id') || (location.pathname.startsWith('/datasets/') ? decodeURIComponent(location.pathname.slice('/datasets/'.length)) : null);
const status = document.querySelector('#status');
let detail;
function canDownloadContent() {
  return detail && (detail.accessLevel === 'OPEN' ||
    (detail.accessLevel === 'EMBARGOED' && detail.embargoUntil && Date.now() >= new Date(detail.embargoUntil).getTime()));
}
function node(tag, value, className) { const item = document.createElement(tag); item.textContent = value; if (className) item.className = className; return item; }
function field(key, value) { if (!value) return; const list = document.querySelector('#identity'); const content = value instanceof Node ? value : node('dd', value); list.append(uiNode('dt', key), content); }
function doiField(key, value) {
  if (!value) return;
  const list = document.querySelector('#identity'); const description = uiNode('dt', key); const detail = document.createElement('dd');
  if (/^10\.\d{4,9}\/\S+$/.test(value)) {
    const link = document.createElement('a'); link.href = `https://doi.org/${value.split('/').map(encodeURIComponent).join('/')}`; link.textContent = value;
    link.rel = 'noopener noreferrer'; detail.append(link);
  } else detail.textContent = value;
  list.append(description, detail);
}

function uiNode(tag, key, params = {}, className) { const item = node(tag, '', className); uiI18n.set(item, key, params); return item; }
const dateBindings = new Map();
function dateNode(tag, value, dateOnly = false) {
  const item = node(tag, ''); dateBindings.set(item, {value, dateOnly}); formatDate(item, value, dateOnly); return item;
}
function formatDate(item, value, dateOnly) {
  const date = new Date(value); item.textContent = Number.isNaN(date.getTime()) ? String(value) :
    (dateOnly ? date.toLocaleDateString(uiI18n.locale) : date.toLocaleString(uiI18n.locale));
}
window.addEventListener('ui-locale-changed', () => {
  for (const [item, data] of dateBindings) { if (!item.isConnected) dateBindings.delete(item); else formatDate(item, data.value, data.dateOnly); }
});

function renderMarkdown(source) {
  const root = document.querySelector('#description'); root.replaceChildren();
  for (const line of source.split(/\r?\n/)) {
    if (!line.trim()) continue;
    const image = line.match(/^!\[([^\]]*)\]\(([^)]+)\)$/);
    if (image && !/^(?:[a-z]+:|\/|\.\.)/i.test(image[2])) {
      const img = document.createElement('img'); img.alt = image[1]; img.loading = 'lazy';
      img.src = `/api/v1/public/resources/${encodeURIComponent(id)}/file?path=${encodeURIComponent(image[2])}&inline=true`;
      root.append(img); continue;
    }
    const match = line.match(/^(#{1,3})\s+(.+)$/);
    if (match) root.append(node(`h${match[1].length}`, match[2]));
    else if (/^[-*]\s+/.test(line)) root.append(node('p', `• ${line.slice(2)}`));
    else root.append(node('p', line));
  }
}
function renderRelations(relations) {
  if (!relations?.length) return;
  const section = document.createElement('section'); section.className = 'panel';
  section.append(uiNode('h2', 'publicRecord.related', {}));
  for (const relation of relations) {
    const row = document.createElement('div'); row.className = 'related-resource';
    const label = node('p', `${relation.kind} · ${relation.relationType}`);
    const link = document.createElement('a');
    link.href = relation.identifierType === 'DOI' ? `https://doi.org/${relation.identifier.split('/').map(encodeURIComponent).join('/')}` : relation.identifier;
    link.rel = 'noopener noreferrer'; link.textContent = relation.title || relation.identifier;
    row.append(label, link); section.append(row);
  }
  document.querySelector('#landing').append(section);
}
function renderAuthors(authors) {
  if (!authors?.length) return;
  const section = document.createElement('section'); section.className = 'panel';
  section.append(uiNode('h2', 'publicRecord.authors', {}));
  for (const author of authors) {
    const row = document.createElement('p'); row.className = 'author-identity';
    const authorName = [author.givenName, author.familyName].filter(Boolean).join(' ');
    row.append(authorName ? node('strong', authorName) : uiNode('strong', 'publicRecord.unnamed'));
    if (author.orcid) {
      const link = document.createElement('a'); link.href = `https://orcid.org/${author.orcid.replace(/^https:\/\/orcid\.org\//, '')}`;
      link.textContent = `ORCID ${author.orcid}`; link.rel = 'noopener noreferrer'; row.append(' · ', link);
      const status = document.createElement('small');
      uiI18n.set(status, author.orcidAuthenticated ? 'publicRecord.controlled' : 'publicRecord.declared');
      row.append(status);
    }
    const affiliations = Array.isArray(author.affiliations) ? author.affiliations :
      (author.institution ? [{institution: author.institution, ror: author.ror}] : []);
    for (const affiliation of affiliations) {
      row.append(node('span', ` · ${affiliation.institution}`));
      if (affiliation.ror) {
        const link = document.createElement('a'); link.href = `https://ror.org/${affiliation.ror.replace(/^https:\/\/ror\.org\//, '')}`;
        link.textContent = `ROR ${affiliation.ror}`; link.rel = 'noopener noreferrer'; row.append(' · ', link);
      }
    }
    section.append(row);
  }
  document.querySelector('#description').closest('.panel').before(section);
}
function renderFunding(funding) {
  if (!funding?.length) return;
  const section = document.createElement('section'); section.className = 'panel';
  section.append(uiNode('h2', 'publicRecord.funding', {}));
  for (const item of funding) {
    const row = document.createElement('p'); row.className = 'related-resource';
    row.append(node('strong', item.funderName));
    if (item.funderRor) {
      const link = document.createElement('a'); link.href = `https://ror.org/${item.funderRor.replace(/^https:\/\/ror\.org\//, '')}`;
      uiI18n.set(link, 'publicRecord.ror'); link.rel = 'noopener noreferrer'; row.append(' · ', link);
    }
    if (item.awardTitle) row.append(uiNode('span', 'publicRecord.awardTitle', {title: item.awardTitle}));
    if (item.awardNumber) row.append(uiNode('span', 'publicRecord.awardNumber', {number: item.awardNumber}));
    section.append(row);
  }
  document.querySelector('#description').closest('.panel').before(section);
}
async function loadFiles(page = 0) {
  const response = await fetch(`/api/v1/public/resources/${encodeURIComponent(id)}/files?page=${page}&size=50`);
  if (!response.ok) throw uiI18n.error('publicRecord.filesFailure');
  const data = await response.json(); const list = document.querySelector('#files'); list.replaceChildren();
  if (!data.files.length) list.append(uiNode('p', 'publicRecord.emptyFiles', {}, 'empty'));
  for (const file of data.files) {
    const row = document.createElement('div'); row.className = 'file-row';
    const info = document.createElement('span'); info.append(node('strong', file.path)); const media = node('small', ''); media.append(node('span', `${file.size} bytes · `), file.mediaType ? node('span', file.mediaType) : uiNode('span', 'publicRecord.file')); info.append(media);
    if (file.sha256) { const checksum = node('small', `SHA-256: ${file.sha256}`); uiI18n.attribute(checksum, 'aria-label', 'publicRecord.checksumHint'); info.append(checksum); }
    const fixityKeys = {MATCH:'publicRecord.fixityMATCH',MISMATCH:'publicRecord.fixityMISMATCH',MISSING_FILE:'publicRecord.fixityMISSING_FILE',NO_BASELINE:'publicRecord.fixityNO_BASELINE',UNSUPPORTED_URI:'publicRecord.fixityUNSUPPORTED_URI',READ_ERROR:'publicRecord.fixityREAD_ERROR'};
    if (file.fixityStatus) {
      const verified = node('small', ''); verified.append(Object.hasOwn(fixityKeys, file.fixityStatus) ? uiNode('span', fixityKeys[file.fixityStatus]) : node('span', file.fixityStatus)); if (file.fixityCheckedAt) verified.append(node('span', ' · '), dateNode('span', file.fixityCheckedAt));
      verified.className = file.fixityStatus === 'MATCH' ? 'quality-ok' : 'quality-missing'; info.append(verified);
    } else info.append(uiNode('small', 'publicRecord.noFixity'));
    const link = document.createElement('a'); link.className = 'secondary'; uiI18n.set(link, 'publicRecord.download'); link.href = `/api/v1/public/resources/${encodeURIComponent(id)}/file?path=${encodeURIComponent(file.path)}`;
    link.addEventListener('click', event => { event.preventDefault(); downloadPublic(link.href, file.path); });
    row.append(info, canDownloadContent() ? link : uiNode('span', 'publicRecord.restricted', {}, 'muted')); list.append(row);
  }
  const pager = document.querySelector('#files-pagination'); pager.replaceChildren();
  if (data.pages > 1) {
    const prev = uiNode('button', 'publicRecord.previousPage', {}, 'secondary'); prev.disabled = page === 0; prev.onclick = () => paginateFiles(page - 1);
    const next = uiNode('button', 'publicRecord.nextPage', {}, 'secondary'); next.disabled = page + 1 >= data.pages; next.onclick = () => paginateFiles(page + 1);
    pager.append(prev, uiNode('span', 'publicRecord.page', {page: page + 1, pages: data.pages}), next);
  }
}
async function paginateFiles(page) {
  try { await loadFiles(page); uiI18n.plain(status, ''); } catch(error) { document.querySelector('#files').replaceChildren(); document.querySelector('#files-pagination').replaceChildren(); uiI18n.showError(status,error); }
}
async function load() {
  if (!id) { uiI18n.set(status, 'publicRecord.missing'); return; }
  try {
    const response = await fetch(`/api/v1/public/resources/${encodeURIComponent(id)}`);
    const data = await response.json();
    if (response.status === 410) { if (data.title) uiI18n.plain(document.querySelector('#title'), data.title); else uiI18n.set(document.querySelector('#title'), 'publicRecord.withdrawnTitle'); uiI18n.set(status, data.reason ? 'publicRecord.withdrawn' : 'publicRecord.withdrawnNoReason', {reason: data.reason}); return; }
    if (!response.ok) throw uiI18n.error('publicRecord.unavailable');
    detail = data; document.querySelector('#landing').hidden = false;
    const archive = document.querySelector('#download-archive');
    archive.hidden = !canDownloadContent();
    archive.onclick = event => { event.preventDefault(); downloadPublic(archive.href, id + '.zip'); };
    if (!archive.hidden) archive.href = `/api/v1/public/resources/${encodeURIComponent(id)}/archive`;
    uiI18n.plain(document.querySelector('#title'), data.title);
    const subtitle = document.querySelector('#subtitle'); subtitle.replaceChildren(); subtitle.append(data.authors?.length ? node('span', data.authors.join(', ')) : uiNode('span', 'publicRecord.noAuthors'), node('span', ' · '), data.year ? node('span', data.year) : uiNode('span', 'publicRecord.noYear'));
    uiI18n.set(document.querySelector('#version'), data.version ? 'publicRecord.version' : 'publicRecord.noVersion', {version: data.version});
    doiField('publicRecord.doi', data.doi); doiField('publicRecord.conceptualDoi', data.conceptualDoi); field('publicRecord.license', data.license);
    const accessKey = {OPEN:'publicRecord.open',RESTRICTED:'publicRecord.accessRestricted',EMBARGOED:'publicRecord.embargoed'}[data.accessLevel]; field('publicRecord.access', accessKey ? uiNode('dd', accessKey) : data.accessLevel);
    field('publicRecord.embargoEnd', data.embargoUntil ? dateNode('dd', data.embargoUntil) : null);
    field('publicRecord.institution', data.institution);
    if (!data.authorIdentities?.some(item => item.orcid)) field('publicRecord.orcid', data.orcid);
    if (!data.authorIdentities?.some(item => item.ror)) field('publicRecord.ror', data.ror);
    field('publicRecord.language', data.language); field('publicRecord.discipline', data.discipline); field('publicRecord.keywords', data.keywords);
    field('publicRecord.summary',data.summary);field('publicRecord.geographic',data.geographicCoverage);
    if(data.temporalStart||data.temporalEnd){const coverage=node('dd','');coverage.append(data.temporalStart?node('span',data.temporalStart):uiNode('span','publicRecord.noStart'),node('span',' / '),data.temporalEnd?node('span',data.temporalEnd):uiNode('span','publicRecord.noEnd'));field('publicRecord.temporal',coverage);}
    renderTranslations(data.translations);
    const provenance=document.querySelector('#provenance-fields');provenance.replaceChildren();
    for(const [key,label]of [['productionDescription','publicRecord.production'],['processingDescription','publicRecord.processing'],['processingTools','publicRecord.tools']])if(data[key])provenance.append(uiNode('dt',label),node('dd',data[key]));
    document.querySelector('#scientific-provenance').hidden=!provenance.children.length;
    field('publicRecord.methods', data.methodology); field('publicRecord.publications', data.relatedPublications);
    field('publicRecord.updated', data.lastUpdate ? dateNode('dd', data.lastUpdate) : null);
    field('publicRecord.published', data.publishedAt ? dateNode('dd', data.publishedAt, true) : null);
    if (data.previousResourceId) {
      const previous = document.createElement('a'); previous.href = `/datasets/${encodeURIComponent(data.previousResourceId)}`;
      uiI18n.set(previous, 'publicRecord.previousVersion'); document.querySelector('#version-links').append(previous);
    }
    if (data.newerVersionId && !document.querySelector('#family-version-warning')) {
      const warning = document.createElement('p'); warning.className = 'version-warning legacy-version-warning';
      warning.append(uiNode('span', 'publicRecord.older'), node('span', ' '));
      const newer = document.createElement('a'); newer.href = `/datasets/${encodeURIComponent(data.newerVersionId)}`;
      uiI18n.set(newer, 'publicRecord.nextVersion'); warning.append(newer);
      document.querySelector('#version-links').append(warning);
    }
    renderAuthors(data.authorIdentities); renderFunding(data.funding); renderMarkdown(data.markdown || ''); renderRelations(data.relations); await loadFiles(); uiI18n.plain(status, '');
    const citation = `${data.authors?.join(', ') || 'Autor no informado'} (${data.year || 's. f.'}). ${data.title} (versión ${data.version}) [${data.type || 'Recurso'}]. ${data.publisher || 'Editorial no informada'}. https://doi.org/${data.doi}`;
    document.querySelector('#citation').textContent = citation;
    document.querySelector('#copy-citation').onclick = async () => { try { await navigator.clipboard.writeText(citation); uiI18n.set(status, 'publicRecord.copiedCitation'); } catch { uiI18n.set(status, 'publicRecord.copyFailure'); } };
  } catch (error) { uiI18n.showError(status, error); }
}
document.querySelector('#export').addEventListener('click', () => {
  if (!detail) return;
  const format = document.querySelector('#format').value;
  const extension = ({bibtex:'bib', ris:'ris', 'csl-json':'json'})[format] || 'txt';
  downloadPublic(`/api/v1/scientific/${encodeURIComponent(id)}/citation?format=${encodeURIComponent(format)}`, `citation.${extension}`);
});
async function downloadPublic(url, filename) {
  try {
    const result = await transfers.download(url, filename);
    uiI18n.set(status, result.savedToDisk ? 'publicRecord.saved' : 'publicRecord.prepared');
  }
  catch (error) { if(error.name === 'AbortError') uiI18n.set(status, 'publicRecord.cancelled'); else uiI18n.showError(status, error); }
}
load();

document.querySelector('#share-resource').addEventListener('click',async()=>{
  if(!detail)return;
  const url=new URL(`/datasets/${encodeURIComponent(detail.id)}`,location.origin).href;
  try {await navigator.clipboard.writeText(url);uiI18n.set(status, 'publicRecord.shared');}
  catch {uiI18n.plain(status, ''); status.replaceChildren(uiNode('p','publicRecord.shareFailure'));const link=node('a',url);link.href=url;status.append(link);}
});
document.querySelector('#export-metadata').addEventListener('click',()=>{
  if(!detail)return;
  const payload={schema:'reduniv.public-metadata.v1',exportedAt:new Date().toISOString(),
    landingPage:new URL(`/datasets/${encodeURIComponent(detail.id)}`,location.origin).href,metadata:detail};
  const url=URL.createObjectURL(new Blob([JSON.stringify(payload,null,2)],{type:'application/json;charset=utf-8'}));
  const link=document.createElement('a');link.href=url;link.download=`metadata-${detail.id}.json`;link.click();
  setTimeout(()=>URL.revokeObjectURL(url),1000);uiI18n.set(status, 'publicRecord.exported');
});

function renderTranslations(values) {
  const entries=Object.entries(values||{});if(!entries.length)return;
  const section=node('section','','panel');section.append(uiNode('h2','publicRecord.translations'));
  const label=node('label','');label.append(uiNode('span','publicRecord.translationLanguage'));const select=document.createElement('select');
  for(const [language] of entries){const option=node('option',language);option.value=language;select.append(option);}
  label.append(select);const translated=document.createElement('div');translated.setAttribute('aria-live','polite');
  function show(){const value=values[select.value];translated.replaceChildren();translated.lang=select.value;
    if(value.title)translated.append(node('h3',value.title));if(value.summary)translated.append(node('p',value.summary));}
  select.onchange=show;section.append(label,translated);document.querySelector('#landing').append(section);show();
}
