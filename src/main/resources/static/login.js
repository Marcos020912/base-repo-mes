// A stored token may be invalid after deployment; keep login reachable.
const loginForm = document.querySelector('#login-form');
const loginSubmit = loginForm.querySelector('button');
const loginMessage = document.querySelector('#login-message');
loginForm.addEventListener('submit', async event => {
  event.preventDefault();
  if (loginSubmit.disabled) return;
  const form = new FormData(loginForm);
  let navigating = false;
  loginSubmit.disabled = true; loginForm.setAttribute('aria-busy', 'true');
  loginMessage.className = 'message'; uiI18n.set(loginMessage,'login.validating');
  try {
    const response = await fetch('/api/v1/auth/login', {method:'POST', headers:{'Content-Type':'application/json'},
      body:JSON.stringify({username:form.get('username'), password:form.get('password')})});
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      if (body.code === 'EMAIL_NOT_VERIFIED') {
        navigating = true; location.assign(`verify.html?email=${encodeURIComponent(body.email || '')}`); return;
      }
      if (body.code === 'ACCOUNT_RESTRICTED') {
        loginMessage.className = 'message error';
        const link = document.createElement('a'); link.href = 'mailto:soporte@mes.gob.cu'; link.textContent = 'soporte@mes.gob.cu';
        delete loginMessage.dataset.i18n;const explanation=document.createElement('span');uiI18n.set(explanation,'login.restricted');loginMessage.replaceChildren(explanation, link, '.'); return;
      }
      throw body.message?new Error(body.message):uiI18n.error('login.failed');
    }
    if (typeof body.token !== 'string' || !body.token.trim() ||
        !body.user || typeof body.user.username !== 'string' || !body.user.username.trim() ||
        !['USER', 'CURATOR', 'ADMINISTRATOR'].includes(body.user.role))
      throw uiI18n.error('login.invalidResponse');
    localStorage.setItem('base-repo-token', body.token);
    localStorage.setItem('base-repo-user', JSON.stringify(body.user));
    navigating = true; location.replace('index.html');
  } catch (error) {loginMessage.className = 'message error'; uiI18n.showError(loginMessage,error);}
  finally {if (!navigating) {loginSubmit.disabled = false; loginForm.setAttribute('aria-busy', 'false');}}
});
