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
let postgres;
let smtp;
const verificationMessages = [];
let rejectSmtp = false;
let verifiedAccount;

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
  const crafted = (filename, kind) => {
    const archive = path.join(temp, filename);
    const program = `import sys,zipfile\nwith zipfile.ZipFile(sys.argv[1],'w') as z:\n k=sys.argv[2]\n if k=='description-traversal':\n  z.writestr('description.md','# Prueba\\n'); z.writestr('folder/../chart.png',b'png')\n elif k=='description-duplicate':\n  z.writestr('description.md','# Uno\\n'); z.writestr('description.md','# Dos\\n')\n elif k=='description-html':\n  z.writestr('description.md','# Prueba\\n'); z.writestr('script.html','<script></script>')\n elif k=='package-traversal':\n  z.writestr('description/description.md','# Prueba\\n'); z.writestr('datos-uno.csv','a,b\\n1,2\\n'); z.writestr('../fuera.csv','x,y\\n3,4\\n')\n`;
    const result = spawnSync('python3', ['-c', program, archive, kind], {encoding:'utf8'});
    assert(result.status === 0, `No se pudo crear ZIP malicioso ${kind}: ${result.stderr}`);
    return archive;
  };
  return {data, description, csv1, csv2, zip, fullZip, missingDescriptionZip, invalidTypeZip,
    descriptionTraversalZip:crafted('descripcion-ruta-invalida.zip','description-traversal'),
    descriptionDuplicateZip:crafted('descripcion-duplicada.zip','description-duplicate'),
    descriptionHtmlZip:crafted('descripcion-html.zip','description-html'),
    fullTraversalZip:crafted('deposito-ruta-invalida.zip','package-traversal')};
}
async function smtpProperties() {
  if (process.env.E2E_MAIL !== '1') return [];
  if (!smtp) {
    smtp = net.createServer(socket => {
      socket.setEncoding('utf8'); socket.write('220 localhost fixture SMTP\r\n');
      let buffer='', data=false, message=[];
      socket.on('data', chunk => {
        buffer += chunk;
        while (buffer.includes('\n')) {
          const end=buffer.indexOf('\n'), line=buffer.slice(0,end).replace(/\r$/, '');
          buffer=buffer.slice(end+1);
          if (data) {
            if (line==='.') { verificationMessages.push(message.join('\n')); message=[]; data=false; socket.write('250 accepted\r\n'); }
            else message.push(line);
          } else if (/^(EHLO|HELO)/i.test(line)) socket.write('250 localhost\r\n');
          else if (/^MAIL FROM:/i.test(line)) socket.write(rejectSmtp ? '451 4.3.0 Temporary lookup error\r\n' : '250 sender accepted\r\n');
          else if (/^RCPT TO:/i.test(line)) socket.write(/@example\.invalid>/i.test(line) ? '250 recipient accepted\r\n' : '550 only test recipients\r\n');
          else if (/^DATA$/i.test(line)) { data=true; socket.write('354 send data\r\n'); }
          else if (/^QUIT$/i.test(line)) socket.end('221 bye\r\n');
          else if (/^RSET$/i.test(line)) { data=false;message=[];socket.write('250 reset\r\n'); }
          else socket.write('502 unsupported\r\n');
        }
      });
      socket.on('error', () => {});
    });
    await new Promise(resolve => smtp.listen(0,'127.0.0.1',resolve));
  }
  return ['spring.mail.host=127.0.0.1', `spring.mail.port=${smtp.address().port}`,
    'spring.mail.username=', 'spring.mail.password=', 'repo.mail.from=fixture@example.invalid',
    'spring.mail.properties.mail.smtp.auth=false',
    'spring.mail.properties.mail.smtp.starttls.enable=false',
    'spring.mail.properties.mail.smtp.starttls.required=false',
    'spring.mail.properties.mail.smtp.ssl.enable=false',
    'spring.mail.properties.mail.smtp.connectiontimeout=5000',
    'spring.mail.properties.mail.smtp.timeout=5000'];
}
async function verificationFlow(base) {
  if (!smtp) return;
  const email='verification@example.invalid', name='verification-fixture';
  const secret=crypto.randomBytes(16).toString('hex');
  async function post(endpoint, body) {
    const response=await fetch(base+'/api/v1/auth/'+endpoint, {
      method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)
    });
    return {status:response.status, body:await response.json()};
  }
  const created=await post('register',{username:name,email,password:secret});
  assert(created.status===201, 'El registro con SMTP local no completó.');
  assert(verificationMessages.length===1, 'No llegó exactamente un mensaje al SMTP local.');
  const code=verificationMessages[0].match(/es: ([0-9]{6})/)?.[1];
  assert(code, 'El correo local no contiene código de seis dígitos.');
  const blocked=await post('login',{username:name,password:secret});
  assert(blocked.body.code==='EMAIL_NOT_VERIFIED' && !blocked.body.token, 'Una cuenta sin verificar obtuvo acceso.');
  const verified=await post('verify',{email,code});
  assert(verified.status===200, 'El código enviado por SMTP no verificó la cuenta.');
  const logged=await post('login',{username:name,password:secret});
  assert(logged.status===200 && logged.body.token && logged.body.user.role==='USER',
    'La cuenta verificada no inició sesión como Usuario.');
  verifiedAccount={...logged.body, password:secret};
  const replay=await post('verify',{email,code});
  assert(replay.status===400, 'El código ya consumido se pudo reutilizar.');
  const recoveryEmail='mail-recovery@example.invalid', recoveryName='mail-recovery-fixture';
  rejectSmtp=true;
  try {
    const failed=await post('register',{username:recoveryName,email:recoveryEmail,password:secret});
    assert(failed.status===503 && failed.body.code==='VERIFICATION_MAIL_UNAVAILABLE',
      'El fallo SMTP no conservó la respuesta recuperable de registro.');
    assert(verificationMessages.length===1, 'El SMTP rechazado aceptó un mensaje.');
    const retry=await post('register',{username:recoveryName,email:recoveryEmail,password:secret});
    assert(retry.status===409, 'La cuenta creada con correo fallido desapareció o se duplicó.');
    const denied=await post('login',{username:recoveryName,password:secret});
    assert(denied.body.code==='EMAIL_NOT_VERIFIED' && !denied.body.token,
      'La cuenta con correo fallido obtuvo acceso.');
    const unavailable=await post('resend-verification',{email:recoveryEmail});
    assert(unavailable.status===503 && !JSON.stringify(unavailable.body).includes('Temporary lookup error'),
      'Reenvío fallido no devolvió mensaje seguro.');
  } finally { rejectSmtp=false; }
  const resent=await post('resend-verification',{email:recoveryEmail});
  assert(resent.status===200 && verificationMessages.length===2, 'No se recuperó el reenvío SMTP.');
  const recoveryCode=verificationMessages[1].match(/es: ([0-9]{6})/)?.[1];
  assert(recoveryCode, 'No llegó código de recuperación.');
  assert((await post('verify',{email:recoveryEmail,code:recoveryCode})).status===200,
    'El código reenviado no verificó la cuenta conservada.');
  const recovered=await post('login',{username:recoveryName,password:secret});
  assert(recovered.status===200 && recovered.body.user.role==='USER', 'No se pudo entrar tras recuperar correo.');
  process.stdout.write('Correo local OK: registro/verificación y recuperación tras rechazo SMTP451.\n');
}
async function databaseProperties() {
  if (process.env.E2E_POSTGRES !== '1') return [
    'spring.datasource.driver-class-name=org.h2.Driver',
    'spring.datasource.url=jdbc:h2:mem:reduniv_wizard_smoke;DB_CLOSE_DELAY=-1;MODE=LEGACY;NON_KEYWORDS=VALUE',
    'spring.datasource.username=sa', 'spring.datasource.password=sa'
  ];
  if (postgres) return ['spring.datasource.driver-class-name=org.postgresql.Driver',
    `spring.datasource.url=jdbc:postgresql://127.0.0.1:${postgres.port}/${postgres.database}`,
    'spring.datasource.username=e2e_admin', `spring.datasource.password=${postgres.secret}`];
  assert(process.getuid() !== 0, 'Ejecute PostgreSQL efímero sin sudo.');
  const bin = process.env.PG_BIN || '/usr/lib/postgresql/18/bin';
  const data = path.join(temp, 'pgdata');
  const secret = crypto.randomBytes(24).toString('hex');
  const pwfile = path.join(temp, 'pg-password');
  fs.writeFileSync(pwfile, secret + '\n', {mode:0o600});
  const initialized = spawnSync(path.join(bin, 'initdb'),
    ['-D', data, '-U', 'e2e_admin', '-A', 'scram-sha-256', '--pwfile', pwfile, '--no-instructions'], {encoding:'utf8'});
  assert(initialized.status === 0, `initdb efímero falló: ${initialized.stderr}`);
  const port = await freePort();
  const started = spawnSync(path.join(bin, 'pg_ctl'), ['-D', data, '-l', path.join(temp, 'postgres.log'),
    '-o', `-h 127.0.0.1 -p ${port} -k ${temp}`, '-w', 'start'], {encoding:'utf8'});
  postgres = {bin, data, port, secret, database:'reduniv_e2e'};
  assert(started.status === 0, `PostgreSQL efímero no inició: ${started.stderr}`);
  const created = spawnSync(path.join(bin, 'createdb'), ['-h', '127.0.0.1', '-p', String(port), '-U', 'e2e_admin', 'reduniv_e2e'],
    {encoding:'utf8', env:{...process.env, PGPASSWORD:secret}});
  assert(created.status === 0, `No se pudo crear base efímera: ${created.stderr}`);
  return ['spring.datasource.driver-class-name=org.postgresql.Driver',
    `spring.datasource.url=jdbc:postgresql://127.0.0.1:${port}/reduniv_e2e`,
    'spring.datasource.username=e2e_admin', `spring.datasource.password=${secret}`];
}
async function startApp(port, files) {
  assert(fs.existsSync(jar), 'Falta build/libs/base-repo.jar; compile antes de iniciar el smoke test.');
  const config = path.join(temp, 'application.properties');
  fs.writeFileSync(config, [
    `server.port=${port}`, 'server.address=127.0.0.1',
    ...await databaseProperties(),
    ...await smtpProperties(),
    `spring.jpa.hibernate.ddl-auto=${postgres?.restored ? 'validate' : 'update'}`,
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
  await page.select('select[name=privacyClassification]','NONE');
  await page.type('textarea[name=productionDescription]','Producción científica declarada por sensores.');
  await page.type('textarea[name=processingDescription]','Limpieza científica declarada de valores ausentes.');
  await page.type('textarea[name=processingTools]','Python 3.12; script público v1.');
  await page.type('textarea[name=summary]','Resumen estructurado de prueba.');
  await page.type('input[name=geographicCoverage]','Cuba');
  await page.evaluate(()=>{document.querySelector('[name=temporalStart]').value='2025-01-01';document.querySelector('[name=temporalEnd]').value='2025-12-31';});
  await page.click('#metadata-translations > button');
  await page.type('.translation-row input','en');
  await page.type('.translation-row label:nth-of-type(2) input','Translated scientific title');
  await page.type('.translation-row textarea','Structured translated summary.');
  if(mode==='md') {
    await page.evaluate(()=>document.querySelector('[name=temporalEnd]').value='2024-12-31');
    await page.click('#wizard-next');
    assert(await page.$eval('.wizard-panel[data-step="1"]',element=>!element.hidden),'El asistente aceptó un rango temporal invertido.');
    await page.evaluate(()=>document.querySelector('[name=temporalEnd]').value='2025-12-31');
  }
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
  assert(preview.includes('Vista previa de cita (borrador)') && preview.includes('Cita provisional') && preview.includes(title), 'Asistente sin cita de borrador previa o sin título.');
  assert(preview.includes(title) && preview.includes('Ada Ejemplo'), 'La vista previa no muestra los metadatos.');
  if (mode === 'package') {
    assert((await page.$eval('#save-submit', element => element.textContent)).includes('revisar antes de enviar'),
      'El asistente promete enviar el ZIP sin mostrar su contenido real.');
    await page.click('#save-submit');
  } else await page.click('#save-draft');
  await page.waitForFunction(() => location.pathname.endsWith('/resource.html'), {timeout:30000})
    .catch(async () => { throw new Error(`No redirigió después de guardar: ${await page.$eval('#wizard-message', element => element.textContent)}; transferencias: ${await page.evaluate(() => document.querySelector('.transfer-list')?.textContent || 'monitor ausente')}`); });
  await page.waitForFunction(() => document.querySelector('#resource-title')?.textContent !== 'Cargando…', {timeout:15000});
  await page.waitForFunction(() => document.querySelector('#download-list')?.textContent.includes('datos-uno.csv'), {timeout:15000});
  assert((await page.$eval('#resource-title', element => element.textContent)) === title,
    'El borrador creado no muestra el título esperado.');
  const listing = await page.$eval('#download-list', element => element.textContent);
  if (mode !== 'md') {
    assert(listing.includes('datos-dos.csv'), 'Falta el segundo archivo del dataset.');
    await page.waitForSelector('#markdown-rendered img');
    await page.$eval('#markdown-rendered img',image=>image.scrollIntoView({block:'center'}));
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
  const structured=await page.evaluate(async()=>await (await fetch('/api/v1/scientific/'+new URLSearchParams(location.search).get('id'),{headers:auth.headers()})).json());
  assert(structured.productionDescription==='Producción científica declarada por sensores.'&&structured.processingDescription==='Limpieza científica declarada de valores ausentes.'&&structured.processingTools==='Python 3.12; script público v1.','Procedencia científica no persistida.');
  assert(structured.summary==='Resumen estructurado de prueba.' && structured.temporalStart==='2025-01-01' && structured.geographicCoverage==='Cuba' && structured.translations.en.title==='Translated scientific title','El depósito no persistió metadatos estructurados/traducciones.');
  assert(status === 'DRAFT', `El depósito no quedó como borrador: ${status}`);
  if (mode === 'md') {
    await page.click('.download-file[data-path="datos-uno.csv"]');
    await page.waitForFunction(() => document.querySelector('.transfer-list')?.textContent.includes('Descarga preparada'));
    await page.click('.transfer-toggle');
    assert(await page.$eval('#transfer-panel', element => !element.hidden), 'No se abrió el monitor de descargas.');
    assert(await page.$eval('.transfer-item progress', element => element.value) === 100, 'La descarga no terminó en el monitor.');
    await page.evaluate(require('../a11y/node_modules/axe-core').source);
    const violations = await page.evaluate(async () => (await axe.run('#transfer-panel')).violations.map(item => item.id));
    assert(violations.length === 0, `Accesibilidad del monitor: ${violations.join(', ')}`);
    await page.click('.transfer-toggle');
  }
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
async function rejectDescription(page, base, id, archive, expected) {
  const token = await page.evaluate(() => localStorage.getItem('base-repo-token'));
  const form = new FormData();
  form.append('file', new Blob([fs.readFileSync(archive)], {type:'application/zip'}), path.basename(archive));
  const response = await fetch(`${base}/api/v1/dataresources/${encodeURIComponent(id)}/description`,
    {method:'POST', headers:{Authorization:`Bearer ${token}`}, body:form});
  const body = await response.text();
  assert(response.status === 400 && body.includes(expected),
    `El ZIP de descripción inválido no fue rechazado: HTTP ${response.status} ${body}`);
}
async function recoverInterruptedUpload(page, base, files) {
  const title = `Depósito E2E interrumpido ${Date.now()}`;
  await page.goto(base + '/create.html', {waitUntil:'load'});
  await page.waitForSelector('.creator-given');
  await page.type('input[name=title]', title);
  await page.type('.creator-given', 'Eva');
  await page.click('#wizard-next');
  await page.waitForSelector('.wizard-panel[data-step="1"]:not([hidden])');
  await page.type('input[name=licenseId]', 'CC-BY-4.0');
  await page.type('input[name=institution]', 'RedUniv');
  await page.type('textarea[name=methodology]', 'Prueba de recuperación.');
  await page.click('#wizard-next');
  await page.waitForSelector('.wizard-panel[data-step="2"]:not([hidden])');
  await (await page.$('#description-file')).uploadFile(files.description);
  await (await page.$('#dataset-files')).uploadFile(files.csv1);
  await page.click('#wizard-next');
  await page.waitForFunction(() => !document.querySelector('#save-submit').disabled);
  const interrupt = request => request.url().includes('/attachments?') ? request.abort('failed') : request.continue();
  await page.setRequestInterception(true);
  page.on('request', interrupt);
  try {
    await page.click('#save-submit');
    await page.waitForSelector('#wizard-message a[href^="resource.html?id="]', {timeout:30000});
    assert(new URL(page.url()).pathname.endsWith('/create.html'), 'El depósito se envió pese al fallo de subida.');
    assert((await page.$eval('.transfer-list', element => element.textContent)).includes('Error de red'),
      'El monitor no informó la subida interrumpida.');
  } finally {
    page.off('request', interrupt);
    await page.setRequestInterception(false);
  }
  const link = await page.$eval('#wizard-message a', anchor => anchor.href);
  const id = new URL(link).searchParams.get('id');
  const before = await page.evaluate(async resourceId => {
    const response = await fetch(`/api/v1/scientific/${resourceId}`, {headers:{Authorization:`Bearer ${localStorage.getItem('base-repo-token')}`}});
    return (await response.json()).status;
  }, id);
  assert(before === 'DRAFT', `El fallo de subida cambió el estado a ${before}.`);
  await page.goto(link, {waitUntil:'load'});
  await page.waitForFunction(() => document.querySelector('#markdown-rendered')?.textContent.includes('Descripción de prueba'));
  assert(!(await page.$eval('#download-list', element => element.textContent)).includes('datos-uno.csv'),
    'La prueba de interrupción no abortó la subida de datos.');
  await page.click('[data-open-modal="files-modal"]');
  await (await page.$('#resource-files')).uploadFile(files.csv1);
  await page.click('#files-form button.primary');
  await page.waitForFunction(() => document.querySelector('#download-list')?.textContent.includes('datos-uno.csv'));
  await page.click('#submit-science');
  await page.waitForFunction(() => document.querySelector('#submit-preview-content')?.textContent.includes('Descripción de prueba'));
  assert(await page.$eval('#confirm-submit-science', element => !element.disabled),
    'El depósito recuperado sigue bloqueado para revisión.');
  await page.click('#confirm-submit-science');
  await page.waitForFunction(() => document.querySelector('#publication-status')?.textContent.includes('En revisión'));
  return title;
}
async function deriveNewVersion(page, base, files, previous) {
  const token = await page.evaluate(() => localStorage.getItem('base-repo-token'));
  async function request(method, endpoint, body) {
    const response = await fetch(base + endpoint, {method,
      headers:{Authorization:`Bearer ${token}`, ...(body ? {'Content-Type':'application/json'} : {})},
      ...(body ? {body:JSON.stringify(body)} : {})});
    const text = await response.text();
    assert(response.ok, `${method} ${endpoint} falló: HTTP ${response.status} ${text}`);
    return text ? JSON.parse(text) : null;
  }
  const versionDoi = `10.99999/reduniv-local-${Date.now()}`;
  await request('PUT', `/api/v1/scientific/${previous.id}`, {
    versionLabel:'1.0', versionDoi, conceptualDoi:`10.99999/reduniv-concept-local-${Date.now()}`,
    licenseId:'CC-BY-4.0', institution:'RedUniv', methodology:'Metodología de prueba local.', accessLevel:'OPEN'
  });
  await request('POST', `/api/v1/scientific/${previous.id}/submit`);
  // Synthetic DOI approval is confined to the throwaway local database.
  await request('POST', `/api/v1/scientific/${previous.id}/publish`, {doiRegisteredExternally:true});
  await page.goto(`${base}/resource.html?id=${encodeURIComponent(previous.id)}`, {waitUntil:'load'});
  await page.waitForSelector('#new-version:not([hidden])');
  await page.click('#new-version');
  await page.waitForFunction(id => location.pathname.endsWith('/create.html') && new URLSearchParams(location.search).get('basedOn') === id,
    {}, previous.id);
  await page.waitForFunction(title => document.querySelector('input[name=title]')?.value === title,
    {}, previous.title);
  await page.click('#wizard-next');
  await page.waitForSelector('.wizard-panel[data-step="1"]:not([hidden])');
  await page.$eval('input[name=versionLabel]', input => {input.value='2.0';input.dispatchEvent(new Event('input',{bubbles:true}));});
  await page.type('input[name=licenseId]', 'CC-BY-4.0');
  await page.type('input[name=institution]', 'RedUniv');
  await page.type('textarea[name=methodology]', 'Segunda versión de prueba.');
  await page.click('#wizard-next');
  await page.waitForSelector('.wizard-panel[data-step="2"]:not([hidden])');
  await (await page.$('#description-file')).uploadFile(files.description);
  await (await page.$('#dataset-files')).uploadFile(files.csv2);
  await page.click('#wizard-next');
  await page.waitForFunction(() => !document.querySelector('#save-draft').disabled);
  assert((await page.$eval('#preview-content', node => node.textContent)).includes(`Nueva versión de ${previous.id}`),
    'La vista previa no muestra el vínculo con la versión anterior.');
  await page.click('#save-draft');
  await page.waitForFunction(() => location.pathname.endsWith('/resource.html'), {timeout:30000});
  const id = new URLSearchParams(new URL(page.url()).search).get('id');
  const next = await request('GET', `/api/v1/scientific/${id}`);
  assert(next.status === 'DRAFT' && next.previousResourceId === previous.id && next.versionLabel === '2.0',
    `La segunda versión no quedó vinculada como borrador: ${JSON.stringify({status:next.status, previousResourceId:next.previousResourceId, versionLabel:next.versionLabel})}`);
  await page.waitForFunction(() => document.querySelector('#download-list')?.textContent.includes('datos-dos.csv'));
  const listing = await page.$eval('#download-list', node => node.textContent);
  assert(!listing.includes('datos-uno.csv'), 'La versión nueva heredó archivos sin confirmación del autor.');
  const original = await request('GET', `/api/v1/scientific/${previous.id}`);
  assert(original.status === 'PUBLISHED', 'Crear la nueva versión alteró la publicación anterior.');
  assert(next.conceptualDoi === original.conceptualDoi,
    'La nueva versión no conservó el DOI conceptual del conjunto.');
  return id;
}
async function publicDownloads(base, resource) {
  const metricsResponse=await fetch(base+'/api/v1/public/metrics');
  assert(metricsResponse.ok,'Métricas públicas no accesibles.');
  assert(metricsResponse.headers.get('cache-control').includes('no-store'),'Inventario almacenado en caché.');
  const metrics=await metricsResponse.json();
  assert(metrics.publishedVersions>=1 && metrics.definitions.publishedVersions && metrics.scope.includes('COUNTER'), 'Inventario sin datos/definiciones.');
  const help = await fetch(base+'/help.html');
  assert(help.status===200,'La guía de ayuda debe ser pública, sin sesión.');
  const helpHtml = await help.text();
  assert(helpHtml.includes('Citar una versión') && helpHtml.includes('pendiente de aprobación/publicación'),
    'Guía incompleta o política institucional presentada como aprobada.');
  const context = await browser.createBrowserContext();
  try {
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.evaluateOnNewDocument(() => {
      window.showSaveFilePicker = async options => {
        const directory = await navigator.storage.getDirectory();
        window.directDownloadHandle = await directory.getFileHandle(options.suggestedName, {create:true});
        return window.directDownloadHandle;
      };
    });
    await page.goto(`${base}/public-resource.html?id=${encodeURIComponent(resource.id)}`, {waitUntil:'load'});
    await page.waitForSelector('#files .file-row a');
    assert(await page.evaluate(() => localStorage.getItem('base-repo-token')) === null,
      'La prueba pública heredó una sesión autenticada.');
    assert(await page.evaluate(()=>document.querySelector('#landing').textContent.includes('Translated scientific title') && document.querySelector('#identity').textContent.includes('Cuba') && document.querySelector('#identity').textContent.includes('Resumen estructurado') && document.querySelector('#scientific-provenance').textContent.includes('Limpieza científica declarada')), 'Ficha pública sin resumen/cobertura/traducción.');
    assert(await page.evaluate(()=>document.querySelector('#identity').textContent.includes('Última actualización')), 'Ficha sin última actualización.');
    await page.evaluate(()=>{
      const original=URL.createObjectURL;window.originalCreateObjectURL=original;
      URL.createObjectURL=blob=>{blob.text().then(value=>window.exportedPublicMetadata=JSON.parse(value));return original(blob);};
      Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:async value=>{window.copiedPermanentLink=value;}}});
    });
    await page.click('#share-resource');
    assert(await page.evaluate(id=>window.copiedPermanentLink.endsWith('/datasets/'+id),resource.id),'Enlace compartido no permanente.');
    await page.click('#export-metadata');
    await page.waitForFunction(()=>window.exportedPublicMetadata);
    const exported=await page.evaluate(()=>window.exportedPublicMetadata);
    assert(exported.schema==='reduniv.public-metadata.v1' && exported.metadata.id===resource.id && exported.metadata.lastUpdate,'Exportación general incompleta.');
    await page.evaluate(()=>{URL.createObjectURL=window.originalCreateObjectURL;});
    const fileResponse = page.waitForResponse(response => response.url().includes('/file?path='));
    await page.click('#files .file-row a');
    const file = await fileResponse;
    assert(file.ok(), 'Descarga pública individual rechazada.');
    assert((await file.text()).includes('nombre,valor'), 'Contenido público individual incorrecto.');
    await page.waitForFunction(() => document.querySelector('.transfer-list')?.textContent.includes('Descarga preparada'));
    const archiveResponse = page.waitForResponse(response => response.url().endsWith('/archive'));
    await page.click('#download-archive');
    const archive = await archiveResponse;
    assert(archive.ok(), 'ZIP público rechazado.');
    const zip = path.join(temp, 'public-download.zip');
    fs.writeFileSync(zip, await archive.buffer());
    const inspected = spawnSync('python3', ['-c',
      "import sys,zipfile; z=zipfile.ZipFile(sys.argv[1]); assert z.testzip() is None; names=z.namelist(); assert any(n.endswith('description.md') for n in names), names; assert any(n.endswith('datos-uno.csv') for n in names), names",
      zip], {encoding:'utf8'});
    assert(inspected.status === 0, `ZIP público incompleto: ${inspected.stderr}`);
    await page.waitForFunction(() => [...document.querySelectorAll('.transfer-item span')].filter(node => node.textContent === 'Descarga preparada').length === 2);
    await page.select('#format', 'bibtex');
    const citationResponse = page.waitForResponse(response => response.url().includes('/citation?format=bibtex'));
    await page.click('#export');
    const citation = await citationResponse;
    assert(citation.ok() && (await citation.text()).includes('@'), 'Exportación pública BibTeX inválida.');
    await page.waitForFunction(() => [...document.querySelectorAll('.transfer-item span')].filter(node => node.textContent === 'Descarga preparada').length === 3);
    await page.click('.transfer-toggle');
    await page.click('#transfer-disk-mode');
    await page.click('.transfer-close');
    await page.click('#files .file-row a');
    await page.waitForFunction(()=>document.querySelector('.transfer-item span')?.textContent==='Archivo guardado' && document.querySelector('#status')?.textContent==='Archivo guardado.', {timeout:5000}).catch(async error => {throw new Error(error.message+': '+await page.evaluate(()=>JSON.stringify({status:document.querySelector('#status')?.textContent,monitor:document.querySelector('.transfer-list')?.textContent,disk:document.querySelector('#transfer-disk-mode')?.checked})));});
    const savedCsv = await page.evaluate(async () => (await directDownloadHandle.getFile()).text());
    assert(savedCsv === await file.text(),'El guardado directo no conserva los bytes del CSV público.');
    assert(await page.evaluate(()=>localStorage.getItem('base-repo-token'))===null,
      'El guardado directo público requirió sesión.');
    assert(errors.length === 0, `Errores JavaScript públicos: ${errors.join('; ')}`);
    process.stdout.write('Descargas públicas anónimas OK: CSV, ZIP con descripción, BibTeX y CSV directo a disco (OPFS).\n');
  } finally { await context.close(); }
}
async function privacyFlow(page,base,draft) {
  const token=await page.evaluate(()=>localStorage.getItem('base-repo-token'));
  const headers={Authorization:`Bearer ${token}`,'Content-Type':'application/json'};
  await page.goto(base+'/resource.html?id='+draft.id,{waitUntil:'load'});
  await page.waitForSelector('#edit-privacy:not([hidden])');
  await page.click('#edit-privacy');await page.select('#privacy-form select','PERSONAL');
  await page.type('#privacy-form textarea','PRIVATE_ASSESSMENT_SENTINEL protection measures');
  const declaration=page.waitForResponse(r=>r.request().method()==='PUT'&&r.url().endsWith('/privacy'));
  await page.click('#privacy-form [type=submit]');assert((await declaration).ok(),'Modal de declaración rechazado.');
  const policy=await (await fetch(base+'/api/v1/scientific/privacy-policy',{headers})).json();assert(typeof policy.required==='boolean','Política de privacidad inválida.');
  const science=await (await fetch(base+'/api/v1/scientific/'+draft.id,{headers})).json();
  const restricted=await fetch(base+'/api/v1/scientific/'+draft.id,{method:'PUT',headers,body:JSON.stringify({...science,accessLevel:'RESTRICTED',versionDoi:`10.99999/privacy-${Date.now()}`})});
  assert(restricted.ok,'No se pudo restringir el depósito de prueba.');
  assert((await fetch(base+'/api/v1/scientific/'+draft.id+'/submit',{method:'POST',headers})).ok,'No se pudo enviar evaluación a curación.');
  assert((await fetch(base+'/api/v1/scientific/'+draft.id+'/publish',{method:'POST',headers,body:'{"doiRegisteredExternally":true}'})).status===409,'Publicó datos sensibles sin aprobación.');
  if(verifiedAccount)assert((await fetch(base+'/api/v1/scientific/'+draft.id+'/privacy',{headers:{Authorization:`Bearer ${verifiedAccount.token}`}})).status===404,'Usuario ajeno leyó notas privadas.');
  await page.goto(base+'/reviews.html',{waitUntil:'load'});
  await page.waitForFunction(id=>[...document.querySelectorAll('.review-card')].some(card=>card.querySelector('h2').textContent===id),{},draft.id);
  await page.evaluate(id=>{const card=[...document.querySelectorAll('.review-card')].find(c=>c.querySelector('h2').textContent===id);[...card.querySelectorAll('button')].find(b=>b.textContent==='Revisar metadatos y archivos').click();},draft.id);
  await page.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent==='Revisar privacidad'));
  await page.evaluate(()=>[...document.querySelectorAll('button')].find(b=>b.textContent==='Revisar privacidad').click());
  await page.select('#privacy-review-form select','APPROVED');await page.type('#privacy-review-form textarea','PRIVATE_REVIEW_SENTINEL checked access.');
  const approval=page.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/privacy/review'));
  await page.click('#privacy-review-form [type=submit]');assert((await approval).ok(),'Modal de revisión de privacidad rechazado.');
  assert((await fetch(base+'/api/v1/scientific/'+draft.id+'/publish',{method:'POST',headers,body:'{"doiRegisteredExternally":true}'})).ok,'No publicó después de aprobación privada y restricción.');
  const publicMetadata=await fetch(base+'/api/v1/public/resources/'+draft.id);assert(publicMetadata.ok,'Metadatos restringidos no consultables.');
  const body=await publicMetadata.text();assert(!body.includes('PRIVATE_ASSESSMENT_SENTINEL')&&!body.includes('PRIVATE_REVIEW_SENTINEL')&&!body.includes('assessmentNote'),'Notas privadas filtradas en ficha pública.');
  assert(!(await fetch(base+'/api/v1/public/resources/'+draft.id+'/file?path=datos-uno.csv')).ok,'Archivo sensible descargable anónimamente.');
  process.stdout.write('Privacidad OK: declaración/revisión modal, aprobación obligatoria, notas privadas y archivos restringidos.\n');
}
async function collectionsFlow(page, base, published, draft) {
  const token=await page.evaluate(()=>localStorage.getItem('base-repo-token'));
  const headers={Authorization:`Bearer ${token}`,'Content-Type':'application/json'};
  await page.goto(base+'/collections.html?manage=true',{waitUntil:'load'});
  await page.waitForFunction(()=>!document.querySelector('#new-collection').hidden);
  await page.click('#new-collection');
  await page.type('#collection-form [name=title]','Colección E2E '+Date.now());
  await page.type('#collection-form [name=description]','Agrupación real local');
  const creation=page.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/api/v1/collections'));
  await page.click('#collection-form [type=submit]');
  const response=await creation;assert(response.status()===201,'No se pudo crear colección por formulario.');
  const collection=await response.json();
  assert((await fetch(base+'/api/v1/public/collections/'+collection.id)).status===404,'Colección privada expuesta.');
  await page.goto(base+'/collections.html?manage=true&id='+collection.id,{waitUntil:'load'});
  await page.waitForFunction(()=>!document.querySelector('#member-form').hidden);
  await page.type('#member-form input',published.id);
  const membership=page.waitForResponse(r=>r.request().method()==='PUT'&&r.url().includes('/datasets/'));
  await page.click('#member-form button');
  assert((await membership).status()===204,'No se pudo agregar miembro por formulario.');
  assert((await fetch(base+'/api/v1/collections/'+collection.id+'/datasets/'+draft.id,{method:'PUT',headers})).status===204,'No se pudo agregar borrador.');
  // Repeated membership must remain a single reference, not a copy.
  assert((await fetch(base+'/api/v1/collections/'+collection.id+'/datasets/'+published.id,{method:'PUT',headers})).status===204,'Membresía no idempotente.');
  await page.reload({waitUntil:'load'});
  await page.waitForFunction(()=>!document.querySelector('#edit-collection').disabled);
  await page.click('#edit-collection');await page.click('#collection-form [name=published]');
  const updated=page.waitForResponse(r=>r.request().method()==='PUT'&&r.url().endsWith('/api/v1/collections/'+collection.id));
  await page.click('#collection-form [type=submit]');assert((await updated).ok(),'No se pudo publicar colección.');
  const stale=await fetch(base+'/api/v1/collections/'+collection.id,{method:'PUT',headers,body:JSON.stringify({...collection,title:'Edición obsoleta'})});
  assert(stale.status===409,'Edición obsoleta no fue rechazada.');
  const publicPage=await (await fetch(base+'/api/v1/public/collections/'+collection.id+'?size=1')).json();
  assert(publicPage.total===1&&publicPage.items[0].id===published.id,'Borrador expuesto o miembro duplicado en colección pública.');
  const anonymous=await browser.createBrowserContext();
  try {
    const visitor=await anonymous.newPage();
    await visitor.goto(base+'/collections.html?id='+collection.id,{waitUntil:'load'});
    await visitor.waitForSelector('#collection-results .resource-card');
    assert((await visitor.$$eval('#collection-results .resource-card',items=>items.length))===1,'Vista pública muestra miembros privados.');
    assert(await visitor.$eval('#management',el=>el.hidden),'Visitante recibe controles de gestión.');
  } finally {await anonymous.close();}
  assert((await fetch(base+'/api/v1/collections/'+collection.id+'/datasets/'+published.id,{method:'DELETE',headers})).status===204,'No se pudo quitar miembro.');
  assert((await fetch(base+'/api/v1/public/resources/'+published.id)).ok,'Quitar miembro eliminó dataset.');
  await page.reload({waitUntil:'load'});await page.waitForFunction(()=>!document.querySelector('#delete-collection').disabled);
  await page.click('#delete-collection');await page.waitForSelector('#collection-confirm[open]');
  const removal=page.waitForResponse(r=>r.request().method()==='DELETE'&&r.url().endsWith('/api/v1/collections/'+collection.id));
  await page.click('#confirm-action');assert((await removal).status()===204,'Eliminar colección falló.');
  assert((await fetch(base+'/api/v1/public/resources/'+published.id)).ok,'Eliminar colección eliminó dataset.');
  assert((await fetch(base+'/api/v1/public/collections/'+collection.id)).status===404,'Colección eliminada sigue pública.');
  process.stdout.write('Colecciones OK: formularios, privado/público, membresía única, borradores ocultos y eliminación sin borrar datasets.\n');
}
async function verifyMultiuserAccess(page, base, published, otherDraft) {
  if (!verifiedAccount) return;
  let userHeaders={Authorization:`Bearer ${verifiedAccount.token}`};
  const adminTasksToken=await page.evaluate(()=>localStorage.getItem('base-repo-token'));
  const taskResponse=await fetch(base+'/api/v1/my-deposit-tasks?page=0&size=1',{headers:{Authorization:`Bearer ${adminTasksToken}`}});
  assert(taskResponse.ok,'No se pudieron consultar tareas del autor.');const tasks=await taskResponse.json();assert(tasks.total>1&&tasks.items.length===1&&tasks.pages===tasks.total,'Paginación de tareas inválida.');
  assert(tasks.items.every(item=>['DRAFT','IN_REVIEW'].includes(item.status)&&item.resourceId!==published.id),'Tareas incluye publicado.');
  const otherTasks=await fetch(base+'/api/v1/my-deposit-tasks',{headers:userHeaders});assert(otherTasks.ok&&(await otherTasks.json()).total===0,'Usuario ajeno recibió tareas privadas.');
  assert((await fetch(base+'/api/v1/my-deposit-tasks')).status===401,'Tareas accesibles anónimamente.');
  await page.goto(base+'/my-datasets.html',{waitUntil:'load'});await page.waitForFunction(()=>document.querySelector('#task-list article'));
  assert(await page.$eval('#task-status',n=>n.textContent.includes('depósitos activos')),'Vista no informa tareas activas.');
  process.stdout.write('Tareas OK: listado del autor, checklist, paginación y ausencia de datos ajenos.\n');
  assert((await fetch(base+'/api/v1/scientific/preservation/storage',{headers:userHeaders})).status===403,'Usuario normal consultó volumen de servidor.');
  assert((await fetch(base+'/api/v1/scientific/preservation/storage')).status===401,'Estado de volumen accesible anónimamente.');
  const storageResponse=await fetch(base+'/api/v1/scientific/preservation/storage',{headers:{Authorization:`Bearer ${adminTasksToken}`}});
  assert(storageResponse.ok&&storageResponse.headers.get('cache-control').includes('no-store'),'Estado operativo no protegido de caché.');
  const volume=await storageResponse.json();assert(volume.status==='AVAILABLE'&&volume.totalBytes>0&&volume.usableBytes>=0&&!JSON.stringify(volume).includes('/tmp/'),'Medición de volumen inválida o ruta expuesta.');
  await page.goto(base+'/operations.html',{waitUntil:'load'});await page.waitForSelector('#export-operations:not([disabled])');
  assert(await page.$eval('#storage-summary',n=>n.textContent.includes('Disponible para la aplicación')),'Panel no informa capacidad.');
  await page.evaluate(()=>{window.originalOperationsObjectURL=URL.createObjectURL;URL.createObjectURL=blob=>{blob.text().then(value=>window.exportedOperations=JSON.parse(value));return window.originalOperationsObjectURL(blob);};});
  await page.click('#export-operations');await page.waitForFunction(()=>window.exportedOperations);
  const operationalReport=await page.evaluate(()=>window.exportedOperations);assert(operationalReport.schema==='reduniv-operational-report/1'&&operationalReport.measuredAt&&operationalReport.storage.status==='AVAILABLE'&&Array.isArray(operationalReport.audits),'Informe exportado incompleto.');
  await page.evaluate(()=>{URL.createObjectURL=window.originalOperationsObjectURL;});
  process.stdout.write('Operaciones OK: volumen local, informe JSON y autorización/caché.\n');


  assert((await fetch(base+'/api/v1/collections',{headers:userHeaders})).status===403,'Usuario normal puede gestionar colecciones.');
  assert((await fetch(base+'/api/v1/collections',{method:'POST',headers:{...userHeaders,'Content-Type':'application/json'},body:JSON.stringify({title:'No autorizado',kind:'THEMATIC',published:true})})).status===403,'Usuario normal puede crear colecciones.');
  assert((await fetch(base+'/api/v1/scientific/'+published.id+'/doi/landing-targets',{headers:userHeaders})).status===403,'Usuario accedió a mantenimiento DOI administrativo.');
  assert((await fetch(base+'/api/v1/scientific/'+published.id+'/doi/refresh-urls',{method:'POST',headers:{...userHeaders,'Content-Type':'application/json'},body:'{}'})).status===403,'Usuario pudo actualizar URLs DOI.');
  const catalogue=await fetch(base+'/api/v1/catalog?q='+encodeURIComponent(published.title), {headers:userHeaders});
  assert(catalogue.ok && (await catalogue.json()).items.some(item=>item.id===published.id),
    'El usuario verificado no ve el dataset compartido de otro autor.');
  const adminPage=await fetch(base+'/api/v1/users',{headers:userHeaders});
  assert(adminPage.status===403, `Gestión administrativa devolvió ${adminPage.status}: ${await adminPage.text()}`);
  const edit=await fetch(`${base}/api/v1/scientific/${otherDraft}`,{
    method:'PUT',headers:{...userHeaders,'Content-Type':'application/json'},body:JSON.stringify({versionLabel:'unauthorized-change'})
  });
  assert(edit.status===403, 'Un Usuario editó un depósito ajeno.');
  const nextPassword=crypto.randomBytes(16).toString('hex');
  const changed=await fetch(base+'/api/v1/auth/change-password',{
    method:'POST',headers:{...userHeaders,'Content-Type':'application/json'},
    body:JSON.stringify({currentPassword:verifiedAccount.password,newPassword:nextPassword})
  });
  assert(changed.ok,'El usuario no pudo cambiar su propia contraseña.');
  assert((await fetch(base+'/api/v1/catalog',{headers:userHeaders})).status===401,
    'El token antiguo sobrevivió al cambio de contraseña.');
  const freshLogin=await fetch(base+'/api/v1/auth/login',{
    method:'POST',headers:{'Content-Type':'application/json'},
    body:JSON.stringify({username:verifiedAccount.user.username,password:nextPassword})
  });
  assert(freshLogin.ok,'No se pudo iniciar sesión tras cambiar contraseña.');
  const fresh=await freshLogin.json();
  verifiedAccount.token=fresh.token; verifiedAccount.password=nextPassword;
  userHeaders={Authorization:`Bearer ${fresh.token}`};
  assert((await fetch(base+'/api/v1/catalog',{headers:userHeaders})).ok,
    'La nueva sesión fue rechazada tras el cambio de contraseña.');
  const adminToken=await page.evaluate(()=>localStorage.getItem('base-repo-token'));
  const suspended=await fetch(`${base}/api/v1/users/${verifiedAccount.user.id}`, {
    method:'PUT',headers:{Authorization:`Bearer ${adminToken}`,'Content-Type':'application/json'},
    body:JSON.stringify({username:verifiedAccount.user.username,role:'USER',enabled:false})
  });
  assert(suspended.ok, 'No se pudo suspender la cuenta de prueba.');
  const revoked=await fetch(base+'/api/v1/catalog',{headers:userHeaders});
  assert(revoked.status===401, 'El token emitido antes de suspender conserva acceso.');
  const loginResponse=await fetch(base+'/api/v1/auth/login',{
    method:'POST',headers:{'Content-Type':'application/json'},
    body:JSON.stringify({username:verifiedAccount.user.username,password:verifiedAccount.password})
  });
  assert(loginResponse.status===403 && (await loginResponse.json()).code==='ACCOUNT_RESTRICTED',
    'Login no informa cuenta restringida.');
  process.stdout.write('Multiusuario OK: catálogo compartido, administración/edición denegadas y token bloqueado al suspender.\n');
}
async function restorePostgresFixture(page, base, files, published) {
  if (!postgres || process.env.E2E_POSTGRES_RESTORE !== '1') return;
  await stopApp();
  const args = ['-h','127.0.0.1','-p',String(postgres.port),'-U','e2e_admin'];
  function pg(command, extra) {
    const result = spawnSync(path.join(postgres.bin, command), [...args,...extra],
      {encoding:'utf8',env:{...process.env,PGPASSWORD:postgres.secret}});
    assert(result.status === 0, `Ensayo PostgreSQL ${command} falló: ${result.stderr}`);
    return result;
  }
  function tree(directory, prefix='') {
    const result = {};
    for (const entry of fs.readdirSync(directory,{withFileTypes:true})) {
      const relative = prefix + entry.name, target = path.join(directory,entry.name);
      if (entry.isDirectory()) Object.assign(result,tree(target,relative+'/'));
      else if (entry.isFile()) result[relative]=crypto.createHash('sha256').update(fs.readFileSync(target)).digest('hex');
      else throw new Error('El fixture de restauración contiene enlace o archivo no regular.');
    }
    return result;
  }
  const baseline = tree(files.data);
  const migration = path.join(root,'docs/migrations/2026-09-scientific-records.sql');
  for (let i=0;i<2;i++) pg('psql',['-X','-v','ON_ERROR_STOP=1','-d',postgres.database,'-f',migration]);
  function dataFingerprints(database) {
    const tables = pg('psql',['-X','-At','-d',database,'-c',
      "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename"]).stdout.trim().split('\n').filter(Boolean);
    const result = {};
    for (const table of tables) {
      const quoted = '"' + table.replaceAll('"','""') + '"';
      const query = `SELECT count(*),md5(coalesce(string_agg(row_to_json(t)::text, E'\\n' ORDER BY row_to_json(t)::text),'')) FROM public.${quoted} t`;
      result[table] = pg('psql',['-X','-At','-d',database,'-c',query]).stdout.trim();
    }
    return result;
  }
  const originalData = dataFingerprints(postgres.database);
  const dump = path.join(temp,'fixture.dump');
  pg('pg_dump',['-Fc','--no-owner','--no-acl','-d',postgres.database,'-f',dump]);
  pg('createdb',['reduniv_restored']);
  pg('pg_restore',['--no-owner','--no-acl','--exit-on-error','-d','reduniv_restored',dump]);
  assert(JSON.stringify(dataFingerprints('reduniv_restored')) === JSON.stringify(originalData),
    'La restauración alteró filas o perdió tablas del fixture.');
  const inventory = pg('psql',['-X','-d','reduniv_restored','-c',
    'COPY (SELECT id, parent_resource_id, relative_path, content_uri FROM content_information ORDER BY id) TO STDOUT WITH CSV HEADER']).stdout;
  const pathAudit = spawnSync('python3', [path.join(root,'tools/storage/audit_content_paths.py'),
    '--basepath',files.data,'--strict'], {encoding:'utf8',input:inventory});
  assert(pathAudit.status === 0, `Inventario de rutas restauradas detectó anomalías: ${pathAudit.stdout} ${pathAudit.stderr}`);
  assert(!pathAudit.stdout.includes('Registros: 0.'), 'El inventario restaurado estaba vacío.');
  process.stdout.write('Inventario de contentUri PostgreSQL restaurado OK (sin modificar datos).\n');
  postgres.database='reduniv_restored'; postgres.restored=true;
  await startApp(new URL(base).port,files);
  const staleToken = await page.evaluate(() => localStorage.getItem('base-repo-token'));
  assert(staleToken, 'Falta la sesión antigua para probar recuperación tras reinicio.');
  await login(page,base);
  assert(await page.evaluate(() => localStorage.getItem('base-repo-token')) !== staleToken,
    'El nuevo login no sustituyó la sesión inválida después del reinicio.');
  const response = await fetch(`${base}/api/v1/public/resources/${encodeURIComponent(published.id)}`);
  assert(response.ok, 'El recurso publicado no sobrevivió a la restauración.');
  const restored=await response.json();
  assert(restored.processingDescription==='Limpieza científica declarada de valores ausentes.'&&restored.processingTools==='Python 3.12; script público v1.','Restauración perdió procedencia científica.');
  assert(restored.title===published.title && restored.translations.en.title==='Translated scientific title' && restored.temporalStart==='2025-01-01','La restauración alteró título/traducciones/cobertura.');
  const file = await fetch(`${base}/api/v1/public/resources/${encodeURIComponent(published.id)}/file?path=datos-uno.csv`);
  assert(file.ok && (await file.text()).includes('nombre,valor'), 'Los archivos no son accesibles tras restaurar.');
  assert(JSON.stringify(tree(files.data))===JSON.stringify(baseline), 'El ensayo alteró los archivos del depósito.');
  process.stdout.write('Restauración PostgreSQL sintética OK: migración doble, datos idénticos, esquema validado y descarga.\n');
}
async function stopApp() {
  if (app && app.exitCode === null && app.signalCode === null) {
    app.kill('SIGTERM');
    await Promise.race([new Promise(resolve => app.once('exit', resolve)), sleep(5000)]);
    if (app.exitCode === null && app.signalCode === null) {
      app.kill('SIGKILL');
      await new Promise(resolve => app.once('exit', resolve));
    }
  }
}
async function cleanup() {
  if (browser) await browser.close().catch(() => {});
  await stopApp();
  if (postgres) {
    const stopped = spawnSync(path.join(postgres.bin, 'pg_ctl'), ['-D', postgres.data, '-m', 'immediate', '-w', 'stop'], {encoding:'utf8'});
    assert(stopped.status === 0, 'No se pudo detener el PostgreSQL efímero; revise el directorio temporal.');
  }
  if (smtp) await new Promise(resolve => smtp.close(resolve));
  fs.rmSync(temp, {recursive:true, force:true});
}
async function main() {
  try {
    const files = setupFiles();
    const port = await freePort();
    await startApp(port, files);
    await verificationFlow(`http://127.0.0.1:${port}`);
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
    await rejectPackage(page, base, packaged.id, files.fullTraversalZip, 'ruta no válida');
    await rejectDescription(page, base, packaged.id, files.descriptionTraversalZip, 'ruta no permitida');
    await rejectDescription(page, base, packaged.id, files.descriptionDuplicateZip, 'nombres repetidos');
    await rejectDescription(page, base, packaged.id, files.descriptionHtmlZip, 'solo admite description.md e imágenes');
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
    await page.goto(base + '/reviews.html', {waitUntil:'load'});
    await page.waitForFunction(() => [...document.querySelectorAll('#reviews-list button')].some(button => button.textContent === 'Revisar metadatos y archivos'));
    await page.evaluate(() => [...document.querySelectorAll('#reviews-list button')].find(button => button.textContent === 'Revisar metadatos y archivos').click());
    await page.waitForFunction(() => document.querySelector('#reviews-list').textContent.includes('Requisitos automáticos completos.'));
    await page.evaluate(() => [...document.querySelectorAll('.review-files button')].find(button => button.textContent === 'Descargar').click());
    await page.waitForFunction(() => document.querySelector('.transfer-list')?.textContent.includes('Descarga preparada'));
    const preservationResponse = page.waitForResponse(response => response.url().includes('/preservation/') && response.url().endsWith('/package'));
    await page.evaluate(() => [...document.querySelectorAll('#reviews-list button')].find(button => button.textContent === 'Descargar paquete de preservación').click());
    const preservation = await preservationResponse;
    assert(preservation.ok(), 'El paquete de preservación fue rechazado.');
    const preservationZip = path.join(temp, 'preservation-download.zip');
    fs.writeFileSync(preservationZip, await preservation.buffer());
    const verifiedPackage = spawnSync('python3', ['-c', [
      'import sys,zipfile,json,hashlib',
      'z=zipfile.ZipFile(sys.argv[1]); assert z.testzip() is None',
      "names=z.namelist(); assert 'data/datos-uno.csv' in names; assert any(n.endswith('description.md') for n in names)",
      "assert 'BagIt-Version: 1.0' in z.read('bagit.txt').decode()",
      "for name in ['preservation/metadata.json','preservation/provenance.json','preservation/prov.jsonld','ro-crate-metadata.json']: json.loads(z.read(name))",
      "prov=json.loads(z.read('preservation/prov.jsonld')); declaration=next(n for n in prov['@graph'] if n['@id']=='#scientific-provenance'); assert 'Limpieza científica declarada' in declaration['schema:description'] and 'prov:wasGeneratedBy' not in declaration",
      "crate=json.loads(z.read('ro-crate-metadata.json')); assert any(n.get('@id')=='#scientific-provenance' and 'Python 3.12' in n['description'] for n in crate['@graph'])",
      "for name in ['manifest-sha256.txt','tagmanifest-sha256.txt']:",
      " for line in z.read(name).decode().splitlines():",
      "  if line.strip():",
      "   digest,entry=line.split('  ',1); assert hashlib.sha256(z.read(entry)).hexdigest()==digest, entry"
    ].join('\n'), preservationZip], {encoding:'utf8'});
    assert(verifiedPackage.status === 0, `Paquete preservación inválido: ${verifiedPackage.stderr}`);
    await page.waitForFunction(() => [...document.querySelectorAll('.transfer-item span')].filter(node => node.textContent === 'Descarga preparada').length === 2);
    process.stdout.write('Curación OK: vista previa, archivo y paquete de preservación con manifiestos verificados.\n');
    const reviewerLink = await page.evaluate(async id => {
      const response = await fetch(`/api/v1/scientific/${encodeURIComponent(id)}/review-links`, {
        method:'POST', headers:{Authorization:`Bearer ${localStorage.getItem('base-repo-token')}`, 'Content-Type':'application/json'},
        body:JSON.stringify({hours:1})
      });
      if (!response.ok) throw new Error('No se pudo crear enlace local de revisión.');
      return await response.json();
    }, packaged.id);
    const reviewerContext = await browser.createBrowserContext();
    try {
      const reviewer = await reviewerContext.newPage();
      await reviewer.goto(new URL(reviewerLink.relativeUrl, base).href, {waitUntil:'load'});
      await reviewer.waitForSelector('#review-files button');
      assert(new URL(reviewer.url()).hash === '', 'El token permaneció en la URL del revisor.');
      assert(await reviewer.evaluate(() => localStorage.getItem('base-repo-token')) === null, 'Revisor heredó autenticación.');
      const privateResponse = reviewer.waitForResponse(response => response.url().includes('/api/v1/reviewer/file?'));
      await reviewer.click('#review-files button');
      const privateFile = await privateResponse;
      assert(privateFile.ok(), 'El archivo privado fue rechazado antes de revocar.');
      assert((privateFile.headers()['cache-control'] || '').includes('no-store'), 'El archivo privado admite caché.');
      await reviewer.waitForFunction(() => document.querySelector('.transfer-list')?.textContent.includes('Descarga preparada'));
      const revoked = await page.evaluate(async ({id,linkId}) => {
        const response = await fetch(`/api/v1/scientific/${encodeURIComponent(id)}/review-links/${linkId}`, {
          method:'DELETE', headers:{Authorization:`Bearer ${localStorage.getItem('base-repo-token')}`}
        });
        return response.status;
      }, {id:packaged.id,linkId:reviewerLink.id});
      assert(revoked === 204, 'No se pudo revocar el enlace privado.');
      const deniedResponse = reviewer.waitForResponse(response => response.url().includes('/api/v1/reviewer/file?'));
      await reviewer.click('#review-files button');
      const denied = await deniedResponse;
      assert(!denied.ok(), 'El enlace revocado todavía permite descargar.');
      assert((denied.headers()['cache-control'] || '').includes('no-store'), 'La respuesta de enlace revocado admite caché.');
      await reviewer.waitForFunction(() => document.querySelector('.transfer-item span')?.textContent === 'Error en la descarga');
      assert(await reviewer.$eval('#review-status', node => node.textContent) === 'No se pudo descargar el archivo.',
        'El rechazo del enlace no se informó al revisor.');
      process.stdout.write('Revisor externo local OK: enlace temporal, no-store, descarga y revocación efectiva.\n');
    } finally { await reviewerContext.close(); }
    const recovered = await recoverInterruptedUpload(page, base, files);
    const newVersionId = await deriveNewVersion(page, base, files, markdown);
    await privacyFlow(page,base,zipped);
    await publicDownloads(base, markdown);
    await collectionsFlow(page, base, markdown, packaged);
    await verifyMultiuserAccess(page,base,markdown,newVersionId);
    await restorePostgresFixture(page,base,files,markdown);
    assert(pageErrors.length === 0, `Errores JavaScript: ${pageErrors.join('; ')}`);
    process.stdout.write(`Asistente OK: ${markdown.title}; ${zipped.title}; ${packaged.title}; ${recovered}; nueva versión ${newVersionId}.\n`);
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
