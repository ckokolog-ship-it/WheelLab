import { useState } from "react";
import { pickCheck, pickCheckLines, pickPosition } from "../api";
import LinesGrid from "../components/LinesGrid";
import NumberPad from "../components/NumberPad";
import { describeEntry } from "./EntryEditor";
import { hitOdds, oneIn } from "./odds";

const fmt = (v) => Number(v).toLocaleString("en-US");

/** Prizes per number of hits, as the user typed them -> numbers (blank = no prize). */
export const prizeValues = (prizes, k) => Array.from({ length: k + 1 }, (_, h) => (prizes[h] === "" || prizes[h] == null ? null : Number(prizes[h])));

function PrizeInputs({ k, prizes, onChange }) {
  return (
    <div className="row small">
      <span className="muted">Prize per line with</span>
      {Array.from({ length: k + 1 }, (_, h) => k - h).map((h) => (
        <label key={h}>
          {h} hits{" "}
          <input type="number" min={0} step="any" value={prizes[h] ?? ""} placeholder="0" style={{ width: 80 }} aria-label={`Prize for ${h} hits`}
            onChange={(e) => onChange({ ...prizes, [h]: e.target.value })} />
        </label>
      ))}
    </div>
  );
}

export function CheckPanel({ game, body, entries, prizes, onPrizes, disabled }) {
  const k = game.pick;
  const [drawn, setDrawn] = useState([]);
  const [result, setResult] = useState(null);
  const [minHits, setMinHits] = useState(k);
  const [exact, setExact] = useState(false);
  const [shown, setShown] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const key = JSON.stringify([body, drawn, prizes]);
  const current = result?.key === key ? result.data : null;
  const ready = drawn.length === game.draw && !disabled;

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
  const values = prizeValues(prizes, k);
  return (
    <section className="card">
      <h2>Check</h2>
      <p className="muted">Pick the {game.draw} drawn numbers: you get how many of your lines hit {k}, {k - 1}, … -- instantly, without building them.</p>
      <NumberPad max={game.numbers} selected={drawn} onChange={setDrawn} limit={game.draw} randomCount={game.draw} label="Drawn" />
      <div style={{ marginTop: 10 }}><PrizeInputs k={k} prizes={prizes} onChange={onPrizes} /></div>
      <div className="row" style={{ marginTop: 10 }}>
        <button disabled={!ready || busy} onClick={() => run(async () => setResult({ key, data: await pickCheck(body, drawn, values) }))}>
          {busy ? "Checking…" : "Check"}
        </button>
        {!ready && <span className="muted small">{disabled ? "Add entries first." : `Pick ${game.draw - drawn.length} more drawn numbers.`}</span>}
      </div>
      {error && <p className="error">{error}</p>}
      {current && (
        <>
          <div className="table-scroll" style={{ marginTop: 12 }}>
            <table>
              <thead><tr><th>Hits</th><th>Lines</th><th>Prize</th><th>Subtotal</th></tr></thead>
              <tbody>
                {current.histogram.map((v, h) => ({ v, h })).reverse().filter((r) => r.v > 0).map(({ v, h }) => (
                  <tr key={h}>
                    <td><strong>{h}/{k}</strong></td>
                    <td>{fmt(v)}</td>
                    <td>{values[h] ? fmt(values[h]) : "-"}</td>
                    <td>{values[h] ? <strong>{(values[h] * v).toLocaleString("en-US")}</strong> : "-"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p>Lines: <strong>{fmt(current.total)}</strong>{current.payout !== undefined && <> · Total prize: <strong>{Number(current.payout).toLocaleString("en-US")}</strong></>}</p>
          {current.entries.length > 1 && (
            <details>
              <summary>By entry</summary>
              <ol className="entry-list small">
                {current.entries.map((e, i) => (
                  <li key={i}>
                    {describeEntry(entries[i])} --{" "}
                    <span className="muted">{e.histogram.map((v, h) => ({ v, h })).reverse().filter((r) => r.v > 0).map((r) => `${r.h} hits: ${fmt(r.v)}`).join(" · ")}</span>
                  </li>
                ))}
              </ol>
            </details>
          )}
          <div className="row" style={{ marginTop: 10 }}>
            <label>
              Show lines with{" "}
              <select value={exact ? "exact" : "min"} onChange={(e) => setExact(e.target.value === "exact")} aria-label="At least or exactly">
                <option value="min">at least</option>
                <option value="exact">exactly</option>
              </select>{" "}
              <input type="number" min={0} max={k} value={minHits} style={{ width: 60 }} aria-label="Hits"
                onChange={(e) => setMinHits(Math.max(0, Math.min(k, Number(e.target.value) || 0)))} /> hits
            </label>
            <button disabled={busy} onClick={() => run(async () => setShown({ ...(await pickCheckLines(body, drawn, minHits, exact)), minHits, exact, drawn }))}>Show lines</button>
          </div>
        </>
      )}
      {shown && (
        <div style={{ marginTop: 12 }}>
          <p className="muted">{fmt(shown.total)} lines with {shown.exact ? "exactly" : "at least"} {shown.minHits} hits (hits highlighted).</p>
          {shown.total > 0 && <LinesGrid key={shown.id} id={shown.id} total={shown.total} name="hits"
            hits={(line) => line.map((s) => shown.drawn.includes(Number(s)))} />}
        </div>
      )}
    </section>
  );
}

export function OddsPanel({ game, prizes }) {
  const rows = hitOdds(game.numbers, game.pick, game.draw);
  const values = prizeValues(prizes, game.pick);
  const hasPrizes = values.some((v) => v);
  const expected = rows.reduce((s, r) => s + (values[r.hits] || 0) * (Number(r.ways) / Number(r.total)), 0);
  return (
    <section className="card">
      <h2>Odds</h2>
      <p className="muted">One line of {game.pick} numbers from 1-{game.numbers}, {game.draw} numbers drawn.</p>
      <div className="table-scroll">
        <table>
          <thead><tr><th>Hits</th><th>Chance</th><th>Probability</th>{hasPrizes && <th>Prize</th>}</tr></thead>
          <tbody>
            {[...rows].reverse().map((r) => (
              <tr key={r.hits}>
                <td><strong>{r.hits}/{game.pick}</strong></td>
                <td>{oneIn(r.ways, r.total)}</td>
                <td className="muted">{(Number(r.ways) / Number(r.total) * 100).toPrecision(4)}%</td>
                {hasPrizes && <td>{values[r.hits] ? fmt(values[r.hits]) : "-"}</td>}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {hasPrizes && <p>Expected prize per line: <strong>{expected.toLocaleString("en-US", { maximumFractionDigits: 4 })}</strong> <span className="muted small">(from the prizes in Check)</span></p>}
    </section>
  );
}

export function PositionPanel({ game }) {
  const [text, setText] = useState("");
  const [usePool, setUsePool] = useState(false);
  const [pool, setPool] = useState([]);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const lines = text.split("\n").map((l) => l.trim()).filter(Boolean);
  const example = Array.from({ length: game.pick }, (_, i) => i + 1).join(" ");

  async function find() {
    setError(null);
    try {
      setResult(await pickPosition(game, lines, usePool ? pool : null));
    } catch (e) {
      setError(e.message);
      setResult(null);
    }
  }
  return (
    <section className="card">
      <h2>Find position</h2>
      <p className="muted">Where a line is among all lines, in order (1 2 3 … first).</p>
      <div className="row">
        <label><input type="radio" checked={!usePool} onChange={() => setUsePool(false)} /> all lines of the game</label>
        <label><input type="radio" checked={usePool} onChange={() => setUsePool(true)} /> a system on my numbers</label>
      </div>
      {usePool && <div style={{ marginTop: 8 }}><NumberPad max={game.numbers} selected={pool} onChange={setPool} randomCount={game.pick + 2} label="Pool" /></div>}
      <textarea rows={4} value={text} onChange={(e) => setText(e.target.value)} placeholder={`${example}   (one line per row)`}
        aria-label="Lines to find" style={{ width: "100%", marginTop: 8, font: "inherit" }} />
      <div className="row"><button onClick={find} disabled={!lines.length}>Find</button></div>
      {error && <p className="error">{error}</p>}
      {result && (
        <div className="table-scroll" style={{ marginTop: 8 }}>
          <p className="muted small">{fmt(result.total)} lines · 50 per page</p>
          <table>
            <thead><tr><th>Line</th><th>Position</th><th>Page</th></tr></thead>
            <tbody>
              {result.rows.map((r, i) => (
                <tr key={i}>
                  <td>{r.line}</td>
                  {r.error ? <td colSpan={2} className="error">{r.error}</td> : <><td><strong>{fmt(r.rank)}</strong></td><td>{fmt(Math.ceil(r.rank / 50))}</td></>}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
