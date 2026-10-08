// End-to-end check of the screen reader in the real Electron app on a virtual display:
// a stand-in "game" window shows a SYNTHETIC scoreboard (scripts/make-test-scoreboard.ts), the
// app captures the real screen through desktopCapturer, the user calibration is performed with
// mouse drags, and the read is applied to the match. This is not a test against Deadlock.
//
// Usage (Linux): xvfb-run -a -s "-screen 0 1920x1080x24" node scripts/screen-e2e.cjs <outDir> <synthetic-scoreboard.png>
const { _electron: electron } = require("/opt/node-tools/node_modules/playwright");
const { execFileSync } = require("node:child_process");
const path = require("node:path");
const fs = require("node:fs");
const os = require("node:os");

const out = path.resolve(process.argv[2] || "../../docs/screenshots");
const png = path.resolve(process.argv[3] || path.join(out, "synthetic-scoreboard.png"));
const truth = JSON.parse(fs.readFileSync(png.replace(/\.png$/, ".json"), "utf8"));
const root = path.resolve(__dirname, "..");
const electronBin = path.resolve(root, "../../node_modules/electron/dist/electron");
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function drag(page, shot, rect) {
  await shot.evaluate((el) => el.scrollIntoView({ block: "start" }));
  await sleep(150);
  const bb = await shot.boundingBox();
  const k = bb.width / truth.width;
  const x0 = bb.x + rect.x * k;
  const y0 = bb.y + rect.y * k;
  await page.mouse.move(x0, y0);
  await page.mouse.down();
  await page.mouse.move(x0 + (rect.w * k) / 2, y0 + (rect.h * k) / 2, { steps: 4 });
  await page.mouse.move(x0 + rect.w * k, y0 + rect.h * k, { steps: 4 });
  await page.mouse.up();
  await sleep(150);
}

