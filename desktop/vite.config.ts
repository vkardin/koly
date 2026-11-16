import { defineConfig } from "vite";
import { resolve } from "node:path";

export default defineConfig({
  clearScreen: false,
  // The power map (web/powermap.*, web/ua-map.json) is shared with the website.
  resolve: { alias: { "@web": resolve(__dirname, "../web") } },
  server: { port: 1420, strictPort: true, fs: { allow: [".", "../web"] } },
  build: {
    target: "es2022",
    minify: "esbuild",
    cssMinify: true,
    assetsInlineLimit: 0,
    rollupOptions: {
      input: {
        widget: resolve(__dirname, "index.html"),
        settings: resolve(__dirname, "settings.html"),
      },
    },
  },
});
