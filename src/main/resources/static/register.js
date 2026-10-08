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
    uiI18n.set(registrationMessage,'password.mismatch'); return;
  }
  registrationSubmit.disabled = true; registrationForm.setAttribute('aria-busy', 'true');
  registrationMessage.className = 'message'; uiI18n.set(registrationMessage,'register.busy');
  try {
    const response = await fetch('/api/v1/auth/register', {method:'POST', headers:{'Content-Type':'application/json'},
      body:JSON.stringify({username:form.get('username'), email:form.get('email'), password:form.get('password')})});
    const body = await response.json().catch(() => ({}));
    const mailPending = body.code === 'VERIFICATION_MAIL_UNAVAILABLE';
    if (!response.ok && !mailPending) throw body.message?new Error(body.message):uiI18n.error('register.failed');
    registrationMessage.className = mailPending ? 'message error' : 'message success';
    if(mailPending&&body.message)uiI18n.plain(registrationMessage,body.message);else uiI18n.set(registrationMessage,mailPending?'mail.pending':'register.success');
    setTimeout(() => location.assign(`verify.html?email=${encodeURIComponent(form.get('email'))}${mailPending ? '&mailPending=1' : ''}`), mailPending ? 2200 : 900);
  } catch (error) {
    registrationMessage.className = 'message error'; uiI18n.showError(registrationMessage,error);
    registrationSubmit.disabled = false; registrationForm.setAttribute('aria-busy', 'false');
  }
});
