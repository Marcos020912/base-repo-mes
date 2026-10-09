const auth = {
  token: () => localStorage.getItem('base-repo-token'),
  user: () => { try { return JSON.parse(localStorage.getItem('base-repo-user')); } catch { return null; } },
  requireLogin: () => { if (!localStorage.getItem('base-repo-token')) location.replace('login.html'); },
  logout: () => { localStorage.removeItem('base-repo-token'); localStorage.removeItem('base-repo-user'); location.replace('login.html'); },
  headers: (headers = {}) => ({ ...headers, ...(auth.token() ? { Authorization: `Bearer ${auth.token()}` } : {}) })
};

const toast = {
  show(kind, text, key, params={}) {
    const signature=JSON.stringify([kind,text,key,params]);if(this.last?.signature===signature&&Date.now()-this.last.time<250)return;this.last={signature,time:Date.now()};
    let container = document.querySelector('#toast-container');
    if (!container) { container = document.createElement('div'); container.id = 'toast-container'; container.setAttribute('aria-live', 'polite'); document.body.append(container); }
    const activeDialog=document.querySelector('dialog[open]');
    if(activeDialog){activeDialog.append(container);activeDialog.addEventListener('close',()=>{if(container.parentNode===activeDialog)document.body.append(container);},{once:true});}else if(container.parentNode!==document.body)document.body.append(container);
    const item = document.createElement('div'); item.className = `toast ${kind}`;
    const icon = document.createElement('span'); icon.className = 'toast-icon'; icon.textContent = kind === 'success' ? '✓' : '×';
    const label = document.createElement('span'); if(key)uiI18n.set(label,key,params);else label.textContent = String(text);
    icon.setAttribute('aria-hidden','true');
    label.className='toast-text';
    const close=document.createElement('button');close.type='button';close.className='toast-close';close.textContent='×';uiI18n.attribute(close,'aria-label','notification.close');
    const remove=()=>{clearTimeout(timer);item.remove();};close.addEventListener('click',remove);
    item.setAttribute('role',kind==='error'?'alert':'status');item.append(icon,label,close);
    container.append(item); const timer=setTimeout(remove,10000);
  },
  success: (text) => toast.show('success', text),
  error: (text) => toast.show('error', text),
  successKey: (key,params)=>toast.show('success',null,key,params),
  errorKey: (key,params)=>toast.show('error',null,key,params),
  errorObject: error=>error.i18nKey?toast.errorKey(error.i18nKey,error.i18nParams):toast.error(error.message)
};

