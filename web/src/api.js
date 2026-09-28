// WheelLab server API. By default the app calls the same origin ("/api/..."): the server serves the built
// app, and the dev server proxies /api. VITE_API_BASE_URL points it at another server.
const BASE = (import.meta.env.VITE_API_BASE_URL ?? "").replace(/\/+$/, "");

async function request(path, options) {
  let res;
  try {
    res = await fetch(BASE + path, options);
  } catch {
    throw new Error("Cannot reach the WheelLab server -- is it running?");
  }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || `Server error (${res.status}).`);
  return data;
}

const post = (path, body) =>
  request(path, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });

export const health = () => request("/api/health");

/** A page of a built system: {total, start, lines}. */
export const fetchLines = (id, start, size) => request(`/api/lines?id=${encodeURIComponent(id)}&start=${start}&size=${size}`);

export const matchxCount = (body) => post("/api/matchx/count", body);
export const matchxBuild = (body) => post("/api/matchx/build", body);
export const matchxCheck = (body, result) => post("/api/matchx/check", { ...body, result });
export const matchxCheckLines = (body, result, minHits, exact) =>
  post("/api/matchx/check-lines", { ...body, result, minHits, exact });

// Number games (LOTTO, KENO): {game: {numbers, pick, draw}, entries: [...]}
export const pickCount = (body) => post("/api/pick/count", body);
export const pickBuild = (body) => post("/api/pick/build", body);
export const pickCheck = (body, drawn, drawnBonus, payouts) =>
  post("/api/pick/check", { ...body, drawn, drawnBonus, ...(payouts ? { payouts } : {}) });
export const pickCheckLines = (body, drawn, drawnBonus, minHits, exact, minBonus) =>
  post("/api/pick/check-lines", { ...body, drawn, drawnBonus, minHits, exact, minBonus });
export const pickPosition = (game, lines, pool, bonusPool) =>
  post("/api/pick/position", { game, lines, ...(pool?.length ? { pool } : {}), ...(bonusPool?.length ? { bonusPool } : {}) });
export const pickAnalysis = (body, entry) => post("/api/pick/analysis", { ...body, entry });
export const pickFrequency = (body) => post("/api/pick/frequency", body);
export const pickParse = (text) => post("/api/pick/parse", { text });
export const pickText = (body) => post("/api/pick/text", body);
