import { defineConfig } from "vite";

export default defineConfig({
  clearScreen: false,
  server: {
    host: "localhost",
    port: 1420,
    strictPort: true
  },
  build: {
    outDir: "dist",
    emptyOutDir: true
  }
});