// One shared shell and navigation registry for every authenticated section.
const signedInUser = auth.user();
const currentSection = location.pathname.split('/').pop() || 'index.html';
if (signedInUser && auth.token() && ['collections.html','self-assessment.html'].includes(currentSection) && !document.querySelector('.sidebar')) {
  const main = document.querySelector('main');
  const shell = document.createElement('div'); shell.className = 'app-shell';
  const sidebar = document.createElement('aside'); sidebar.className = 'sidebar';
  const brand = document.createElement('a'); brand.className = 'brand'; brand.href = 'index.html';
  const nav = document.createElement('nav');
  const bottom = document.createElement('div'); bottom.className = 'sidebar-bottom';
  const identity = document.createElement('span'); identity.id = 'current-user';
  const logout = document.createElement('button'); logout.type = 'button'; logout.className = 'link-button'; logout.dataset.logout = '';
  bottom.append(identity,logout); sidebar.append(brand,nav,bottom);
  // Preserve the existing selector (and its locale event listener) before removing the public nav.
  const picker = document.querySelector('[data-ui-locale]');
  if (picker) sidebar.insertBefore(picker,nav);
  main.before(shell); main.classList.remove('public-main'); main.classList.add('app-main');
  shell.append(sidebar,main);
  document.querySelector('.public-nav')?.remove();
}
document.querySelectorAll('.brand').forEach(brand => {
  const logo = document.createElement('img'); logo.src = 'logo%20mes.png'; uiI18n.attribute(logo,'alt','brand.alt');
  const name = document.createElement('span'); name.textContent = 'Datos RedUniv'; brand.replaceChildren(logo,name);
});
const editorial = ['CURATOR','ADMINISTRATOR'].includes(signedInUser?.role);
const administrator = signedInUser?.role === 'ADMINISTRATOR';
const navigation = [
  ['index.html','nav.catalog',true], ['my-datasets.html','nav.mine',true],
  ['reviews.html','nav.reviews',editorial], ['users.html','nav.users',administrator],
  [editorial?'collections.html?manage=true':'collections.html','nav.collections',true],
  ['operations.html','nav.operations',editorial], ['self-assessment.html','nav.assessment',editorial],
  ['vocabulary-admin.html','nav.vocabularies',administrator], ['metadata-profiles-admin.html','nav.profiles',administrator]
];
const activeSection = ['resource.html','create.html'].includes(currentSection) ? 'my-datasets.html' : currentSection;
document.querySelectorAll('.sidebar').forEach(sidebar => {
  const nav = sidebar.querySelector('nav'); nav.replaceChildren(); uiI18n.attribute(nav,'aria-label','nav.main');
  for (const [href,key,visible] of navigation) {
    // Keep this hidden anchor for older page scripts; authorization remains server-side.
    if (!visible && href !== 'users.html') continue;
    const link = document.createElement('a'); link.href = href; link.hidden = !visible;
    if (href === 'users.html') link.id = 'users-nav';
    if (href.split('?')[0] === activeSection) {link.className = 'active'; link.setAttribute('aria-current','page');}
    uiI18n.set(link,key); nav.append(link);
  }
  const select = document.querySelector('[data-ui-locale]');
  if (select) {
    let label = select.closest('label.locale-picker');
    if (!label) {label = document.createElement('label'); const caption = document.createElement('span'); uiI18n.set(caption,'language'); label.append(caption,select);}
    label.classList.add('locale-picker','sidebar-locale'); uiI18n.attribute(select,'aria-label','language');
    sidebar.insertBefore(label,nav);
  }
});
document.querySelectorAll('#current-user').forEach(node=>{
  const button=document.createElement('button');button.id='current-user';button.type='button';button.className='link-button account-trigger';button.textContent=signedInUser?.username||'';
  button.setAttribute('aria-haspopup','dialog');uiI18n.attribute(button,'aria-label','account.open',{username:signedInUser?.username||''});node.replaceWith(button);
});
document.querySelectorAll('[data-logout]').forEach(button=>{uiI18n.set(button,'nav.logout');button.addEventListener('click',auth.logout);});

