// Bundles the TypeScript tests with esbuild and runs them with node:test.
import { build } from "esbuild";
import { spawnSync } from "node:child_process";
import { mkdtempSync, readdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const out = mkdtempSync(join(tmpdir(), "koly-tests-"));
const entries = readdirSync("tests").filter((f) => f.endsWith(".test.ts")).map((f) => join("tests", f));
await build({ entryPoints: entries, bundle: true, platform: "node", format: "esm", outdir: out, outExtension: { ".js": ".mjs" }, logLevel: "warning" });
const files = readdirSync(out).map((f) => join(out, f));
const res = spawnSync(process.execPath, ["--test", ...files], { stdio: "inherit" });
process.exit(res.status ?? 1);
