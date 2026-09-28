import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// The dev server forwards /api to the WheelLab server (default port 8090), so the app always calls
// same-origin "/api/...". Set VITE_API_BASE_URL to call a server elsewhere instead.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: { "/api": process.env.WHEELLAB_API ?? "http://localhost:8090" },
  },
});
