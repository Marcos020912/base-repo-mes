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
  loginMessage.className = 'message'; loginMessage.textContent = 'Validando…';
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
        loginMessage.replaceChildren('Su cuenta fue restringida. Póngase en contacto con ', link, '.'); return;
      }
      throw new Error(body.message || 'No se pudo iniciar sesión.');
    }
    localStorage.setItem('base-repo-token', body.token);
    localStorage.setItem('base-repo-user', JSON.stringify(body.user));
    navigating = true; location.replace('index.html');
  } catch (error) {loginMessage.className = 'message error'; loginMessage.textContent = error.message;}
  finally {if (!navigating) {loginSubmit.disabled = false; loginForm.setAttribute('aria-busy', 'false');}}
});
