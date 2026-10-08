#!/usr/bin/env node
/* Real browser + backend smoke test. Never connects to a deployed instance. */
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const net = require('node:net');
const crypto = require('node:crypto');
const {spawn, spawnSync} = require('node:child_process');
const puppeteer = require('../a11y/node_modules/puppeteer-core');

const root = path.resolve(__dirname, '../..');
const jar = path.join(root, 'build/libs/base-repo.jar');
const chrome = process.env.CHROME_BIN || '/usr/bin/google-chrome';
const java = process.env.JAVA_BIN || '/usr/lib/jvm/java-21-openjdk-amd64/bin/java';
const password = crypto.randomBytes(24).toString('hex');
const username = 'e2e-admin';
const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'reduniv-wizard-'));
let app;
let browser;

function assert(condition, message) { if (!condition) throw new Error(message); }
function sleep(ms) { return new Promise(resolve => setTimeout(resolve, ms)); }
function freePort() {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const port = server.address().port;
      server.close(error => error ? reject(error) : resolve(port));
    });
  });
}
function setupFiles() {
  const data = path.join(temp, 'data');
  const description = path.join(temp, 'description.md');
  const csv1 = path.join(temp, 'datos-uno.csv');
  const csv2 = path.join(temp, 'datos-dos.csv');
  fs.mkdirSync(data);
  fs.writeFileSync(description, '# Descripción de prueba\n\nDatos de demostración.\n');
  fs.writeFileSync(csv1, 'nombre,valor\nuno,1\n');
  fs.writeFileSync(csv2, 'nombre,valor\ndos,2\n');
  const packageDir = path.join(temp, 'package');
  fs.mkdirSync(path.join(packageDir, 'description'), {recursive:true});
  fs.writeFileSync(path.join(packageDir, 'description.md'),
    '# Descripción con imagen\n\n![Gráfico](description/chart.png)\n');
  fs.writeFileSync(path.join(packageDir, 'description/chart.png'), Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL/nwAAAABJRU5ErkJggg==', 'base64'));
  const zip = path.join(temp, 'project-description.zip');
  const packed = spawnSync('zip', ['-q', '-r', zip, 'description.md', 'description'], {cwd:packageDir});
  assert(packed.status === 0, 'No se pudo crear el ZIP de prueba; instale zip.');
  const fullDir = path.join(temp, 'full-package');
  fs.mkdirSync(path.join(fullDir, 'description'), {recursive:true});
  fs.writeFileSync(path.join(fullDir, 'description/description.md'), '# Paquete completo\n\n![Gráfico](chart.png)\n');
  fs.copyFileSync(path.join(packageDir, 'description/chart.png'), path.join(fullDir, 'description/chart.png'));
  fs.copyFileSync(csv1, path.join(fullDir, 'datos-uno.csv'));
  fs.copyFileSync(csv2, path.join(fullDir, 'datos-dos.csv'));
  const fullZip = path.join(temp, 'dataset-completo.zip');
  const fullPacked = spawnSync('zip', ['-q', '-r', fullZip, 'description', 'datos-uno.csv', 'datos-dos.csv'], {cwd:fullDir});
  assert(fullPacked.status === 0, 'No se pudo crear el ZIP completo de prueba.');
  const missingDescriptionZip = path.join(temp, 'sin-descripcion.zip');
  assert(spawnSync('zip', ['-q', missingDescriptionZip, 'datos-uno.csv'], {cwd:fullDir}).status === 0,
    'No se pudo preparar ZIP sin descripción.');
  fs.writeFileSync(path.join(fullDir, 'invalido.exe'), 'No debe aceptarse.');
  const invalidTypeZip = path.join(temp, 'tipo-invalido.zip');
  assert(spawnSync('zip', ['-q', '-r', invalidTypeZip, 'description', 'datos-uno.csv', 'invalido.exe'], {cwd:fullDir}).status === 0,
    'No se pudo preparar ZIP de tipo inválido.');
  return {data, description, csv1, csv2, zip, fullZip, missingDescriptionZip, invalidTypeZip};
}
async function startApp(port, files) {
  assert(fs.existsSync(jar), 'Falta build/libs/base-repo.jar; compile antes de iniciar el smoke test.');
  const config = path.join(temp, 'application.properties');
  fs.writeFileSync(config, [
    `server.port=${port}`, 'server.address=127.0.0.1',
    'spring.datasource.driver-class-name=org.h2.Driver',
    'spring.datasource.url=jdbc:h2:mem:reduniv_wizard_smoke;DB_CLOSE_DELAY=-1;MODE=LEGACY;NON_KEYWORDS=VALUE',
    'spring.datasource.username=sa', 'spring.datasource.password=sa',
    'spring.jpa.hibernate.ddl-auto=update',
    `repo.basepath=${pathToFileUrl(files.data)}`,
    'repo.auth.enabled=true',
    `repo.auth.jwtSecret=${crypto.randomBytes(48).toString('hex')}`,
    `repo.auth.bootstrap-admin-username=${username}`,
    `repo.auth.bootstrap-admin-password=${password}`,
    'repo.search.enabled=false', 'repo.messaging.enabled=false',
    'spring.cloud.config.enabled=false', 'eureka.client.enabled=false',
    'logging.level.edu.kit=INFO',
  ].join('\n') + '\n', {mode:0o600});
  const log = path.join(temp, 'application.log');
  const output = fs.openSync(log, 'w', 0o600);
  app = spawn(java, ['-jar', jar,
    `--spring.config.location=file:${path.join(root, 'config/application-default.properties')},file:${config}`,
    '--spring.profiles.active=default'], {cwd:root, stdio:['ignore', output, output]});
  fs.closeSync(output);
  const health = `http://127.0.0.1:${port}/actuator/health`;
  for (let attempt = 0; attempt < 90; attempt++) {
    if (app.exitCode !== null) throw new Error('El backend terminó durante el arranque.');
    try {
      const response = await fetch(health, {signal:AbortSignal.timeout(1500)});
      if (response.ok && (await response.json()).status === 'UP') return;
    } catch { /* The server has not bound the port yet. */ }
    await sleep(1000);
  }
  throw new Error('El backend no respondió a /actuator/health en 90 segundos.');
}
function pathToFileUrl(directory) {
  return require('node:url').pathToFileURL(directory + path.sep).href;
}
async function login(page, base) {
  await page.goto(base + '/login.html', {waitUntil:'load'});
  await page.type('input[name=username]', username);
  await page.type('input[name=password]', password);
  await page.click('#login-form button[type=submit]');
  await page.waitForFunction(() => location.pathname.endsWith('/index.html'), {timeout:15000});
}
async function deposit(page, base, files, mode) {
  const title = `Depósito E2E ${mode} ${Date.now()}`;
  await page.goto(base + '/create.html', {waitUntil:'load'});
  await page.waitForSelector('.creator-given');
  await page.type('input[name=title]', title);
  await page.type('.creator-given', 'Ada');
  await page.type('.creator-family', 'Ejemplo');
  await page.click('#wizard-next');
  await page.waitForSelector('.wizard-panel[data-step="1"]:not([hidden])');
  await page.type('input[name=licenseId]', 'CC-BY-4.0');
  await page.type('input[name=institution]', 'RedUniv');
  await page.type('textarea[name=methodology]', 'Metodología de prueba local.');
  await page.click('#wizard-next');
  await page.waitForSelector('.wizard-panel[data-step="2"]:not([hidden])');
  if (mode === 'package') {
    await page.click('[name="uploadMode"][value="package"]');
    await (await page.$('#package-file')).uploadFile(files.fullZip);
  } else {
    await (await page.$('#description-file')).uploadFile(mode === 'zip' ? files.zip : files.description);
    await (await page.$('#dataset-files')).uploadFile(...(mode === 'zip' ? [files.csv1, files.csv2] : [files.csv1]));
  }
  await page.click('#wizard-next');
  await page.waitForSelector('.wizard-panel[data-step="3"]:not([hidden])');
  await page.waitForFunction(() => !document.querySelector('#save-draft').disabled, {timeout:10000});
  const preview = await page.$eval('#preview-content', element => element.textContent);
  assert(preview.includes(title) && preview.includes('Ada Ejemplo'), 'La vista previa no muestra los metadatos.');
  if (mode === 'package') {
    assert((await page.$eval('#save-submit', element => element.textContent)).includes('revisar antes de enviar'),
      'El asistente promete enviar el ZIP sin mostrar su contenido real.');
    await page.click('#save-submit');
  } else await page.click('#save-draft');
  await page.waitForFunction(() => location.pathname.endsWith('/resource.html'), {timeout:30000});
  await page.waitForFunction(() => document.querySelector('#resource-title')?.textContent !== 'Cargando…', {timeout:15000});
  await page.waitForFunction(() => document.querySelector('#download-list')?.textContent.includes('datos-uno.csv'), {timeout:15000});
  assert((await page.$eval('#resource-title', element => element.textContent)) === title,
    'El borrador creado no muestra el título esperado.');
  const listing = await page.$eval('#download-list', element => element.textContent);
  if (mode !== 'md') {
    assert(listing.includes('datos-dos.csv'), 'Falta el segundo archivo del dataset.');
    await page.waitForFunction(() => document.querySelector('#markdown-rendered img')?.naturalWidth > 0,
      {timeout:15000});
    if (mode === 'package') await page.waitForFunction(() => document.querySelector('#markdown-rendered')?.textContent.includes('Paquete completo'),
      {timeout:15000});
  } else {
    await page.waitForFunction(() => document.querySelector('#markdown-rendered')?.textContent.includes('Descripción de prueba'),
      {timeout:15000});
  }
  const status = await page.evaluate(async () => {
    const id = new URLSearchParams(location.search).get('id');
    const token = localStorage.getItem('base-repo-token');
    const response = await fetch(`/api/v1/scientific/${encodeURIComponent(id)}`,
      {headers:{Authorization:`Bearer ${token}`}});
    return response.ok ? (await response.json()).status : `HTTP ${response.status}`;
  });
  assert(status === 'DRAFT', `El depósito no quedó como borrador: ${status}`);
  if (mode === 'package') {
    await page.waitForSelector('#submit-science:not([hidden])');
    await page.click('#submit-science');
    await page.waitForFunction(() => document.querySelector('#submit-preview-content')?.textContent.includes('Paquete completo'), {timeout:10000})
      .catch(async () => {
        const details = await page.evaluate(async () => {
          const id = new URLSearchParams(location.search).get('id');
          const response = await fetch(`/api/v1/scientific/${id}/quality`, {headers:{Authorization:`Bearer ${localStorage.getItem('base-repo-token')}`}});
          return {status:response.status, body:await response.text()};
        });
        throw new Error(`Vista previa posterior a ZIP: ${await page.$eval('#submit-preview-content', element => element.textContent)}; calidad: ${JSON.stringify(details)}`);
      });
    assert((await page.$eval('#submit-preview-content', element => element.textContent)).includes('datos-dos.csv'),
      'La vista previa previa al envío no muestra los archivos extraídos.');
    await page.click('#submit-preview-modal [data-close-modal]');
  }
  return {title, id:new URLSearchParams(new URL(page.url()).search).get('id'), fileCount:mode === 'md' ? 1 : 2};
}
async function rejectPackage(page, base, id, archive, expected) {
  const token = await page.evaluate(() => localStorage.getItem('base-repo-token'));
  const form = new FormData();
  form.append('file', new Blob([fs.readFileSync(archive)], {type:'application/zip'}), path.basename(archive));
  const response = await fetch(`${base}/api/v1/dataresources/${encodeURIComponent(id)}/attachments?package=true&path=${encodeURIComponent(path.basename(archive))}`,
    {method:'POST', headers:{Authorization:`Bearer ${token}`}, body:form});
  const body = await response.text();
  assert(response.status === 400 && body.includes(expected),
    `El ZIP inválido no fue rechazado correctamente: HTTP ${response.status} ${body}`);
}
async function cleanup() {
  if (browser) await browser.close().catch(() => {});
  if (app && app.exitCode === null) {
    app.kill('SIGTERM');
    await Promise.race([new Promise(resolve => app.once('exit', resolve)), sleep(5000)]);
    if (app.exitCode === null) app.kill('SIGKILL');
  }
  fs.rmSync(temp, {recursive:true, force:true});
}
async function main() {
  try {
    const files = setupFiles();
    const port = await freePort();
    await startApp(port, files);
    browser = await puppeteer.launch({executablePath:chrome, headless:true,
      args:['--no-sandbox', '--disable-dev-shm-usage']});
    const page = await browser.newPage();
    const pageErrors = [];
    page.on('pageerror', error => pageErrors.push(error.message));
    const base = `http://127.0.0.1:${port}`;
    await login(page, base);
    const markdown = await deposit(page, base, files, 'md');
    const zipped = await deposit(page, base, files, 'zip');
    const packaged = await deposit(page, base, files, 'package');
    const before = await page.$eval('#download-list', element => element.textContent);
    await rejectPackage(page, base, packaged.id, files.missingDescriptionZip, 'description/description.md');
    await rejectPackage(page, base, packaged.id, files.invalidTypeZip, 'no admite archivos .exe');
    await page.reload({waitUntil:'load'});
    await page.waitForFunction(() => document.querySelector('#download-list')?.textContent.includes('datos-uno.csv'));
    assert(await page.$eval('#download-list', element => element.textContent) === before,
      'Un ZIP rechazado alteró el contenido del depósito.');
    await page.click('#submit-science');
    await page.waitForFunction(() => document.querySelector('#submit-preview-content')?.textContent.includes('Paquete completo'));
    assert(await page.$eval('#confirm-submit-science', element => !element.disabled),
      'El envío quedó bloqueado pese a cumplir los requisitos de calidad.');
    await page.click('#confirm-submit-science');
    await page.waitForFunction(() => document.querySelector('#publication-status')?.textContent.includes('En revisión'));
    const submitted = await page.evaluate(async () => {
      const id = new URLSearchParams(location.search).get('id');
      const response = await fetch(`/api/v1/scientific/${id}`, {headers:{Authorization:`Bearer ${localStorage.getItem('base-repo-token')}`}});
      return (await response.json()).status;
    });
    assert(submitted === 'IN_REVIEW', `El depósito no llegó a revisión: ${submitted}`);
    assert(pageErrors.length === 0, `Errores JavaScript: ${pageErrors.join('; ')}`);
    process.stdout.write(`Asistente OK: ${markdown.title}; ${zipped.title}; ${packaged.title} (ZIP integral, vista previa y envío a revisión).\n`);
  } catch (error) {
    process.stderr.write(`${error.stack || error}\n`);
    const log = path.join(temp, 'application.log');
    if (fs.existsSync(log)) {
      const relevant = fs.readFileSync(log, 'utf8').split('\n')
        .filter(line => /ERROR|Exception|APPLICATION FAILED/.test(line)).slice(-12);
      if (relevant.length) process.stderr.write(relevant.join('\n') + '\n');
    }
    process.exitCode = 1;
  } finally { await cleanup(); }
}
main();
