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
  uiI18n.attribute(panel,'aria-label','transfer.panel');
  panel.tabIndex = -1;
  const heading = document.createElement('div'); heading.className = 'transfer-heading';
  const title = document.createElement('h2'); uiI18n.set(title,'transfer.title');
  const clear = document.createElement('button'); clear.type = 'button'; clear.className = 'link-button'; uiI18n.set(clear,'transfer.clear');
  const close = document.createElement('button'); close.type = 'button'; close.className = 'link-button transfer-close';
  uiI18n.set(close,'transfer.close'); uiI18n.attribute(close,'aria-label','transfer.closeLabel');
  const list = document.createElement('ol'); list.className = 'transfer-list';
  heading.append(title, clear, close); panel.append(heading);
  const diskMode = document.createElement('input'); diskMode.type = 'checkbox';
  diskMode.id = 'transfer-disk-mode';
  if (typeof window.showSaveFilePicker === 'function' && window.isSecureContext) {
    const diskLabel = document.createElement('label'); diskLabel.className = 'transfer-disk-option';
    const caption=document.createElement('span');uiI18n.set(caption,'transfer.disk');diskLabel.append(diskMode,document.createTextNode(' '),caption);
    panel.append(diskLabel);
  }
  panel.append(list); document.body.append(panel);
  (document.querySelector('.page-header .button-row') || document.querySelector('.page-header') || document.body).append(toggle);

  function refreshToggle() {
    const active = tasks.filter(task => task.active).length;
    uiI18n.set(toggle,active?'transfer.activeToggle':'transfer.toggle',{count:active});
  }
  function add(label, direction) {
    const item = document.createElement('li'); item.className = 'transfer-item';
    const name = document.createElement('strong'); name.textContent = `${direction === 'upload' ? '↑' : '↓'} ${label}`;
    const status = document.createElement('span'); uiI18n.set(status,'transfer.starting'); status.setAttribute('role', 'status');
    const progress = document.createElement('progress'); progress.max = 100; progress.removeAttribute('value');
    uiI18n.attribute(progress,'aria-label','transfer.progressLabel',{name:label});
    const cancel = document.createElement('button'); cancel.type = 'button'; cancel.className = 'link-button'; uiI18n.set(cancel,'transfer.cancel');
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
    task.phase='progress';task.loaded=loaded;task.total=total;
    if (total > 0) {
      const percent = Math.min(100, Math.round(loaded / total * 100));
      task.progress.value = percent;
      uiI18n.set(task.status,'transfer.progress',{percent,loaded:loaded.toLocaleString(uiI18n.locale),total:total.toLocaleString(uiI18n.locale)});
    } else {
      task.progress.removeAttribute('value');
      uiI18n.set(task.status,'transfer.unknownTotal',{loaded:loaded.toLocaleString(uiI18n.locale)});
    }
  }
  function finish(task, key, success = true) {
    task.phase='finished';task.active = false;uiI18n.set(task.status,key);
    task.progress.value = 100; task.progress.hidden = !success; task.cancel.hidden = true; refreshToggle();
  }
  function closePanel() {
    const returnFocus = panel.contains(document.activeElement);
    panel.hidden = true; toggle.setAttribute('aria-expanded', 'false');
    if (returnFocus) toggle.focus();
  }
  toggle.addEventListener('click', () => {
    if (!panel.hidden) { closePanel(); return; }
    panel.hidden = false; toggle.setAttribute('aria-expanded', 'true'); panel.focus();
  });
  close.addEventListener('click', closePanel);
  panel.addEventListener('keydown', event => {
    if (event.key === 'Escape') { event.preventDefault(); closePanel(); }
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
      function waitingForServer() {
        // All bytes sent is not confirmation of validation or durable storage.
        if (!task.active) return;
        task.progress.removeAttribute('value');
        task.phase='waiting';uiI18n.set(task.status,'transfer.waiting');
      }
      xhr.upload.onprogress = event => {
        if (event.lengthComputable && event.loaded >= event.total) waitingForServer();
        else setProgress(task, event.loaded, event.lengthComputable ? event.total : 0);
      };
      xhr.upload.onload = waitingForServer;
      xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 300) { finish(task, 'transfer.uploadComplete'); resolve(); return; }
        let failure=uiI18n.error('transfer.uploadFailed');
        if ((xhr.getResponseHeader('Content-Type') || '').includes('json')) {
          try {
            const body = JSON.parse(xhr.responseText);
            const message = body.detail || body.message;
            if (typeof message === 'string' && message.trim()) failure = new Error(message);
          } catch { /* Invalid error body: keep the user-facing fallback. */ }
        }
        finish(task, 'transfer.uploadError', false); reject(failure);
      };
      xhr.onerror = () => { finish(task, 'transfer.networkError', false); reject(uiI18n.error('transfer.uploadConnectionLost')); };
      xhr.onabort = () => { finish(task, 'transfer.uploadCancelled', false); reject(uiI18n.error('transfer.uploadCancelledError')); };
      const body = new FormData(); body.append('file', file); xhr.send(body);
    });
  }

  async function download(url, filename, label = filename, options = {}) {
    if (new URL(url, location.href).origin !== location.origin)
      throw uiI18n.error('transfer.sameServer');
    const task = add(label, 'download');
    const controller = new AbortController();
    task.cancel.addEventListener('click', () => controller.abort());
    let writable;
    try {
      let fileHandle;
      if (diskMode.checked) {
        task.phase='choose-location';uiI18n.set(task.status,'transfer.chooseLocation');
        fileHandle = await window.showSaveFilePicker({suggestedName:filename});
        controller.signal.throwIfAborted();
      }
      const response = await fetch(url, {headers:options.headers || headers(), cache:options.cache || 'default', redirect:'error', signal:controller.signal});
      if (!response.ok) {
        let failure=options.errorMessage?new Error(options.errorMessage):uiI18n.error('transfer.downloadFailed');
        if (!options.errorMessage && (response.headers.get('Content-Type') || '').includes('json')) {
          try {
            const problem = await response.json();
            const detail = problem.detail || problem.message;
            if (typeof detail === 'string' && detail.trim()) failure = new Error(detail);
          } catch { /* Keep the generic message for invalid error bodies. */ }
        }
        throw failure;
      }
      const total = Number(response.headers.get('Content-Length')) || 0;
      if (fileHandle) {
        if (!response.body) throw uiI18n.error('transfer.noStreaming');
        writable = await fileHandle.createWritable();
        controller.signal.throwIfAborted();
      }
      const chunks = []; let loaded = 0;
      if (response.body) {
        const reader = response.body.getReader();
        while (true) {
          const {done, value} = await reader.read();
          if (done) break;
          controller.signal.throwIfAborted();
          if (writable) await writable.write(value);
          else chunks.push(value);
          loaded += value.byteLength; setProgress(task, loaded, total);
        }
      } else {
        const chunk = await response.arrayBuffer(); chunks.push(chunk); loaded = chunk.byteLength;
        setProgress(task, loaded, total);
      }
      controller.signal.throwIfAborted();
      if (writable) {
        task.cancel.disabled = true;
        task.phase='finalizing';uiI18n.set(task.status,'transfer.finalizing');
        await writable.close(); writable = null;
        finish(task, 'transfer.saved'); return {savedToDisk:true};
      }
      const blob = new Blob(chunks, {type:response.headers.get('Content-Type') || 'application/octet-stream'});
      const blobUrl = URL.createObjectURL(blob);
      const link = document.createElement('a'); link.href = blobUrl; link.download = filename;
      document.body.append(link); link.click(); link.remove();
      setTimeout(() => URL.revokeObjectURL(blobUrl), 60000);
      finish(task, 'transfer.downloadReady'); return {savedToDisk:false};
    } catch (error) {
      const cancelled = controller.signal.aborted || error.name === 'AbortError';
      // A local write failure must stop the HTTP stream too; classify it before aborting.
      controller.abort();
      if (writable) {
        try { await writable.abort(); } catch { /* Preserve the original download error. */ }
      }
      finish(task, cancelled ? 'transfer.downloadCancelled' : 'transfer.downloadError', false);
      throw error;
    }
  }
  window.addEventListener('ui-locale-changed',()=>{refreshToggle();for(const task of tasks)if(task.active&&task.phase==='progress')setProgress(task,task.loaded,task.total);});
  refreshToggle();
  return {upload, download};
})();