// Account dialog shared by all authenticated pages; credentials are never prefilled.
if(signedInUser && auth.token() && document.querySelector('.sidebar')) {
  const modal=document.createElement('dialog');modal.className='modal account-modal';modal.id='account-modal';modal.setAttribute('aria-labelledby','account-modal-heading');
  modal.innerHTML='<form id="account-modal-form"><header><h2 id="account-modal-heading" data-i18n="account.heading"></h2><button type="button" class="icon-button" data-account-close data-i18n-aria-label="account.close">×</button></header><div class="modal-body"><p data-i18n="account.lead"></p><label><span data-i18n="account.current"></span><input name="currentPassword" type="password" autocomplete="current-password" required></label><label><span data-i18n="account.new"></span><small data-i18n="account.minimum"></small><input name="newPassword" type="password" autocomplete="new-password" minlength="12" required></label><label><span data-i18n="account.confirm"></span><input name="confirmation" type="password" autocomplete="new-password" minlength="12" required></label></div><footer><button type="button" class="secondary" data-account-close data-i18n="creators.cancel"></button><button class="primary" type="submit" data-i18n="account.submit"></button></footer></form>';
  document.body.append(modal);
  modal.querySelectorAll('[data-i18n]').forEach(n=>uiI18n.set(n,n.dataset.i18n));uiI18n.attribute(modal.querySelector('.icon-button'),'aria-label','account.close');
  modal.querySelectorAll('[data-account-close]').forEach(b=>b.onclick=()=>modal.close());
  modal.addEventListener('close',()=>modal.querySelector('form').reset());
  document.querySelectorAll('.account-trigger').forEach(button=>button.onclick=()=>modal.showModal());
}
function bindAccountPasswordForm(passwordForm) {
const passwordSubmit = passwordForm.querySelector('button[type=submit]');
passwordForm.addEventListener('submit', async event => {
  event.preventDefault();
  if (passwordSubmit.disabled) return;
  const form = new FormData(passwordForm);
  if (form.get('newPassword') !== form.get('confirmation')) { toast.errorKey('account.mismatch'); return; }
  let completed = false;
  passwordSubmit.disabled = true;
  passwordForm.setAttribute('aria-busy', 'true');
  uiI18n.set(passwordSubmit,'account.busy');
  try {
    const response = await fetch('/api/v1/auth/change-password', {
      method: 'POST', headers: auth.headers({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ currentPassword: form.get('currentPassword'), newPassword: form.get('newPassword') })
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw body.message?new Error(body.message):uiI18n.error('account.failed');
    completed = true;
    toast.successKey('account.success');
    setTimeout(auth.logout, 1200);
  } catch (error) { toast.errorObject(error); }
  finally {
    if (!completed) {
      passwordSubmit.disabled = false;
      passwordForm.setAttribute('aria-busy', 'false');
      uiI18n.set(passwordSubmit,'account.submit');
    }
  }
});

}
document.querySelectorAll('#password-form,#account-modal-form').forEach(bindAccountPasswordForm);

// Password visibility is per field, never changes its value or submits the form.
let passwordFieldSequence = 0;
document.querySelectorAll('input[type="password"]').forEach(input => {
  if (!input.id) input.id = `password-field-${++passwordFieldSequence}`;
  const wrapper = document.createElement('span'); wrapper.className = 'password-control';
  input.before(wrapper); wrapper.append(input);
  const button = document.createElement('button'); button.type = 'button'; button.className = 'password-toggle';
  button.setAttribute('aria-controls',input.id); button.setAttribute('aria-pressed','false'); uiI18n.set(button,'password.show');
  const hide = () => {input.type='password';button.setAttribute('aria-pressed','false');uiI18n.set(button,'password.show');};
  button.addEventListener('click',()=>{const visible=input.type==='password';input.type=visible?'text':'password';button.setAttribute('aria-pressed',String(visible));uiI18n.set(button,visible?'password.hide':'password.show');});
  input.form?.addEventListener('reset',hide); wrapper.append(button);
});

// Demo delivery must never pretend that a captured message reached the user's inbox.
if (document.querySelector('#register-form, #verify-form')) {
  fetch('/api/v1/public/mail-delivery',{cache:'no-store'}).then(response=>response.ok?response.json():null).then(config=>{
    if (config?.mode !== 'LOCAL_CAPTURE') return;
    const notice = document.createElement('aside'); notice.className = 'mail-delivery-notice'; notice.setAttribute('role','note');
    const text = document.createElement('p'); uiI18n.set(text,'mail.localNotice'); notice.append(text);
    if (config.previewUrl) {
      const url = new URL(config.previewUrl);
      if (url.protocol==='http:' && ['localhost','127.0.0.1','[::1]'].includes(url.hostname) && !url.username && !url.password) {
        const link = document.createElement('a'); link.href=url.href; link.target='_blank';link.rel='noopener noreferrer';uiI18n.set(link,'mail.localLink');notice.append(link);
      }
    }
    document.querySelector('#register-form, #verify-form').before(notice);
  }).catch(()=>{/* Standard SMTP mode remains usable if delivery metadata is unavailable. */});
}
