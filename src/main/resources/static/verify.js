const form = document.querySelector('#verify-form');
const message = document.querySelector('#verify-message');
const resend = document.querySelector('#resend');
const submit = form.querySelector('button');
const params = new URLSearchParams(location.search);
form.elements.email.value = params.get('email') || '';
function notify(text, kind = '') {
  message.className = `message ${kind}`.trim();
  message.textContent = text;
}
function busy(value) {
  resend.disabled = value; submit.disabled = value;
  form.setAttribute('aria-busy', String(value));
}
if (params.get('mailPending') === '1')
  notify('Su cuenta fue creada, pero el correo no salió. Pulse «Reenviar código» más tarde.', 'error');
async function post(path, payload) {
  const response = await fetch(path, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(payload)});
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.message || 'No se pudo completar la operación.');
  return body;
}
form.addEventListener('submit', async event => {
  event.preventDefault();
  if (submit.disabled) return;
  const data = new FormData(form);
  busy(true); notify('Verificando correo…');
  try {
    await post('/api/v1/auth/verify', {email:data.get('email'), code:data.get('code')});
    notify('Correo verificado. Redirigiendo…', 'success');
    setTimeout(() => location.assign('login.html'), 900);
    // Remain disabled until navigation; do not verify the same code twice.
  } catch (error) {notify(error.message, 'error'); busy(false);}
});
resend.addEventListener('click', async () => {
  if (resend.disabled) return;
  busy(true); notify('Solicitando código…');
  try {
    await post('/api/v1/auth/resend-verification', {email:form.elements.email.value});
    notify('Si el correo existe, se envió un código.', 'success');
  } catch (error) {notify(error.message, 'error');}
  finally {busy(false);}
});
