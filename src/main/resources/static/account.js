auth.requireLogin();
document.querySelector('#current-user').textContent = auth.user()?.username || '';
if (auth.user()?.role === 'ADMINISTRATOR') document.querySelector('#users-nav').hidden = false;
document.querySelectorAll('[data-logout]').forEach(button => button.addEventListener('click', auth.logout));
const passwordForm = document.querySelector('#password-form');
const passwordSubmit = passwordForm.querySelector('button[type=submit]');
passwordForm.addEventListener('submit', async event => {
  event.preventDefault();
  if (passwordSubmit.disabled) return;
  const form = new FormData(passwordForm);
  if (form.get('newPassword') !== form.get('confirmation')) { toast.error('Las contraseñas nuevas no coinciden.'); return; }
  let completed = false;
  passwordSubmit.disabled = true;
  passwordForm.setAttribute('aria-busy', 'true');
  passwordSubmit.textContent = 'Actualizando…';
  try {
    const response = await fetch('/api/v1/auth/change-password', {
      method: 'POST', headers: auth.headers({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ currentPassword: form.get('currentPassword'), newPassword: form.get('newPassword') })
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.message || 'No se pudo cambiar la contraseña.');
    completed = true;
    toast.success('Contraseña actualizada. Inicia sesión de nuevo.');
    setTimeout(auth.logout, 1200);
  } catch (error) { toast.error(error.message); }
  finally {
    if (!completed) {
      passwordSubmit.disabled = false;
      passwordForm.setAttribute('aria-busy', 'false');
      passwordSubmit.textContent = 'Cambiar contraseña';
    }
  }
});
