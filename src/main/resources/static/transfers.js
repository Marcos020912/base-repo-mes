/* Local, per-tab transfer monitor for uploads and public/authenticated downloads. */
const transfers = (() => {
  const tasks = [];
  document.body.classList.add('transfer-enabled');
  const toggle = document.createElement('button');
  toggle.type = 'button'; toggle.className = 'transfer-toggle';
  toggle.setAttribute('aria-controls', 'transfer-panel');
  toggle.setAttribute('aria-expanded', 'false');
  const panel = document.createElement('section');
  panel.id = 'transfer-panel'; panel.className = 'transfer-panel'; panel.hidden = true;
  panel.setAttribute('aria-label', 'Subidas y descargas');
  const heading = document.createElement('div'); heading.className = 'transfer-heading';
  const title = document.createElement('h2'); title.textContent = 'Transferencias';
  const clear = document.createElement('button'); clear.type = 'button'; clear.className = 'link-button'; clear.textContent = 'Limpiar finalizadas';
  const list = document.createElement('ol'); list.className = 'transfer-list';
  heading.append(title, clear); panel.append(heading, list); document.body.append(panel);
  (document.querySelector('.page-header .button-row') || document.querySelector('.page-header') || document.body).append(toggle);

  function refreshToggle() {
    const active = tasks.filter(task => task.active).length;
    toggle.textContent = active ? `⇅ Transferencias (${active})` : '⇅ Transferencias';
  }
  function add(label, direction) {
    const item = document.createElement('li'); item.className = 'transfer-item';
    const name = document.createElement('strong'); name.textContent = `${direction === 'upload' ? '↑' : '↓'} ${label}`;
    const status = document.createElement('span'); status.textContent = 'Iniciando…'; status.setAttribute('role', 'status');
    const progress = document.createElement('progress'); progress.max = 100; progress.removeAttribute('value');
    progress.setAttribute('aria-label', `Progreso de ${label}`);
    const cancel = document.createElement('button'); cancel.type = 'button'; cancel.className = 'link-button'; cancel.textContent = 'Cancelar';
    item.append(name, status, progress, cancel); list.prepend(item);
    const task = {item, status, progress, cancel, active:true};
    tasks.push(task); refreshToggle();
    while (tasks.length > 20) {
      const old = tasks.find(candidate => !candidate.active);
      if (!old) break;
      old.item.remove(); tasks.splice(tasks.indexOf(old), 1);
    }
    return task;
  }
  function setProgress(task, loaded, total) {
    if (total > 0) {
      const percent = Math.min(100, Math.round(loaded / total * 100));
      task.progress.value = percent;
      task.status.textContent = `${percent} % · ${loaded.toLocaleString('es')} de ${total.toLocaleString('es')} bytes`;
    } else {
      task.progress.removeAttribute('value');
      task.status.textContent = `${loaded.toLocaleString('es')} bytes · tamaño total no informado`;
    }
  }
  function finish(task, text, success = true) {
    task.active = false; task.status.textContent = text;
    task.progress.value = 100; task.progress.hidden = !success; task.cancel.hidden = true; refreshToggle();
  }
  toggle.addEventListener('click', () => {
    panel.hidden = !panel.hidden;
    toggle.setAttribute('aria-expanded', String(!panel.hidden));
  });
  clear.addEventListener('click', () => {
    for (const task of [...tasks]) if (!task.active) {
      task.item.remove(); tasks.splice(tasks.indexOf(task), 1);
    }
  });
  window.addEventListener('beforeunload', event => {
    if (tasks.some(task => task.active)) { event.preventDefault(); event.returnValue = ''; }
  });

  function headers(extra = {}) {
    return typeof auth !== 'undefined' ? auth.headers(extra) : extra;
  }

  function upload(url, file, label = file.name) {
    const task = add(label, 'upload');
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      task.cancel.addEventListener('click', () => xhr.abort());
      xhr.open('POST', url);
      for (const [name, value] of Object.entries(headers({Accept:'application/json'}))) xhr.setRequestHeader(name, value);
      xhr.upload.onprogress = event => setProgress(task, event.loaded, event.lengthComputable ? event.total : 0);
      xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 300) { finish(task, 'Subida completada'); resolve(); return; }
        let detail = xhr.responseText || 'No se pudo completar la subida.';
        try { const body = JSON.parse(detail); detail = body.detail || body.message || detail; } catch { /* Plain error body. */ }
        finish(task, 'Error en la subida', false); reject(new Error(detail));
      };
      xhr.onerror = () => { finish(task, 'Error de red', false); reject(new Error('Se interrumpió la conexión durante la subida.')); };
      xhr.onabort = () => { finish(task, 'Subida cancelada', false); reject(new Error('Subida cancelada.')); };
      const body = new FormData(); body.append('file', file); xhr.send(body);
    });
  }

  async function download(url, filename, label = filename) {
    const task = add(label, 'download');
    const controller = new AbortController();
    task.cancel.addEventListener('click', () => controller.abort());
    try {
      const response = await fetch(url, {headers:headers(), signal:controller.signal});
      if (!response.ok) throw new Error('No se pudo descargar el archivo.');
      const total = Number(response.headers.get('Content-Length')) || 0;
      const chunks = []; let loaded = 0;
      if (response.body) {
        const reader = response.body.getReader();
        while (true) {
          const {done, value} = await reader.read();
          if (done) break;
          chunks.push(value); loaded += value.byteLength; setProgress(task, loaded, total);
        }
      } else {
        const chunk = await response.arrayBuffer(); chunks.push(chunk); loaded = chunk.byteLength;
        setProgress(task, loaded, total);
      }
      const blob = new Blob(chunks, {type:response.headers.get('Content-Type') || 'application/octet-stream'});
      const blobUrl = URL.createObjectURL(blob);
      const link = document.createElement('a'); link.href = blobUrl; link.download = filename;
      document.body.append(link); link.click(); link.remove();
      setTimeout(() => URL.revokeObjectURL(blobUrl), 60000);
      finish(task, 'Descarga preparada');
    } catch (error) {
      finish(task, controller.signal.aborted ? 'Descarga cancelada' : 'Error en la descarga', false);
      throw error;
    }
  }
  refreshToggle();
  return {upload, download};
})();
