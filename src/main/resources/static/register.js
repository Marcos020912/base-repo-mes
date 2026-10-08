// Keep registration reachable even when a stored session is stale.
const registrationForm = document.querySelector('#register-form');
const registrationMessage = document.querySelector('#register-message');
const registrationSubmit = registrationForm.querySelector('button');
registrationForm.addEventListener('submit', async event => {
  event.preventDefault();
  if (registrationSubmit.disabled) return;
  const form = new FormData(registrationForm);
  if (form.get('password') !== form.get('confirmation')) {
    registrationMessage.className = 'message error';
    registrationMessage.textContent = 'Las contraseñas no coinciden.'; return;
  }
  registrationSubmit.disabled = true; registrationForm.setAttribute('aria-busy', 'true');
  registrationMessage.className = 'message'; registrationMessage.textContent = 'Creando cuenta…';
  try {
    const response = await fetch('/api/v1/auth/register', {method:'POST', headers:{'Content-Type':'application/json'},
      body:JSON.stringify({username:form.get('username'), email:form.get('email'), password:form.get('password')})});
    const body = await response.json().catch(() => ({}));
    const mailPending = body.code === 'VERIFICATION_MAIL_UNAVAILABLE';
    if (!response.ok && !mailPending) throw new Error(body.message || 'No se pudo crear la cuenta.');
    registrationMessage.className = mailPending ? 'message error' : 'message success';
    registrationMessage.textContent = mailPending ? body.message : 'Cuenta creada. Revise su correo para verificarla.';
    setTimeout(() => location.assign(`verify.html?email=${encodeURIComponent(form.get('email'))}${mailPending ? '&mailPending=1' : ''}`), mailPending ? 2200 : 900);
  } catch (error) {
    registrationMessage.className = 'message error'; registrationMessage.textContent = error.message;
    registrationSubmit.disabled = false; registrationForm.setAttribute('aria-busy', 'false');
  }
});
