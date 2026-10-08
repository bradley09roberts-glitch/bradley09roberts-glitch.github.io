// End-to-end check of the screen reader in the real Electron app on a virtual display:
// a stand-in "game" window shows a SYNTHETIC scoreboard (scripts/make-test-scoreboard.ts), the
// app captures the real screen through desktopCapturer, the user calibration is performed with
// mouse drags, and the read is applied to the match. This is not a test against Deadlock.
//
// Usage (Linux): xvfb-run -a -s "-screen 0 1920x1080x24" node scripts/screen-e2e.cjs <outDir> <synthetic-scoreboard.png>
// The "<name>-next.png" board next to it (different builds) is used for the hold-Tab step.
const { _electron: electron } = require("/opt/node-tools/node_modules/playwright");
const { execFileSync } = require("node:child_process");
const path = require("node:path");
const fs = require("node:fs");
const os = require("node:os");

const out = path.resolve(process.argv[2] || "../../docs/screenshots");
const png = path.resolve(process.argv[3] || path.join(out, "synthetic-scoreboard.png"));
const truth = JSON.parse(fs.readFileSync(png.replace(/\.png$/, ".json"), "utf8"));
const nextPng = png.replace(/\.png$/, "-next.png");
const nextTruth = JSON.parse(fs.readFileSync(nextPng.replace(/\.png$/, ".json"), "utf8"));
const root = path.resolve(__dirname, "..");
const electronBin = path.resolve(root, "../../node_modules/electron/dist/electron");
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function drag(page, shot, rect) {
  // Bring the target into the middle of the window (the app's top bar is sticky).
  const first = await shot.boundingBox();
  const k0 = first.width / truth.width;
  await page.evaluate((dy) => window.scrollBy(0, dy), first.y + (rect.y + rect.h / 2) * k0 - 450);
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
  const page = (file) => `<!doctype html><html><body style="margin:0;overflow:hidden;background:#000"><img src="file://${file}" style="display:block"></body></html>`;
  const html = path.join(os.tmpdir(), "cc-fake-game.html");
  fs.writeFileSync(html, page(png));
  const html2 = path.join(os.tmpdir(), "cc-fake-game-next.html");
  fs.writeFileSync(html2, page(nextPng));
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
  step("capture-button", { ok: true, message: await main.locator(".screen-msg").first().textContent() });

  // 2) Calibrate with three drags: the item strip, one icon, and that player's card portrait.
  const shot = main.locator(".shot").first();
  const r0 = truth.rows[0];
  const it0 = r0.items[0];
  await drag(main, shot, truth.geometry.itemArea);
  await drag(main, shot, { x: it0.x, y: it0.y, w: truth.geometry.icon, h: truth.geometry.icon });
  await drag(main, shot, r0.portrait);
  await main.evaluate(() => window.scrollTo(0, 0));
  await main.screenshot({ path: path.join(out, "09-screen-calibrate.png") });
  await main.getByRole("button", { name: "Save calibration" }).click();
  await main.waitForSelector("text=/Applied|Found/", { timeout: 30000 });
  const msg1 = await main.locator(".screen-msg").first().textContent();
  step("calibrate-and-read", { message: msg1, templates: await main.locator("text=/Icon templates/").first().textContent() });

  // Score the read against ground truth (players left to right = cards left to right).
  const snap = JSON.parse(fs.readFileSync(path.join(root, "public/data/snapshot.json"), "utf8"));
  const itemName = new Map(snap.items.map((i) => [i.className, i.name]));
  const heroName = new Map(snap.heroes.map((h) => [h.id, h.name]));
  const rowEls = main.locator(".screen-row");
  const score = async (t) => {
    const n = await rowEls.count();
    let correct = 0;
    let total = 0;
    const wrong = [];
    for (let i = 0; i < Math.min(n, t.rows.length); i++) {
      const got = (await rowEls.nth(i).locator(".screen-item-name").allTextContents()).sort();
      const want = t.rows[i].items.map((x) => itemName.get(x.className)).sort();
      total += want.length;
      want.forEach((w) => (got.includes(w) ? correct++ : wrong.push(`player ${i + 1}: missing ${w}`)));
      got.filter((g) => !want.includes(g)).forEach((g) => wrong.push(`player ${i + 1}: extra ${g}`));
    }
    return { players: n, correct, total, wrong };
  };
  const { players: rows, correct, total, wrong } = await score(truth);
  const auto = /Applied automatically/.test(msg1 || "");
  const proposed = [];
  for (let i = 0; i < rows; i++) proposed.push(await rowEls.nth(i).locator("select option:checked").textContent());
  const expectedWho = truth.rows.map((r) => (r.team === "me" ? `Me (${heroName.get(r.heroId)})` : `${r.team === "enemy" ? "Enemy" : "Ally"}: ${heroName.get(r.heroId)}`));
  step("accuracy", {
    players: rows,
    expectedPlayers: truth.rows.length,
    itemsCorrect: correct,
    itemsExpected: total,
    wrong,
    appliedAutomatically: auto,
    playersNamedFromPortraits: proposed.filter((w, i) => w === expectedWho[i]).length,
  });
  await main.evaluate(() => window.scrollTo(0, 0));
  await main.screenshot({ path: path.join(out, "10-screen-read.png"), fullPage: false });

  // 3) First read of a match: say who each player is (portraits only cover some), then apply.
  if (!auto) {
    const value = (r) => (r.team === "me" ? "me" : `${r.team}:${r.heroId}`);
    for (let i = 0; i < Math.min(rows, truth.rows.length); i++) await rowEls.nth(i).locator("select").selectOption(value(truth.rows[i]));
    await main.locator(".screen-rows").scrollIntoViewIfNeeded();
    await main.screenshot({ path: path.join(out, "11-screen-review.png") });
    await main.locator(".screen-actions").getByRole("button", { name: /^Apply/ }).click();
    await sleep(500);
    step("review-apply", { message: await main.locator(".screen-msg").first().textContent() });
  }

  // 4) Hotkey path: the global shortcut while the "game" is in front.
  await showGame(true);
  await sleep(500);
  let hotkey = "xdotool";
  try {
    execFileSync("xdotool", ["key", "--clearmodifiers", "ctrl+alt+r"]);
  } catch (e) {
    hotkey = `xdotool failed: ${e.message}`;
  }
  await sleep(5000);
  const note = overlay ? await overlay.locator(".ov-screen").textContent().catch(() => null) : null;
  if (overlay) await overlay.screenshot({ path: path.join(out, "12-overlay-screen-note.png") }).catch(() => {});
  await showGame(false);
  step("hotkey", { via: hotkey, overlayNote: note, message: await main.locator(".screen-msg").first().textContent().catch(() => null) });

  // 5) Match tab shows the screen-read items.
  await main.getByRole("tab", { name: "Match" }).click();
  await sleep(800);
  const seen = await main.locator("text=/seen on screen/").count();
  step("match-tab", { seenOnScreenBadges: seen });
  await main.screenshot({ path: path.join(out, "13-match-after-screen-read.png") });

  // 6) Read on Tab: turn the option on, put a board with different builds in the "game", give the
  // game focus and hold Tab for a second. No hotkey, no clicks: the calibrated slots are read.
  await main.getByRole("tab", { name: "Screen" }).click();
  await main.getByText("Read automatically every time I hold Tab in the game").click();
  await main.waitForSelector("text=listening for Tab", { timeout: 10000 }).catch(() => {});
  const listener = await main.locator("text=/listening for Tab|Tab listener could not start/").first().textContent().catch(() => null);
  const before = await main.locator(".screen-msg").first().textContent().catch(() => null);
  await app.evaluate(async (_e, file) => {
    const w = globalThis.fakeGame;
    await w.loadFile(file);
    w.setAlwaysOnTop(true, "screen-saver");
    w.show();
    w.focus();
  }, html2);
  await sleep(800);
  let tabVia = "xdotool";
  try {
    execFileSync("xdotool", ["keydown", "Tab"]);
    await sleep(1000);
    execFileSync("xdotool", ["keyup", "Tab"]);
  } catch (e) {
    tabVia = `xdotool failed: ${e.message}`;
  }
  // Wait for the read to finish (the message passes through "Reading…").
  const settled = (m) => m !== before && !/^(Reading|Preparing|Capturing)/.test(m || "");
  let after = before;
  for (let i = 0; i < 60 && !settled(after); i++) {
    await sleep(250);
    after = await main.locator(".screen-msg").first().textContent().catch(() => null);
  }
  await sleep(500);
  await showGame(false);
  const tabStatus = await main.evaluate(() => window.countercoach.tabStatus()).catch((e) => String(e));
  const nextScore = await score(nextTruth);
  step("hold-tab", {
    via: tabVia,
    listener,
    message: after,
    appliedAutomatically: /Applied automatically/.test(after || ""),
    players: nextScore.players,
    itemsCorrect: nextScore.correct,
    itemsExpected: nextScore.total,
    wrong: nextScore.wrong,
    tabStatus,
  });
  await main.evaluate(() => window.scrollTo(0, 0));
  await main.screenshot({ path: path.join(out, "14-screen-read-on-tab.png") });

  // 7) A quick tap (shorter than the hold delay) must not capture.
  await app.evaluate(() => {
    const w = globalThis.fakeGame;
    w.show();
    w.focus();
  });
  await sleep(500);
  execFileSync("xdotool", ["keydown", "Tab"]);
  await sleep(100);
  execFileSync("xdotool", ["keyup", "Tab"]);
  await sleep(2500);
  await showGame(false);
  const afterTap = await main.locator(".screen-msg").first().textContent().catch(() => null);
  step("tap-tab", { captured: afterTap !== after, tabStatus: await main.evaluate(() => window.countercoach.tabStatus()).catch((e) => String(e)) });
  await main.getByText("Read automatically every time I hold Tab in the game").scrollIntoViewIfNeeded();
  await main.screenshot({ path: path.join(out, "15-screen-tab-setting.png") });

  result.consoleErrors = errors;
  fs.writeFileSync(path.join(out, "screen-e2e.json"), JSON.stringify(result, null, 1) + "\n");
  await app.close();
  fs.rmSync(ud, { recursive: true, force: true });
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