(async () => {
  fs.mkdirSync(out, { recursive: true });
  const html = path.join(os.tmpdir(), "cc-fake-game.html");
  fs.writeFileSync(html, `<!doctype html><html><body style="margin:0;overflow:hidden;background:#000"><img src="file://${png}" style="display:block"></body></html>`);
  const ud = fs.mkdtempSync(path.join(os.tmpdir(), "cc-e2e-"));
  const app = await electron.launch({ executablePath: electronBin, args: ["--no-sandbox", root], env: { ...process.env, COUNTERCOACH_USER_DATA: ud } });
  const errors = [];
  app.on("console", (m) => m.type() === "error" && errors.push(m.text()));
  for (let i = 0; i < 50 && app.windows().length < 2; i++) await sleep(200);
  let main = null;
  let overlay = null;
  for (const w of app.windows()) (w.url().includes("view=overlay") ? (overlay = w) : (main = w));
  main.on("console", (m) => m.type() === "error" && errors.push(m.text()));
  await main.waitForSelector(".topbar", { timeout: 20000 });
  await app.evaluate(({ BrowserWindow }) => {
    const win = BrowserWindow.getAllWindows().find((x) => !x.webContents.getURL().includes("view=overlay"));
    win.setContentSize(1600, 1000);
    win.setPosition(0, 0);
  });
  const result = { note: truth.note, display: "Xvfb 1920x1080 (Linux)", steps: [] };
  const step = (name, data) => {
    result.steps.push({ name, ...data });
    console.log(name, JSON.stringify(data));
  };

  // Match context: the demo scenario's roster.
  await main.getByRole("tab", { name: "Scenarios" }).click();
  await main.getByRole("button", { name: "Abrams mid-game (demo)" }).click();
  await sleep(800);

  // A stand-in game window showing the synthetic scoreboard.
  await app.evaluate(async ({ BrowserWindow }, file) => {
    const w = new BrowserWindow({ x: 0, y: 0, width: 1920, height: 1080, frame: false, show: false, title: "Fake game (synthetic scoreboard)" });
    await w.loadFile(file);
    globalThis.fakeGame = w;
  }, html);
  const showGame = (on) =>
    app.evaluate((_e, on) => {
      const w = globalThis.fakeGame;
      if (on) {
        w.setAlwaysOnTop(true, "screen-saver");
        w.showInactive();
        w.moveTop();
      } else {
        w.setAlwaysOnTop(false);
        w.hide();
      }
    }, on);

  // 1) First capture through the button (5 s delay), with the "game" in front.
  await main.getByRole("tab", { name: "Screen" }).click();
  await main.getByRole("button", { name: "Capture in 5 s" }).click();
  await showGame(true);
  await main.waitForSelector("text=Calibrate (once per resolution)", { timeout: 20000 });
  await showGame(false);
  step("capture-button", { ok: true, message: await main.locator(".screen-page .section-body p.small").first().textContent() });

  // 2) Calibrate with three drags.
  const shot = main.locator(".shot").first();
  const r0 = truth.rows[0];
  const it0 = r0.items[0];
  await drag(main, shot, truth.geometry.itemArea);
  await drag(main, shot, { x: it0.x, y: it0.y, w: truth.geometry.icon, h: truth.geometry.icon });
  const p = truth.geometry.portrait;
  await drag(main, shot, { x: p.x, y: r0.y + (truth.geometry.icon - p.size) / 2, w: p.size, h: p.size });
  await main.evaluate(() => window.scrollTo(0, 0));
  await main.screenshot({ path: path.join(out, "09-screen-calibrate.png") });
  await main.getByRole("button", { name: "Save calibration" }).click();
  await main.waitForSelector("text=/Applied|Found/", { timeout: 30000 });
  const msg1 = await main.locator(".screen-page .section-body p.small").first().textContent();
  step("calibrate-and-read", { message: msg1, templates: await main.locator("text=/Icon templates/").first().textContent() });

  // Score the read against ground truth (row order on screen = row order in the image).
  const snap = JSON.parse(fs.readFileSync(path.join(root, "public/data/snapshot.json"), "utf8"));
  const itemName = new Map(snap.items.map((i) => [i.className, i.name]));
  const heroName = new Map(snap.heroes.map((h) => [h.id, h.name]));
  const rowEls = main.locator(".screen-row");
  const rows = await rowEls.count();
  let correct = 0;
  let total = 0;
  const wrong = [];
  const who = [];
  for (let i = 0; i < rows; i++) {
    const got = await rowEls.nth(i).locator(".screen-item-name").allTextContents();
    const want = truth.rows[i].items.map((x) => itemName.get(x.className));
    total += want.length;
    want.forEach((w, k) => (got[k] === w ? correct++ : wrong.push(`row ${i + 1}: want ${w}, got ${got[k]}`)));
    who.push(await rowEls.nth(i).locator("select option:checked").textContent());
  }
  const expectedWho = truth.rows.map((r) => (r.team === "me" ? `Me (${heroName.get(r.heroId)})` : `${r.team === "enemy" ? "Enemy" : "Ally"}: ${heroName.get(r.heroId)}`));
  step("accuracy", { rows, expectedRows: truth.rows.length, itemsCorrect: correct, itemsExpected: total, wrong, rowsAssignedCorrectly: who.filter((w, i) => w === expectedWho[i]).length });
  await main.evaluate(() => window.scrollTo(0, 0));
  await main.screenshot({ path: path.join(out, "10-screen-read-applied.png"), fullPage: false });

  // 3) Review flow: turn auto-apply off and read again.
  const autoApply = main.getByLabel("Apply automatically when every icon and row is confident");
  // Settings round-trip through the main process, so click and wait instead of check()/uncheck().
  await autoApply.click();
  await sleep(400);
  await main.getByRole("button", { name: "Read again" }).click();
  await main.waitForSelector("text=Who is who", { timeout: 30000 });
  await main.waitForSelector(".screen-actions >> text=Apply", { timeout: 30000 });
  await main.locator(".screen-rows").scrollIntoViewIfNeeded();
  await main.screenshot({ path: path.join(out, "11-screen-review.png") });
  await main.locator(".screen-actions").getByRole("button", { name: /^Apply/ }).click();
  await sleep(500);
  step("review-apply", { message: await main.locator(".screen-page .section-body p.small").first().textContent() });
  await autoApply.click();
  await sleep(400);

  // 4) Hotkey path: the global shortcut while the "game" is in front.
  await showGame(true);
  await sleep(500);
  let hotkey = "xdotool";
  try {
    execFileSync("xdotool", ["key", "--clearmodifiers", "ctrl+alt+r"]);
  } catch (e) {
    hotkey = `xdotool failed: ${e.message}`;
  }
  await sleep(4000);
  const note = overlay ? await overlay.locator(".ov-screen").textContent().catch(() => null) : null;
  if (overlay) await overlay.screenshot({ path: path.join(out, "12-overlay-screen-note.png") }).catch(() => {});
  await showGame(false);
  step("hotkey", { via: hotkey, overlayNote: note });

  // 5) Match tab shows the screen-read items.
  await main.getByRole("tab", { name: "Match" }).click();
  await sleep(800);
  const seen = await main.locator("text=/seen on screen/").count();
  step("match-tab", { seenOnScreenBadges: seen });
  await main.screenshot({ path: path.join(out, "13-match-after-screen-read.png") });

  result.consoleErrors = errors;
  fs.writeFileSync(path.join(out, "screen-e2e.json"), JSON.stringify(result, null, 1) + "\n");
  await app.close();
  fs.rmSync(ud, { recursive: true, force: true });
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
