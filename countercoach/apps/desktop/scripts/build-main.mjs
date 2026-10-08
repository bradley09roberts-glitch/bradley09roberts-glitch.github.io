// Bundle the Electron main and preload scripts (CommonJS, Node target).
import { build } from "esbuild";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "..");
const common = { bundle: true, platform: "node", target: "node22", format: "cjs", external: ["electron"], sourcemap: false, logLevel: "info", legalComments: "none" };
await build({ ...common, entryPoints: [path.join(root, "src/main/main.ts")], outfile: path.join(root, "dist-electron/main.cjs") });
await build({ ...common, entryPoints: [path.join(root, "src/preload/preload.ts")], outfile: path.join(root, "dist-electron/preload.cjs") });
