import { useState } from "react";
import { matchxCheck, matchxCheckLines } from "../api";
import LinesGrid from "../components/LinesGrid";
import { FAMILIES, LETTERS, familyOf } from "./model";

const fmt = (v) => Number(v).toLocaleString("en-US");

/** The market of every match, from the groups that play it (1 X 2 for matches no group plays). */
function marketsOf(state) {
  const out = Array.from({ length: state.matches }, () => FAMILIES[0]);
  for (const t of state.tickets)
    for (const g of t.groups)
      g.matches.forEach((m, i) => {
        if (m <= state.matches && out[m - 1] === FAMILIES[0]) out[m - 1] = familyOf(g.picks[i][0]);
      });
  return out;
}

/**
 * Check: the hit distribution of every ticket for a result (computed by the server without building the
 * lines), and the lines themselves above a number of hits.
 */
export default function CheckPanel({ state, body, disabled }) {
  const markets = marketsOf(state);
  const [picked, setPicked] = useState({});
  const result = markets.map((f, i) => (f.symbols.includes(picked[i]) ? picked[i] : f.symbols[0]));
  const key = JSON.stringify([body, result]);
  const [check, setCheck] = useState(null);
  const [minHits, setMinHits] = useState(Math.max(0, state.matches - 2));
  const [exact, setExact] = useState(false);
  const [shown, setShown] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const current = check?.key === key ? check.data : null;

  async function run(fn) {
    setBusy(true);
    setError(null);
    try {
      await fn();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  const n = state.matches;
  const rows = current ? current.histogram.map((v, h) => ({ h, v })).reverse().filter((r) => r.v > 0) : [];
  return (
    <section className="card">
      <h2>Check</h2>
      <p className="muted">Enter the result of every match: you get how many lines of every ticket hit {n}/{n}, {n - 1}/{n}, … -- instantly, without building them.</p>
      <div className="match-grid">
        {markets.map((f, i) => (
          <div className="match" key={i}>
            <div className="match-head"><strong>Match {i + 1}</strong><span className="muted small">{f.label}</span></div>
            <div className="row">
              {f.symbols.map((s) => (
                <button key={s} type="button" className={result[i] === s ? "num on" : "num"} aria-label={`Result ${i + 1}: ${s}`}
                  aria-pressed={result[i] === s} onClick={() => setPicked({ ...picked, [i]: s })}>
                  {s}
                </button>
              ))}
            </div>
          </div>
        ))}
      </div>
      <div className="row" style={{ marginTop: 12 }}>
        <button onClick={() => run(async () => setCheck({ key, data: await matchxCheck(body, result) }))} disabled={busy || disabled}>
          {busy ? "Checking…" : "Check"}
        </button>
        <button className="pill" onClick={() => setPicked(Object.fromEntries(markets.map((f, i) => [i, f.symbols[Math.floor(Math.random() * f.symbols.length)]])))}>
          🎲 Random result
        </button>
      </div>
      {disabled && <p className="muted small">Fix the ticket first.</p>}
      {error && <p className="error">{error}</p>}

      {current && (
        <>
          <h3 style={{ marginTop: 16 }}>Hits for {result.join(" ")}</h3>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Hits</th>
                  {current.tickets.length > 1 && current.tickets.map((_, i) => <th key={i}>Ticket {i + 1}</th>)}
                  <th>Lines</th>
                </tr>
              </thead>
              <tbody>
                {rows.map(({ h, v }) => (
                  <tr key={h}>
                    <td><strong>{h}/{n}</strong></td>
                    {current.tickets.length > 1 && current.tickets.map((t, i) => <td key={i}>{fmt(t.histogram[h])}</td>)}
                    <td><strong>{fmt(v)}</strong></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <details style={{ marginTop: 8 }}>
            <summary>By group</summary>
            {current.tickets.map((t, ti) => (
              <ul key={ti}>
                {t.groups.map((g, gi) => (
                  <li key={gi} className="small">
                    {current.tickets.length > 1 && `Ticket ${ti + 1} · `}Group {LETTERS[gi]} ({g.type}, matches {g.matches.join(",")}):{" "}
                    <span className="muted">
                      {g.histogram.map((v, h) => ({ v, h })).reverse().filter((x) => x.v > 0).map((x) => `${x.h}/${g.matches.length}: ${fmt(x.v)}`).join(" · ")}
                    </span>
                  </li>
                ))}
              </ul>
            ))}
          </details>
          <div className="row" style={{ marginTop: 12 }}>
            <label>
              Show lines with{" "}
              <select value={exact ? "exact" : "min"} onChange={(e) => setExact(e.target.value === "exact")} aria-label="At least or exactly">
                <option value="min">at least</option>
                <option value="exact">exactly</option>
              </select>{" "}
              <input type="number" min={0} max={n} value={minHits} style={{ width: 60 }} aria-label="Hits"
                onChange={(e) => setMinHits(Math.max(0, Math.min(n, Number(e.target.value) || 0)))} /> hits
            </label>
            <button disabled={busy} onClick={() => run(async () => setShown({ ...(await matchxCheckLines(body, result, minHits, exact)), minHits, exact, result }))}>
              Show lines
            </button>
          </div>
        </>
      )}
      {shown && (
        <div style={{ marginTop: 12 }}>
          <p className="muted">{fmt(shown.total)} lines with {shown.exact ? "exactly" : "at least"} {shown.minHits} hits (hits highlighted).</p>
          {shown.total > 0 && <LinesGrid key={shown.id} id={shown.id} total={shown.total} name="matchx_hits"
            hits={(line) => line.map((s, i) => s === shown.result[i])} />}
        </div>
      )}
    </section>
  );
}
