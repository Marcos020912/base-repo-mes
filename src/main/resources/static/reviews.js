const user = auth.user();
if (!['CURATOR', 'ADMINISTRATOR'].includes(user?.role)) location.replace('index.html');
document.querySelector('#current-user').textContent = user?.username || '';
if (user?.role === 'ADMINISTRATOR') document.querySelector('#users-nav').hidden = false;
document.querySelectorAll('[data-logout]').forEach(button => button.addEventListener('click', auth.logout));
const list = document.querySelector('#reviews-list');
const preservation = document.querySelector('#preservation-summary');
async function request(path, options = {}) {
  const response = await fetch(path, { ...options, headers: auth.headers({ Accept: 'application/json', ...options.headers }) });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw data.detail || data.message ? new Error(data.detail || data.message) : uiI18n.error('reviews.failure');
  return data;
}
function action(label, className, handler) {
  const button = document.createElement('button');
  button.type = 'button'; button.className = className; uiI18n.set(button, label);
  button.addEventListener('click', handler); return button;
}
function uiNode(tag,key,params={}){const n=document.createElement(tag);uiI18n.set(n,key,params);return n;}
async function preview(id, container, page = 0) {
  uiI18n.plain(container,'');uiI18n.set(container,'reviews.previewLoading');
  try {
    const [metadata, listing, quality] = await Promise.all([
      request(`/api/v1/scientific/review/${encodeURIComponent(id)}`),
      request(`/api/v1/scientific/review/${encodeURIComponent(id)}/files?page=${page}`),
      request(`/api/v1/scientific/${encodeURIComponent(id)}/quality`)
    ]);
    const privacy=await request(`/api/v1/scientific/${encodeURIComponent(id)}/privacy`);
    uiI18n.plain(container,'');container.replaceChildren();
    const heading = document.createElement('h3'); heading.textContent = metadata.title;
    const byline = document.createElement('p'); byline.append(metadata.authors.length?document.createTextNode(metadata.authors.join(', ')):uiNode('span','reviews.noAuthors'),document.createTextNode(' · '),metadata.publisher?document.createTextNode(metadata.publisher):uiNode('span','reviews.noInstitution'),document.createTextNode(' · '),metadata.year?document.createTextNode(metadata.year):uiNode('span','reviews.noYear'));
    const blockers = quality.blockers || [];
    const validation = document.createElement('p'); validation.append(uiNode('span','reviews.quality',{percent:quality.completionPercent}),document.createTextNode(' '),blockers.length?uiNode('span','reviews.blockers',{blockers:blockers.join(', ')}):uiNode('span','reviews.complete'));
    const privacyInfo=document.createElement('p');uiI18n.set(privacyInfo,privacy.classification?'reviews.privacy':'reviews.privacyMissing',{classification:privacy.classification,state:privacy.reviewState,note:privacy.assessmentNote||''});
    const privacyActions=document.createElement('div');privacyActions.append(privacyInfo);
    if(privacy.classification)privacyActions.append(action('reviews.privacyReview','secondary',()=>scientificPrivacy.openReview(id,privacy)));
    const descriptionHeading = document.createElement('h4'); uiI18n.set(descriptionHeading,'reviews.description');
    const description = document.createElement('pre'); description.className = 'review-description'; description.textContent = metadata.markdown;
    const filesHeading = document.createElement('h4'); uiI18n.set(filesHeading,'reviews.files');
    const files = document.createElement('ul'); files.className = 'review-files';
    for (const file of listing.files) {
      const row = document.createElement('li');
      const label = document.createElement('span'); label.textContent = `${file.path} · ${file.size} bytes${file.sha256 ? ` · SHA-256 ${file.sha256}` : ''}`;
      const download = action('reviews.download', 'secondary', async () => {
        try {
          await transfers.download(`/api/v1/scientific/review/${encodeURIComponent(id)}/file?path=${encodeURIComponent(file.path)}`, file.path.split('/').pop(), file.path);
        } catch (error) { toast.errorObject(error); }
      });
      const verify = action('reviews.verify', 'secondary', async () => {
        try {
          const result = await request(`/api/v1/scientific/${encodeURIComponent(id)}/fixity?path=${encodeURIComponent(file.path)}`, { method: 'POST' });
          toast[result.status === 'MATCH' ? 'successKey' : 'errorKey']('reviews.fixity',{path:file.path,status:result.status});
        } catch (error) { toast.errorObject(error); }
      });
      row.append(label, download, verify); files.append(row);
    }
    if (!listing.files.length) { const empty = document.createElement('li'); uiI18n.set(empty,'reviews.emptyFiles'); files.append(empty); }
    const pagination = document.createElement('div'); pagination.className = 'pagination';
    if (listing.pages > 1) {
      const previous = action('reviews.previous', 'secondary', () => preview(id, container, page - 1)); previous.disabled = page === 0;
      const next = action('reviews.next', 'secondary', () => preview(id, container, page + 1)); next.disabled = page + 1 >= listing.pages;
      const label = document.createElement('span'); uiI18n.set(label,'reviews.page',{page:page+1,pages:listing.pages});
      pagination.append(previous, label, next);
    }
    const packageButton = action('reviews.package', 'secondary', async () => {
      try {
        await transfers.download(`/api/v1/scientific/preservation/${encodeURIComponent(id)}/package`, `preservation-${id}.zip`, uiI18n.t('reviews.packageName'));
      } catch (error) { toast.errorObject(error); }
    });
    const historyButton = action('reviews.history', 'secondary', async () => {
      try {
        const history = await request(`/api/v1/scientific/${encodeURIComponent(id)}/file-history?size=20`);
        const lines = (history.content || []).map(event => `${new Date(event.occurredAt).toLocaleString()} · ${event.actor} · ${event.action} · ${event.relativePath}`);
        if(lines.length)uiI18n.plain(historyView,lines.join('\n'));else uiI18n.set(historyView,'reviews.emptyHistory');
        historyView.hidden = false;
      } catch (error) { toast.errorObject(error); }
    });
    const historyView = document.createElement('pre'); historyView.className = 'review-description'; historyView.hidden = true;
    const shareHeading = document.createElement('h4'); uiI18n.set(shareHeading,'reviews.external');
    const shareNote = document.createElement('p'); uiI18n.set(shareNote,'reviews.shareHint');
    const shareActions = document.createElement('div'); shareActions.className = 'button-row';
    const shareResult = document.createElement('p'); shareResult.className = 'review-share-result';
    const linksView = document.createElement('div'); linksView.className = 'review-links';
    const showLinks = async () => {
      const links = await request(`/api/v1/scientific/${encodeURIComponent(id)}/review-links`);
      linksView.replaceChildren();
      if (!links.length) linksView.append(uiNode('span','reviews.emptyLinks'));
      for (const link of links) {
        const line = document.createElement('p');
        const state = link.revokedAt ? 'reviews.revoked' : new Date(link.expiresAt).getTime() <= Date.now() ? 'reviews.expired' : 'reviews.active';
        line.append(uiNode('span','reviews.link',{id:link.id}),document.createTextNode(' · '),uiNode('span',state),document.createTextNode(' · '),uiNode('span','reviews.expires',{date:link.expiresAt}));
        if (state === 'reviews.active') line.append(action('reviews.revoke', 'danger-outline', async () => {
          try { await request(`/api/v1/scientific/${encodeURIComponent(id)}/review-links/${link.id}`, {method:'DELETE'});
            toast.successKey('reviews.revokedSuccess'); await showLinks(); } catch (error) { toast.errorObject(error); }
        }));
        linksView.append(line);
      }
    };
    shareActions.append(action('reviews.createLink', 'secondary', async () => {
      try {
        const created = await request(`/api/v1/scientific/${encodeURIComponent(id)}/review-links`, {
          method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({hours:48})
        });
        const url = new URL(created.relativeUrl, location.origin).href;
        shareResult.replaceChildren(uiNode('span','reviews.created'),document.createTextNode(' '));
        const input = document.createElement('input'); input.readOnly = true; input.value = url; uiI18n.attribute(input,'aria-label','reviews.privateLink');
        const copy = action('reviews.copy', 'secondary', async () => { try { await navigator.clipboard.writeText(url); toast.successKey('reviews.copied'); } catch { input.select(); toast.errorKey('reviews.manualCopy'); } });
        shareResult.append(input, copy); await showLinks();
      } catch (error) { toast.errorObject(error); }
    }), action('reviews.viewLinks', 'secondary', async () => { try { await showLinks(); } catch (error) { toast.errorObject(error); } }));
    container.append(heading, byline, validation, privacyActions, descriptionHeading, description, filesHeading, files, pagination,
      packageButton, historyButton, historyView, shareHeading, shareNote, shareActions, shareResult, linksView);
  } catch (error) { uiI18n.plain(container,''); toast.errorObject(error); }
}
async function load() {
  uiI18n.plain(list,'');uiI18n.set(list,'reviews.loading');
  try {
    const doiEnabled = (await request('/api/v1/scientific/doi/config')).enabled;
    const records = await request('/api/v1/scientific/reviews');
    uiI18n.plain(list,'');list.replaceChildren();
    if (!records.length) { const empty = document.createElement('p'); empty.className = 'panel empty'; uiI18n.set(empty,'reviews.empty'); list.append(empty); return; }
    for (const record of records) {
      const card = document.createElement('article'); card.className = 'panel review-card';
      const heading = document.createElement('h2'); heading.textContent = record.resourceId;
      const info = document.createElement('p'); uiI18n.set(info,'reviews.info',{version:record.versionLabel||'—',doi:record.versionDoi||'—',license:record.licenseId||'—'});
      const controls = document.createElement('div'); controls.className = 'button-row';
      const detail = document.createElement('section'); detail.className = 'review-preview'; detail.hidden = true;
      controls.append(action('reviews.review', 'secondary', async () => {
        detail.hidden = !detail.hidden;
        if (!detail.hidden) await preview(record.resourceId, detail);
      }), action('reviews.return', 'secondary', async () => {
        if (!confirm(uiI18n.t('reviews.returnConfirm'))) return;
        try { await request(`/api/v1/scientific/${encodeURIComponent(record.resourceId)}/return-to-draft`, { method: 'POST' }); toast.successKey('reviews.returned'); await load(); }
        catch (error) { toast.errorObject(error); }
      }), action('reviews.publish', 'primary', async () => {
        if (!doiEnabled && !record.versionDoi) { toast.errorKey('reviews.doiRequired'); return; }
        if (!confirm(uiI18n.t(doiEnabled?'reviews.publishDatacite':'reviews.publishExternal'))) return;
        try { if(doiEnabled)await request(`/api/v1/scientific/${encodeURIComponent(record.resourceId)}/doi/publish`, { method: 'POST' });else await request(`/api/v1/scientific/${encodeURIComponent(record.resourceId)}/publish`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ doiRegisteredExternally: true }) }); toast.successKey('reviews.published'); await load(); }
        catch (error) { toast.errorObject(error); }
      }));
      card.append(heading, info, controls, detail); list.append(card);
    }
  } catch (error) { uiI18n.plain(list,''); toast.errorObject(error); }
}
async function loadPreservation() {
  try {
    const metrics = await request('/api/v1/scientific/preservation/metrics');
    const latest = metrics.latestAudit?.id?uiNode('span','reviews.latestAudit',{...metrics.latestAudit,message:metrics.latestAudit.message||''}):uiNode('span','reviews.noAudits');
    uiI18n.plain(preservation,'');preservation.append(uiNode('span','reviews.metrics',{published:metrics.published,review:metrics.inReview,files:metrics.files,checked:metrics.filesChecked,mismatched:metrics.mismatched,missing:metrics.missing}),document.createTextNode(' '),latest);
  } catch (error) { uiI18n.plain(preservation,'');uiI18n.set(preservation,'reviews.metricsFailure'); }
}
document.querySelector('#start-audit').addEventListener('click', async () => {
  if (!confirm(uiI18n.t('reviews.auditConfirm'))) return;
  try {
    await request('/api/v1/scientific/preservation/audits', { method: 'POST' });
    toast.successKey('reviews.auditStarted');
    await loadPreservation();
  } catch (error) { toast.errorObject(error); }
});
document.querySelector('#refresh-reviews').addEventListener('click', load);
load();
loadPreservation();
