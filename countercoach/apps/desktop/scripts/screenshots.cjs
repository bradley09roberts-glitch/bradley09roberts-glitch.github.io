// Drive the real Electron app with Playwright and capture screenshots.
// Usage (Linux, virtual display): xvfb-run -a -s "-screen 0 2560x1440x24" node scripts/screenshots.cjs <outDir>
const { _electron: electron } = require("/opt/node-tools/node_modules/playwright");
const path = require("node:path");
const fs = require("node:fs");
const os = require("node:os");

const out = path.resolve(process.argv[2] || "../../docs/screenshots");
const root = path.resolve(__dirname, "..");
const electronBin = path.resolve(root, "../../node_modules/electron/dist/electron");
fs.mkdirSync(out, { recursive: true });

async function launch(userData, extraEnv = {}) {
  const app = await electron.launch({
    executablePath: electronBin,
    args: ["--no-sandbox", root],
    env: { ...process.env, COUNTERCOACH_USER_DATA: userData, ...extraEnv },
  });
  const errors = [];
  app.on("console", (m) => { if (m.type() === "error") errors.push(m.text()); });
  return { app, errors };
}

async function windows(app) {
  for (let i = 0; i < 50 && app.windows().length < 2; i++) await new Promise((r) => setTimeout(r, 200));
  const ws = app.windows();
  let main = null, overlay = null;
  for (const w of ws) (w.url().includes("view=overlay") ? (overlay = w) : (main = w));
  return { main, overlay };
}

async function setMainSize(app, w, h) {
  await app.evaluate(({ BrowserWindow }, [w, h]) => {
    const win = BrowserWindow.getAllWindows().find((x) => !x.webContents.getURL().includes("view=overlay"));
    win.setContentSize(w, h);
    win.setPosition(0, 0);
  }, [w, h]);
  await new Promise((r) => setTimeout(r, 600));
}

async function loadScenario(main, title) {
  await main.getByRole("tab", { name: "Scenarios" }).click();
  await main.getByRole("button", { name: title }).click();
  await main.getByRole("tab", { name: "Match" }).click();
  await main.waitForTimeout(1200);
}

(async () => {
  const ud = fs.mkdtempSync(path.join(os.tmpdir(), "cc-shot-"));
  const { app, errors } = await launch(ud);
  const { main, overlay } = await windows(app);
  await main.waitForSelector(".topbar", { timeout: 20000 });

  // 1. First-run state (no hero yet) at 1366x768.
  await setMainSize(app, 1366, 768);
  await main.screenshot({ path: path.join(out, "01-first-run-1366x768.png") });

  // 2. Mid-game demo scenario at 1920x1080.
  await loadScenario(main, "Abrams mid-game (demo)");
  await setMainSize(app, 1920, 1080);
  await main.waitForTimeout(2500); // allow icons to load
  await main.screenshot({ path: path.join(out, "02-abrams-midgame-1920x1080.png") });

  // 3. Overlay (collapsed) – the real overlay window.
  await overlay.waitForSelector(".ov-name", { timeout: 10000 });
  await overlay.waitForTimeout(800);
  await overlay.screenshot({ path: path.join(out, "03-overlay-collapsed.png") });

  // 4. Overlay expanded at 1.25x via settings (persisted).
  await main.getByRole("tab", { name: "Settings" }).click();
  await main.getByRole("checkbox", { name: "Expanded" }).check();
  await main.waitForTimeout(800);
  await overlay.waitForTimeout(800);
  await overlay.screenshot({ path: path.join(out, "04-overlay-expanded.png") });
  await main.screenshot({ path: path.join(out, "05-settings-1920x1080.png") });

  // 5. Healing scenario – anti-heal advice.
  await loadScenario(main, "Haze vs heavy enemy healing");
  await main.waitForTimeout(1500);
  await main.screenshot({ path: path.join(out, "06-haze-vs-healing-1920x1080.png") });

  // 6. Laning scenario at ultrawide size.
  await loadScenario(main, "Infernus laning (demo)");
  await setMainSize(app, 2560, 1080);
  await main.waitForTimeout(1500);
  await main.screenshot({ path: path.join(out, "07-infernus-lane-2560x1080.png") });

  // 7. What-if tab.
  await setMainSize(app, 1600, 900);
  await loadScenario(main, "Minor, uncertain threat");
  await main.getByRole("tab", { name: "What-if" }).click();
  await main.locator("select").first().selectOption({ label: "Lady Geist" });
  await main.getByPlaceholder("What if they had…").fill("Spirit Lifesteal");
  await main.keyboard.press("Enter");
  await main.waitForTimeout(800);
  await main.screenshot({ path: path.join(out, "08-what-if-1600x900.png") });

  // Resource use of all app processes (Linux container, software rendering).
  await loadScenario(main, "Abrams mid-game (demo)");
  await main.waitForTimeout(3000);
  const metrics = await app.evaluate(({ app }) => app.getAppMetrics().map((m) => ({ type: m.type, cpuPercent: Math.round(m.cpu.percentCPUUsage * 10) / 10, workingSetMB: Math.round(m.memory.workingSetSize / 1024) })));
  const computeTexts = [];
  for (let i = 0; i < 5; i++) {
    await main.getByRole("button", { name: "+500" }).click();
    await main.waitForTimeout(250);
    computeTexts.push(await main.locator("text=/Computed locally in/").first().textContent());
  }

  const overlayBounds = await app.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows().find((x) => x.webContents.getURL().includes("view=overlay")).getBounds());
  const settings = JSON.parse(fs.readFileSync(path.join(ud, "settings.json"), "utf8"));
  await app.close();

  // 8. Relaunch: settings persisted?
  const second = await launch(ud);
  const w2 = await windows(second.app);
  await w2.main.waitForSelector(".topbar", { timeout: 20000 });
  const persisted = await second.app.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows().find((x) => x.webContents.getURL().includes("view=overlay")).getBounds());
  await second.app.close();
  const report = { out, metrics, totalWorkingSetMB: metrics.reduce((n, m) => n + m.workingSetMB, 0), computeTexts, overlayBounds, persistedOverlayBounds: persisted, expandedPersisted: settings.overlay.expanded, rendererErrors: errors.concat(second.errors) };
  fs.writeFileSync(path.join(out, "screenshot-run.json"), JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report, null, 2));
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
