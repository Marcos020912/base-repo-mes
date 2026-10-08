const auth = {
  token: () => localStorage.getItem('base-repo-token'),
  user: () => { try { return JSON.parse(localStorage.getItem('base-repo-user')); } catch { return null; } },
  requireLogin: () => { if (!localStorage.getItem('base-repo-token')) location.replace('login.html'); },
  logout: () => { localStorage.removeItem('base-repo-token'); localStorage.removeItem('base-repo-user'); location.replace('login.html'); },
  headers: (headers = {}) => ({ ...headers, ...(auth.token() ? { Authorization: `Bearer ${auth.token()}` } : {}) })
};

const toast = {
  show(kind, text, key) {
    let container = document.querySelector('#toast-container');
    if (!container) { container = document.createElement('div'); container.id = 'toast-container'; container.setAttribute('aria-live', 'polite'); document.body.append(container); }
    const item = document.createElement('div'); item.className = `toast ${kind}`;
    const icon = document.createElement('span'); icon.className = 'toast-icon'; icon.textContent = kind === 'success' ? '✓' : '×';
    const label = document.createElement('span'); if(key)uiI18n.set(label,key);else label.textContent = String(text);
    item.append(icon, label);
    container.append(item); setTimeout(() => item.remove(), 5500);
  },
  success: (text) => toast.show('success', text),
  error: (text) => toast.show('error', text),
  successKey: key=>toast.show('success',null,key),
  errorKey: key=>toast.show('error',null,key),
  errorObject: error=>error.i18nKey?toast.errorKey(error.i18nKey):toast.error(error.message)
};

document.querySelectorAll('.brand').forEach((brand) => {
  const logo = document.createElement('img');
  logo.src = 'logo%20mes.png';
  logo.lang=uiI18n.locale;logo.dataset.i18nAlt='brand.alt';logo.alt=uiI18n.t('brand.alt');
  const name = document.createElement('span');
  name.textContent = 'Datos RedUniv';
  brand.replaceChildren(logo, name);
});
const signedInUser = auth.user();
document.querySelectorAll('.sidebar nav').forEach((nav) => {
  if (!nav.querySelector('a[href="account.html"]')) {
    const link = document.createElement('a');
    link.href = 'account.html'; uiI18n.set(link,'nav.account');
    nav.insertBefore(link, nav.querySelector('#users-nav'));
  }
});
if (['CURATOR', 'ADMINISTRATOR'].includes(signedInUser?.role)) {
  document.querySelectorAll('.sidebar nav').forEach((nav) => {
    if (!nav.querySelector('a[href="reviews.html"]')) {
      const link = document.createElement('a');
      link.href = 'reviews.html';
      uiI18n.set(link,'nav.reviews');
      nav.insertBefore(link, nav.querySelector('#users-nav'));
    }
  });
}

if (['CURATOR','ADMINISTRATOR'].includes(signedInUser?.role)) {
  document.querySelectorAll('.sidebar nav').forEach(nav=>{
    const link=document.createElement('a');link.href='collections.html?manage=true';uiI18n.set(link,'nav.collections');nav.append(link);
  });
}

if (['CURATOR','ADMINISTRATOR'].includes(signedInUser?.role)) {
  document.querySelectorAll('.sidebar nav').forEach(nav=>{if(!nav.querySelector('a[href="operations.html"]')){const link=document.createElement('a');link.href='operations.html';uiI18n.set(link,'nav.operations');nav.append(link);}});
}

if(auth.user()?.role==='ADMINISTRATOR')document.querySelectorAll('.sidebar nav').forEach(nav=>{if(!nav.querySelector('a[href="vocabulary-admin.html"]')){const link=document.createElement('a');link.href='vocabulary-admin.html';uiI18n.set(link,'nav.vocabularies');nav.append(link);}});

if(auth.user()?.role==='ADMINISTRATOR')document.querySelectorAll('.sidebar nav').forEach(nav=>{if(!nav.querySelector('a[href="metadata-profiles-admin.html"]')){const link=document.createElement('a');link.href='metadata-profiles-admin.html';uiI18n.set(link,'nav.profiles');nav.append(link);}});

// Keys are selected by stable navigation routes, never by visible text or user data.
const sharedNavKeys={'index.html':'nav.catalog','my-datasets.html':'nav.mine','account.html':'nav.account','users.html':'nav.users','reviews.html':'nav.reviews','collections.html?manage=true':'nav.collections','operations.html':'nav.operations','vocabulary-admin.html':'nav.vocabularies','metadata-profiles-admin.html':'nav.profiles'};
document.querySelectorAll('.sidebar nav').forEach(nav=>{nav.lang=uiI18n.locale;nav.dataset.i18nAriaLabel='nav.main';nav.setAttribute('aria-label',uiI18n.t('nav.main'));nav.querySelectorAll('a').forEach(link=>{const key=sharedNavKeys[link.getAttribute('href')];if(key)uiI18n.set(link,key);});});
document.querySelectorAll('[data-logout]').forEach(button=>uiI18n.set(button,'nav.logout'));
window.addEventListener('ui-locale-changed',()=>document.querySelectorAll('.sidebar nav').forEach(nav=>nav.setAttribute('aria-label',uiI18n.t('nav.main'))));
