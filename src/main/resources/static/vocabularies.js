(async () => {
  const fields = [['licenseId', 'licenses'], ['discipline', 'disciplines']];
  try {
    const response = await fetch('/api/v1/scientific/vocabularies', { headers: auth.headers() });
    if (!response.ok) return;
    const vocabulary = await response.json();
    for (const [name, key] of fields) {
      const entries = vocabulary[key];
      if (!Array.isArray(entries)) continue;
      const datalist = document.createElement('datalist');
      datalist.id = `vocabulary-${name}`;
      for (const entry of entries) {
        const option = document.createElement('option'); option.value = entry; datalist.append(option);
      }
      document.body.append(datalist);
      document.querySelectorAll(`input[name="${name}"]`).forEach(input => input.setAttribute('list', datalist.id));
    }
  } catch (_) { /* The form remains usable if suggested terms are unavailable. */ }
})();
