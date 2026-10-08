const auth = {
  token: () => localStorage.getItem('base-repo-token'),
  user: () => { try { return JSON.parse(localStorage.getItem('base-repo-user')); } catch { return null; } },
  requireLogin: () => { if (!localStorage.getItem('base-repo-token')) location.replace('login.html'); },
  logout: () => { localStorage.removeItem('base-repo-token'); localStorage.removeItem('base-repo-user'); location.replace('login.html'); },
  headers: (headers = {}) => ({ ...headers, ...(auth.token() ? { Authorization: `Bearer ${auth.token()}` } : {}) })
};

const toast = {
  show(kind, text) {
    let container = document.querySelector('#toast-container');
    if (!container) { container = document.createElement('div'); container.id = 'toast-container'; container.setAttribute('aria-live', 'polite'); document.body.append(container); }
    const item = document.createElement('div'); item.className = `toast ${kind}`;
    const icon = document.createElement('span'); icon.className = 'toast-icon'; icon.textContent = kind === 'success' ? '✓' : '×';
    const label = document.createElement('span'); label.textContent = String(text);
    item.append(icon, label);
    container.append(item); setTimeout(() => item.remove(), 5500);
  },
  success: (text) => toast.show('success', text),
  error: (text) => toast.show('error', text)
};

document.querySelectorAll('.brand').forEach((brand) => {
  const logo = document.createElement('img');
  logo.src = 'logo%20mes.png';
  logo.alt = '50 MES · Ministerio de Educación Superior';
  const name = document.createElement('span');
  name.textContent = 'Datos RedUniv';
  brand.replaceChildren(logo, name);
});
const signedInUser = auth.user();
document.querySelectorAll('.sidebar nav').forEach((nav) => {
  if (!nav.querySelector('a[href="account.html"]')) {
    const link = document.createElement('a');
    link.href = 'account.html'; link.textContent = '◉ Mi cuenta';
    nav.insertBefore(link, nav.querySelector('#users-nav'));
  }
});
if (['CURATOR', 'ADMINISTRATOR'].includes(signedInUser?.role)) {
  document.querySelectorAll('.sidebar nav').forEach((nav) => {
    if (!nav.querySelector('a[href="reviews.html"]')) {
      const link = document.createElement('a');
      link.href = 'reviews.html';
      link.textContent = '✓ Curación';
      nav.insertBefore(link, nav.querySelector('#users-nav'));
    }
  });
}

if (['CURATOR','ADMINISTRATOR'].includes(signedInUser?.role)) {
  document.querySelectorAll('.sidebar nav').forEach(nav=>{
    const link=document.createElement('a');link.href='collections.html?manage=true';link.textContent='Colecciones';nav.append(link);
  });
}

if (['CURATOR','ADMINISTRATOR'].includes(signedInUser?.role)) {
  document.querySelectorAll('.sidebar nav').forEach(nav=>{if(!nav.querySelector('a[href="operations.html"]')){const link=document.createElement('a');link.href='operations.html';link.textContent='Estado operativo';nav.append(link);}});
}
