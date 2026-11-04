// Interactive checks of the fight review on the served test page.
const { chromium } = require('playwright');
(async () => {
  const base = process.argv[2];   // e.g. http://127.0.0.1:8765/test.html
  const browser = await chromium.launch();
  const out = [];
  const errs = [];
  const mk = async (opts) => { const c = await browser.newContext(opts); const p = await c.newPage(); p.on('console', m => { if (m.type() === 'error') errs.push(m.text()); }); p.on('pageerror', e => errs.push('PAGEERROR ' + e.message)); return [c, p]; };
  // 1. reduced motion: review tab visible
  let [c, p] = await mk({ viewport: { width: 1280, height: 900 }, reducedMotion: 'reduce' });
  await p.goto(base + '#fight-2026-10-31_6d68c1afe954f121_7f6ed67d3ed3c036', { waitUntil: 'load' });
  await p.waitForTimeout(2500);
  await p.click('#mt-review'); await p.waitForTimeout(800);
  out.push('reduced-motion opacity: ' + await p.evaluate(() => getComputedStyle(document.querySelector('#mu-panel > .mu-stack')).opacity));
  out.push('verdict: ' + await p.evaluate(() => (document.querySelector('.rv-vh') || {}).textContent));
  out.push('judges row: ' + await p.evaluate(() => { const dts = [...document.querySelectorAll('.rv-verdict dt')]; const d = dts.find(x => x.textContent.startsWith('Judges')); return d ? d.textContent + ' = ' + d.nextElementSibling.textContent : 'none'; }));
  // Nikolic vs Vologdin judges
  await p.goto(base + '#fight-2026-08-01_0e6b611451a9a7d8_5f926979c980b87e', { waitUntil: 'load' }); await p.waitForTimeout(1500);
  await p.click('#mt-review').catch(() => {}); await p.waitForTimeout(600);
  out.push('Nikolic judges: ' + await p.evaluate(() => { const dts = [...document.querySelectorAll('.rv-verdict dt')]; const d = dts.find(x => x.textContent.startsWith('Judges')); return d ? d.textContent + ' = ' + d.nextElementSibling.textContent : 'none'; }) + ' | verdict ' + await p.evaluate(() => (document.querySelector('.rv-vh') || {}).textContent));
  // 2. search typing on the review page
  await p.goto(base + '#review', { waitUntil: 'load' }); await p.waitForTimeout(2500);
  await p.click('#rv-q'); await p.keyboard.type('Osbourne', { delay: 200 }); await p.waitForTimeout(600);
  out.push('search value: ' + await p.inputValue('#rv-q') + ' | focus: ' + await p.evaluate(() => document.activeElement && document.activeElement.id) + ' | ' + await p.evaluate(() => document.querySelector('.rv-results .ev-count').textContent));
  await c.close();
  // 3. phone More sheet
  [c, p] = await mk({ viewport: { width: 390, height: 844 } });
  await p.goto(base + '#events', { waitUntil: 'load' }); await p.waitForTimeout(2000);
  await p.click('#tabbar [data-nav="more"]'); await p.waitForTimeout(600);
  out.push('phone More sheet: ' + await p.evaluate(() => [...document.querySelectorAll('.palette-item b')].map(x => x.textContent).join(' | ')));
  await c.close();
  await browser.close();
  console.log(out.join('\n')); console.log('errors:', errs.length ? errs.slice(0, 8).join('\n  ') : 'none');
})();
