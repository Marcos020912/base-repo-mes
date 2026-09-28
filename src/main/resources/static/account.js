auth.requireLogin();
document.querySelector('#current-user').textContent = auth.user()?.username || '';
if (auth.user()?.role === 'ADMINISTRATOR') document.querySelector('#users-nav').hidden = false;
document.querySelectorAll('[data-logout]').forEach(button => button.addEventListener('click', auth.logout));
document.querySelector('#password-form').addEventListener('submit', async event => {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  if (form.get('newPassword') !== form.get('confirmation')) { toast.error('Las contraseñas nuevas no coinciden.'); return; }
  try {
    const response = await fetch('/api/v1/auth/change-password', {
      method: 'POST', headers: auth.headers({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ currentPassword: form.get('currentPassword'), newPassword: form.get('newPassword') })
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.message || 'No se pudo cambiar la contraseña.');
    toast.success('Contraseña actualizada. Inicia sesión de nuevo.');
    setTimeout(auth.logout, 1200);
  } catch (error) { toast.error(error.message); }
});
