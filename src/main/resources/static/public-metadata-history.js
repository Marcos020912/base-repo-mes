/* Compare released public projections, not private editorial events or file contents. */
(() => {
  const currentId = new URLSearchParams(location.search).get('id') || decodeURIComponent(location.pathname.match(/^\/datasets\/([^/]+)$/)?.[1] || '');
  const anchor = document.querySelector('#public-versions'); if (!anchor || !currentId) return;
  let sequence = 0; const publishedVersions = new Set();
  function raw(tag, value = '') { const node = document.createElement(tag); node.textContent = value; if(tag==='pre')node.className='review-description'; return node; }
  function ui(tag, key, params = {}) { const node = raw(tag); uiI18n.set(node, key, params); return node; }
  const section = raw('section'); section.className = 'panel'; section.hidden = true;
  section.append(ui('h2', 'metadataHistory.heading'), ui('p', 'metadataHistory.scope'));
  const status = raw('p'); status.setAttribute('role', 'status');
  const changes = raw('div'); changes.className = 'metadata-differences'; section.append(status, changes); anchor.after(section);
  const fields = {
    title:'resourceDynamic.title',authors:'publicRecord.authors',publisher:'resourceDynamic.publisher',year:'resourceDynamic.year',type:'resourceDynamic.type',
    version:'wizard.version',doi:'publicRecord.doi',conceptualDoi:'publicRecord.conceptualDoi',license:'publicRecord.license',institution:'publicRecord.institution',
    orcid:'publicRecord.orcid',ror:'publicRecord.ror',language:'publicRecord.language',discipline:'publicRecord.discipline',keywords:'publicRecord.keywords',
    relatedPublications:'publicRecord.publications',methodology:'publicRecord.methods',accessLevel:'publicRecord.access',embargoUntil:'publicRecord.embargoEnd',
    summary:'publicRecord.summary',temporalStart:'resourceDynamic.start',temporalEnd:'resourceDynamic.end',geographicCoverage:'publicRecord.geographic',
    translations:'publicRecord.translations',productionDescription:'publicRecord.production',processingDescription:'publicRecord.processing',processingTools:'publicRecord.tools',
    markdown:'publicRecord.description',relations:'publicRecord.related',authorIdentities:'publicRecord.authors',funding:'publicRecord.funding'
  };
  function canonical(value) {
    if (Array.isArray(value)) return value.map(canonical);
    if (value && typeof value === 'object') return Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])]));
    return value ?? null;
  }
  function select(value, keys) { return Object.fromEntries(keys.filter(key => Object.hasOwn(value, key)).map(key => [key, value[key]])); }
  function project(data) {
    const values = Object.fromEntries(Object.keys(fields).map(key => [key, data[key] ?? null]));
    values.relations = (data.relations || []).map(value => select(value, ['kind','identifierType','identifier','title','relationType']));
    values.funding = (data.funding || []).map(value => select(value, ['funderName','funderRor','awardNumber','awardTitle']));
    values.authorIdentities = (data.authorIdentities || []).map(value => ({...select(value, ['givenName','familyName','orcid','institution','ror','orcidAuthenticated']),
      affiliations:(value.affiliations || []).map(item => select(item, ['institution','ror']))}));
    return canonical(values);
  }
  function display(value) { return typeof value === 'string' ? value : JSON.stringify(value, null, 2); }
  async function metadata(id) {
    const response = await fetch(`/api/v1/public/resources/${encodeURIComponent(id)}`, {cache:'no-store'});
    if (!response.ok) throw uiI18n.error('metadataHistory.unavailable');
    return project(await response.json());
  }
  async function compare(id) {
    const current = ++sequence; section.hidden = false; changes.replaceChildren(); uiI18n.set(status, 'metadataHistory.loading');
    try {
      if(!publishedVersions.has(id) || id===currentId)throw uiI18n.error('metadataHistory.unavailable');
      const [other, selected] = await Promise.all([metadata(id), metadata(currentId)]); if (current !== sequence) return;
      const links = raw('p'); links.append(ui('span','metadataHistory.compared'),raw('span',' '));
      const earlier = raw('a', id); earlier.href = '/datasets/' + encodeURIComponent(id);
      const later = raw('a', currentId); later.href = '/datasets/' + encodeURIComponent(currentId);
      links.append(earlier, raw('span',' → '), later); changes.append(links);
      let count = 0;
      for (const [key, label] of Object.entries(fields)) {
        if (JSON.stringify(other[key]) === JSON.stringify(selected[key])) continue;
        count++; const item = raw('section'); item.className = 'metadata-difference'; item.append(ui('h3',label));
        item.append(ui('h4','metadataHistory.other'),raw('pre',display(other[key])),ui('h4','metadataHistory.current'),raw('pre',display(selected[key]))); changes.append(item);
      }
      uiI18n.set(status,count ? 'metadataHistory.total' : 'metadataHistory.identical',{count});
    } catch (error) { if (current !== sequence) return; changes.replaceChildren(); uiI18n.showError(status,error); }
  }
  window.publicMetadataHistory = Object.freeze({compare, registerVersions(versions){for(const version of versions)if(version.status==='PUBLISHED')publishedVersions.add(version.id);}});
})();
