const form = document.querySelector('#verify-form');
const message = document.querySelector('#verify-message');
const resend = document.querySelector('#resend');
const submit = form.querySelector('button');
const params = new URLSearchParams(location.search);
form.elements.email.value = params.get('email') || '';
function notifyKey(key,kind=''){message.className=`message ${kind}`.trim();uiI18n.set(message,key);}
function notifyError(error){message.className='message error';uiI18n.showError(message,error);}
function busy(value) {
  resend.disabled = value; submit.disabled = value;
  form.setAttribute('aria-busy', String(value));
}
if (params.get('mailPending') === '1')
  notifyKey('mail.pending', 'error');
async function post(path, payload) {
  const response = await fetch(path, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(payload)});
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw body.message?new Error(body.message):uiI18n.error('operation.failed');
  return body;
}
form.addEventListener('submit', async event => {
  event.preventDefault();
  if (submit.disabled) return;
  const data = new FormData(form);
  busy(true); notifyKey('verify.busy');
  try {
    await post('/api/v1/auth/verify', {email:data.get('email'), code:data.get('code')});
    notifyKey('verify.success', 'success');
    setTimeout(() => location.assign('login.html'), 900);
    // Remain disabled until navigation; do not verify the same code twice.
  } catch (error) {notifyError(error); busy(false);}
});
resend.addEventListener('click', async () => {
  if (resend.disabled) return;
  const email = form.elements.email;
  if (!email.reportValidity()) return;
  const address = email.value;
  busy(true); notifyKey('verify.requesting');
  try {
    await post('/api/v1/auth/resend-verification', {email:address});
    notifyKey('verify.sent', 'success');
  } catch (error) {notifyError(error);}
  finally {busy(false);}
});
