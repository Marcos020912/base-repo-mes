const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const puppeteer = require('puppeteer-core');
const axe = require('axe-core');

const root = path.resolve(__dirname, '../../src/main/resources/static');
const browserPath = process.env.CHROME_BIN || '/usr/bin/google-chrome';
const tags = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'];
const mime = {'.html':'text/html; charset=utf-8', '.css':'text/css; charset=utf-8',
  '.png':'image/png', '.svg':'image/svg+xml', '.jpg':'image/jpeg', '.jpeg':'image/jpeg'};

const server = http.createServer((request, response) => {
  let filename;
  try { filename = path.resolve(root, '.' + decodeURIComponent(new URL(request.url, 'http://localhost').pathname)); }
  catch { response.writeHead(400).end(); return; }
  if (!filename.startsWith(root + path.sep) || !fs.existsSync(filename) || !fs.statSync(filename).isFile()) {
    response.writeHead(404).end(); return;
  }
  response.writeHead(200, {'Content-Type':mime[path.extname(filename)] || 'application/octet-stream'});
  fs.createReadStream(filename).pipe(response);
});

async function audit(page, label) {
  const report = await page.evaluate(options => axe.run(document, options), {runOnly:{type:'tag', values:tags}});
  for (const violation of report.violations) {
    console.error(`${label}: ${violation.id} (${violation.impact})`);
    for (const item of violation.nodes) console.error(`  ${item.target.join(' ')}`);
  }
  return report.violations.length;
}

async function main() {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}/`;
  const browser = await puppeteer.launch({executablePath:browserPath, headless:true,
    args:['--no-sandbox', '--disable-gpu']});
  let failures = 0, states = 0;
  try {
    const pages = fs.readdirSync(root).filter(name => name.endsWith('.html')).sort();
    for (const name of pages) {
      const page = await browser.newPage();
      try {
        await page.setViewport({width:1280, height:800});
        // Static audit intentionally avoids API requests, authentication redirects and real accounts.
        await page.setRequestInterception(true);
        page.on('request', request => request.url().endsWith('.js') ? request.abort() : request.continue());
        await page.goto(base + name, {waitUntil:'load'});
        await page.keyboard.press('Tab');
        const firstStop = await page.evaluate(() => document.activeElement.classList.contains('skip-link'));
        if (!firstStop) { console.error(`${name}: el primer Tab no alcanza el enlace para saltar al contenido`); failures++; }
        if (firstStop) {
          await page.keyboard.press('Enter');
          const skipTarget = await page.evaluate(() => document.activeElement.id === 'main-content');
          if (!skipTarget) { console.error(`${name}: el enlace no transfiere el foco al contenido`); failures++; }
        }
        await page.evaluate(axe.source);
        failures += await audit(page, name); states++;
        const variants = await page.evaluate(() => Array.from(document.querySelectorAll('dialog[id],section[data-step]'))
          .map(element => ({id:element.id || element.dataset.step, type:element.tagName, hidden:element.hidden})));
        for (const variant of variants) {
          if (variant.type === 'DIALOG') await page.evaluate(id => document.getElementById(id).showModal(), variant.id);
          else await page.evaluate(id => { document.querySelector(`section[data-step="${id}"]`).hidden = false; }, variant.id);
          failures += await audit(page, `${name}#${variant.id}`); states++;
          if (variant.type === 'DIALOG') await page.evaluate(id => document.getElementById(id).close(), variant.id);
          else await page.evaluate(({id,hidden}) => { document.querySelector(`section[data-step="${id}"]`).hidden = hidden; }, variant);
        }
        await page.setViewport({width:320, height:700});
        const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1);
        if (overflow) { console.error(`${name}@320: desbordamiento horizontal de la página`); failures++; }
        failures += await audit(page, `${name}@320`); states++;
      } finally { await page.close(); }
    }
  } finally { await browser.close(); server.close(); }
  console.log(`Axe: ${states} estados revisados, ${failures} infracciones automáticas.`);
  if (failures) process.exitCode = 1;
}

main().catch(error => { console.error(error); server.close(); process.exitCode = 1; });
