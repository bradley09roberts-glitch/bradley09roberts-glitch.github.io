const { chromium } = require('playwright');
(async () => {
  const [file, outDir, ...routes] = process.argv.slice(2);
  const browser = await chromium.launch();
  for (const [vw, tag] of [[1280, 'd'], [390, 'm']]) {
    const page = await browser.newPage({ viewport: { width: vw, height: 900 }, deviceScaleFactor: 1 });
    const errs = [];
    page.on('console', m => { if (m.type() === 'error') errs.push(m.text()); });
    page.on('pageerror', e => errs.push('PAGEERROR ' + e.message));
    for (const r of routes) {
      const [route, click] = r.split('|');
      await page.goto((/^https?:/.test(file) ? file : 'file://' + file) + '#' + route, { waitUntil: 'load', timeout: 120000 });
      await page.waitForTimeout(1500);
      if (click) { await page.click(click).catch(e => errs.push('CLICK ' + click + ' ' + e.message)); await page.waitForTimeout(800); }
      const name = `${outDir}/${tag}_${route.replace(/[^a-z0-9]+/gi, '_').slice(0, 60)}${click ? '_t' : ''}.png`;
      await page.screenshot({ path: name, fullPage: true });
      const txt = await page.evaluate(() => document.querySelector('#view').innerText.slice(0, 300));
      console.log(tag, route, click || '', '->', name, '|', txt.replace(/\s+/g, ' ').slice(0, 160));
    }
    console.log(tag, 'errors:', errs.length ? errs.slice(0, 10).join('\n  ') : 'none');
    await page.close();
  }
  await browser.close();
})();
