#!/usr/bin/env node
/* Isolated UI contract: real markup/shared scripts, no real users or outbound mail. */
const http=require('node:http'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const puppeteer=require('../a11y/node_modules/puppeteer-core');
const root=path.resolve(__dirname,'../../src/main/resources/static');
const shared=new Set(['auth.js','ui-i18n.js','ui-locales.js']);
const server=http.createServer((req,res)=>{
 const pathname=new URL(req.url,'http://localhost').pathname;
 if(pathname==='/api/v1/public/mail-delivery'){res.setHeader('Content-Type','application/json');res.end(JSON.stringify({mode:'LOCAL_CAPTURE',previewUrl:'http://localhost:8025/'}));return;}
 const name=decodeURIComponent(pathname.slice(1)),file=path.resolve(root,name);
 if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}
 res.setHeader('Content-Type',name.endsWith('.html')?'text/html':name.endsWith('.js')?'application/javascript':name.endsWith('.css')?'text/css':'image/png');
 res.end(name.endsWith('.js')&&!shared.has(name)?'':fs.readFileSync(file));
});
(async()=>{let browser;try{
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const base=`http://127.0.0.1:${server.address().port}`;
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const page=await browser.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
 const sections=['index.html','my-datasets.html','account.html','create.html','resource.html','reviews.html','users.html','operations.html','vocabulary-admin.html','metadata-profiles-admin.html','collections.html?manage=true','self-assessment.html'];
 for(const role of ['USER','CURATOR','ADMINISTRATOR']){
  await page.goto(base+'/login.html');await page.evaluate(role=>{localStorage.setItem('base-repo-token','isolated-fixture');localStorage.setItem('base-repo-user',JSON.stringify({username:'fixture',role}));},role);
  let baseline;
  for(const section of sections){await page.goto(base+'/'+section);const menu=await page.$$eval('.sidebar nav a',links=>links.filter(x=>!x.hidden).map(x=>x.getAttribute('href')));baseline??=menu;assert.deepEqual(menu,baseline,role+' menu changed on '+section);
   assert.equal(await page.$$eval('.sidebar [data-ui-locale]',xs=>xs.length),1);
   assert(await page.$eval('.sidebar',el=>{const children=[...el.children];return children.indexOf(el.querySelector('.brand'))<children.indexOf(el.querySelector('.sidebar-locale'))&&children.indexOf(el.querySelector('.sidebar-locale'))<children.indexOf(el.querySelector('nav'));}));
   const active = ['resource.html','create.html'].includes(section)?'my-datasets.html':section.split('?')[0];
   assert.equal(await page.$$eval('.sidebar nav a[aria-current=page]:not([hidden])',xs=>xs.length),baseline.some(href=>href.split('?')[0]===active)?1:0,role+' active '+section);
   await page.select('[data-ui-locale]','en');assert.equal(await page.$eval('.sidebar nav a',x=>x.textContent),'▦ Catalogue');
  }
  assert.equal(baseline.includes('users.html'),role==='ADMINISTRATOR');assert.equal(baseline.includes('operations.html'),role!=='USER');
 }
 await page.goto(base+'/register.html');await page.waitForSelector('.mail-delivery-notice a');assert.equal(await page.$eval('.mail-delivery-notice a',x=>x.href),'http://localhost:8025/');
 await page.type('[name=password]','synthetic-fixture-only');await page.click('.password-toggle');assert.equal(await page.$eval('[name=password]',x=>x.type),'text');assert.equal(await page.$eval('.password-toggle',x=>x.getAttribute('aria-pressed')),'true');
 await page.select('[data-ui-locale]','es');assert.equal(await page.$eval('[name=password]',x=>x.value),'synthetic-fixture-only');await page.click('.password-toggle');assert.equal(await page.$eval('[name=password]',x=>x.type),'password');
 assert.equal(await page.$eval('#register-form button[type=submit]',x=>x.type),'submit');
 await page.goto(base+'/verify.html');await page.waitForSelector('.mail-delivery-notice');
 await page.setViewport({width:320,height:800});await page.goto(base+'/account.html');assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 if(process.env.E2E_SCREENSHOT_PATH){await page.setViewport({width:1440,height:1000});await page.goto(base+'/index.html');await page.select('[data-ui-locale]','es');await (await page.$('.sidebar')).screenshot({path:process.env.E2E_SCREENSHOT_PATH});}
 assert.deepEqual(errors,[]);console.log('Shared shell PASS: 36 role/section menus, language position/persistence, active link, password visibility and local-mail instructions; 320px no overflow.');
}finally{await browser?.close();await new Promise(resolve=>server.close(resolve));}})().catch(error=>{console.error(error);process.exitCode=1;});
