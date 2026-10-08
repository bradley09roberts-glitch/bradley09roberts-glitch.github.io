// Bundle the Electron main and preload scripts (CommonJS, Node target), and ship the native
// keyboard listener (uiohook-napi, used only for the opt-in "read when I hold Tab") next to them.
import { build } from "esbuild";
import { cp, mkdir, rm } from "node:fs/promises";
import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "..");
const require = createRequire(import.meta.url);
const common = { bundle: true, platform: "node", target: "node22", format: "cjs", external: ["electron", "uiohook-napi"], sourcemap: false, logLevel: "info", legalComments: "none" };
await build({ ...common, entryPoints: [path.join(root, "src/main/main.ts")], outfile: path.join(root, "dist-electron/main.cjs") });
await build({ ...common, entryPoints: [path.join(root, "src/preload/preload.ts")], outfile: path.join(root, "dist-electron/preload.cjs") });

// Vendor the listener: its JS, the prebuilt N-API binaries for the platforms we package, and its
// loader. electron-builder keeps the .node files outside the asar (asarUnpack) so they can load.
const vendor = path.join(root, "dist-electron/vendor/uiohook-napi");
await rm(vendor, { recursive: true, force: true });
const pkg = path.dirname(require.resolve("uiohook-napi/package.json"));
const loader = path.dirname(require.resolve("node-gyp-build/package.json", { paths: [pkg] }));
await mkdir(vendor, { recursive: true });
await cp(path.join(pkg, "package.json"), path.join(vendor, "package.json"));
await cp(path.join(pkg, "LICENSE"), path.join(vendor, "LICENSE"));
await cp(path.join(pkg, "dist"), path.join(vendor, "dist"), { recursive: true });
for (const target of ["win32-x64", "linux-x64"]) {
  await cp(path.join(pkg, "prebuilds", target), path.join(vendor, "prebuilds", target), { recursive: true });
}
await cp(loader, path.join(vendor, "node_modules/node-gyp-build"), { recursive: true });
console.log("vendored uiohook-napi (win32-x64, linux-x64)");
