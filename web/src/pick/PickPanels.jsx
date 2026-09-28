import { useState } from "react";
import { pickAnalysis, pickCheck, pickCheckLines, pickFrequency, pickPosition } from "../api";
import LinesGrid from "../components/LinesGrid";
import NumberPad from "../components/NumberPad";
import { describeEntry } from "./EntryEditor";
import { hitOdds, oneIn } from "./odds";

const fmt = (v) => Number(v).toLocaleString("en-US");
const range = (a, b) => Array.from({ length: Math.max(0, b - a + 1) }, (_, i) => a + i);
export const bounds = (game) => ({ min: game.min ?? 1, max: game.max ?? game.numbers });
const size = (game) => bounds(game).max - bounds(game).min + 1;

/**
 * Prizes as typed ({"5": "1000", "5+2": "1000000"} -- "h" = h main hits with any bonus hits) -> the server's
 * payouts: payouts[h], or payouts[h][s] in games with a bonus pool. Blank = no prize.
 */
export function payouts(prizes, game) {
  const val = (key) => (prizes[key] === "" || prizes[key] == null ? null : Number(prizes[key]));
  return range(0, game.pick).map((h) => (game.bonus ? range(0, game.bonus.pick).map((s) => val(`${h}+${s}`) ?? val(`${h}`)) : val(`${h}`)));
}
const prizeOf = (p, h, s) => (Array.isArray(p[h]) ? p[h][s] : p[h]) || 0;

function PrizeInputs({ game, prizes, onChange }) {
  const set = (key, v) => onChange({ ...prizes, [key]: v });
  const input = (key, label) => (
    <input type="number" min={0} step="any" value={prizes[key] ?? ""} placeholder="0" style={{ width: 90 }} aria-label={label}
      onChange={(e) => set(key, e.target.value)} />
  );
  if (!game.bonus)
    return (
      <div className="row small">
        <span className="muted">Prize per line with</span>
        {range(0, game.pick).reverse().map((h) => <label key={h}>{h} hits {input(`${h}`, `Prize for ${h} hits`)}</label>)}
      </div>
    );
  return (
    <details>
      <summary className="small">Prizes per line (main hits + {game.bonus.label} hits)</summary>
      <div className="table-scroll">
        <table>
          <thead><tr><th>Main hits</th>{range(0, game.bonus.pick).reverse().map((s) => <th key={s}>+{s} {game.bonus.label}</th>)}</tr></thead>
          <tbody>
            {range(0, game.pick).reverse().map((h) => (
              <tr key={h}>
                <td><strong>{h}</strong></td>
                {range(0, game.bonus.pick).reverse().map((s) => <td key={s}>{input(`${h}+${s}`, `Prize for ${h}+${s} hits`)}</td>)}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </details>
  );
}

export function CheckPanel({ game, body, entries, prizes, onPrizes, disabled }) {
  const k = game.pick, bp = game.bonus?.pick ?? 0;
  const { min, max } = bounds(game);
  const [drawn, setDrawn] = useState([]);
  const [drawnBonus, setDrawnBonus] = useState([]);
  const [result, setResult] = useState(null);
  const [minHits, setMinHits] = useState(k);
  const [exact, setExact] = useState(false);
  const [shown, setShown] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const pays = payouts(prizes, game);
  const key = JSON.stringify([body, drawn, drawnBonus, pays]);
  const current = result?.key === key ? result.data : null;
  const bonusReady = !game.bonus || drawnBonus.length === game.bonus.draw;
  const ready = drawn.length === game.draw && bonusReady && !disabled;

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
  const hasPrizes = pays.some((p) => (Array.isArray(p) ? p.some(Boolean) : p));
  const rows = current ? range(0, k).reverse().filter((h) => current.histogram[h] > 0) : [];
  return (
    <section className="card">
      <h2>Check</h2>
      <p className="muted">Pick the {game.draw} drawn numbers{game.bonus ? ` and the ${game.bonus.draw} drawn ${game.bonus.label}` : ""}: you get how many of your lines hit {k}, {k - 1}, … -- instantly, without building them.</p>
      <NumberPad min={min} max={max} selected={drawn} onChange={setDrawn} limit={game.draw} randomCount={game.draw} label="Drawn" />
      {game.bonus && (
        <div style={{ marginTop: 8 }}>
          <h3>Drawn {game.bonus.label}</h3>
          <NumberPad min={game.bonus.min} max={game.bonus.max} selected={drawnBonus} onChange={setDrawnBonus} limit={game.bonus.draw}
            randomCount={game.bonus.draw} label={`Drawn ${game.bonus.label}`} />
        </div>
      )}
      <div style={{ marginTop: 10 }}><PrizeInputs game={game} prizes={prizes} onChange={onPrizes} /></div>
      <div className="row" style={{ marginTop: 10 }}>
        <button disabled={!ready || busy} onClick={() => run(async () => setResult({ key, data: await pickCheck(body, drawn, drawnBonus, hasPrizes ? pays : null) }))}>
          {busy ? "Checking…" : "Check"}
        </button>
        {!ready && <span className="muted small">{disabled ? "Add entries first." : "Pick all the drawn numbers."}</span>}
      </div>
      {error && <p className="error">{error}</p>}
      {current && (
        <>
          <div className="table-scroll" style={{ marginTop: 12 }}>
            <table>
              <thead>
                <tr>
                  <th>Hits</th>
                  {game.bonus && range(0, bp).reverse().map((s) => <th key={s}>+{s} {game.bonus.label}</th>)}
                  <th>Lines</th>
                  {hasPrizes && <th>Prize</th>}
                </tr>
              </thead>
              <tbody>
                {rows.map((h) => {
                  const cells = game.bonus ? current.grid[h] : [current.histogram[h]];
                  const pay = cells.reduce((sum, v, s) => sum + v * prizeOf(pays, h, s), 0);
                  return (
                    <tr key={h}>
                      <td><strong>{h}/{k}</strong></td>
                      {game.bonus && [...current.grid[h]].reverse().map((v, i) => <td key={i}>{v ? fmt(v) : "-"}</td>)}
                      <td>{fmt(current.histogram[h])}</td>
                      {hasPrizes && <td>{pay ? <strong>{pay.toLocaleString("en-US")}</strong> : "-"}</td>}
                    </tr>
                  );
                })}
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
            <button disabled={busy} onClick={() => run(async () => setShown({ ...(await pickCheckLines(body, drawn, drawnBonus, minHits, exact, 0)), minHits, exact, drawn, drawnBonus }))}>Show lines</button>
          </div>
        </>
      )}
      {shown && (
        <div style={{ marginTop: 12 }}>
          <p className="muted">{fmt(shown.total)} lines with {shown.exact ? "exactly" : "at least"} {shown.minHits} hits (hits highlighted).</p>
          {shown.total > 0 && <LinesGrid key={shown.id} id={shown.id} total={shown.total} name="hits"
            hits={(line) => line.map((s) => (s.startsWith("+") ? shown.drawnBonus.includes(Number(s.slice(1))) : shown.drawn.includes(Number(s))))} />}
        </div>
      )}
    </section>
  );
}

export function OddsPanel({ game, prizes }) {
  const main = hitOdds(size(game), game.pick, game.draw);
  const bonus = game.bonus ? hitOdds(game.bonus.max - game.bonus.min + 1, game.bonus.pick, game.bonus.draw) : [{ hits: 0, ways: 1n, total: 1n }];
  const pays = payouts(prizes, game);
  const rows = [];
  for (const m of [...main].reverse())
    for (const b of [...bonus].reverse()) rows.push({ h: m.hits, s: b.hits, ways: m.ways * b.ways, total: m.total * b.total });
  const hasPrizes = pays.some((p) => (Array.isArray(p) ? p.some(Boolean) : p));
  const expected = rows.reduce((sum, r) => sum + prizeOf(pays, r.h, r.s) * (Number(r.ways) / Number(r.total)), 0);
  return (
    <section className="card">
      <h2>Odds</h2>
      <p className="muted">
        One line of {game.pick} numbers from {bounds(game).min}-{bounds(game).max}, {game.draw} drawn
        {game.bonus ? `; plus ${game.bonus.pick} ${game.bonus.label} from ${game.bonus.min}-${game.bonus.max}, ${game.bonus.draw} drawn` : ""}.
      </p>
      <div className="table-scroll">
        <table>
          <thead><tr><th>Hits</th><th>Chance</th><th>Probability</th>{hasPrizes && <th>Prize</th>}</tr></thead>
          <tbody>
            {rows.filter((r) => r.ways > 0n).map((r) => (
              <tr key={`${r.h}+${r.s}`}>
                <td><strong>{r.h}/{game.pick}{game.bonus ? ` + ${r.s}` : ""}</strong></td>
                <td>{oneIn(r.ways, r.total)}</td>
                <td className="muted">{(Number(r.ways) / Number(r.total) * 100).toPrecision(4)}%</td>
                {hasPrizes && <td>{prizeOf(pays, r.h, r.s) ? fmt(prizeOf(pays, r.h, r.s)) : "-"}</td>}
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
  const { min, max } = bounds(game);
  const [text, setText] = useState("");
  const [usePool, setUsePool] = useState(false);
  const [pool, setPool] = useState([]);
  const [bonusPool, setBonusPool] = useState([]);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const lines = text.split("\n").map((l) => l.trim()).filter(Boolean);
  const example = range(min, min + game.pick - 1).join(" ") + (game.bonus ? " | " + range(game.bonus.min, game.bonus.min + game.bonus.pick - 1).join(" ") : "");

  async function find() {
    setError(null);
    try {
      setResult(await pickPosition(game, lines, usePool ? pool : null, usePool ? bonusPool : null));
    } catch (e) {
      setError(e.message);
      setResult(null);
    }
  }
  return (
    <section className="card">
      <h2>Find position</h2>
      <p className="muted">Where a line is among all lines, in order ({example} first{game.bonus ? `; ${game.bonus.label} after a |` : ""}).</p>
      <div className="row">
        <label><input type="radio" checked={!usePool} onChange={() => setUsePool(false)} /> all lines of the game</label>
        <label><input type="radio" checked={usePool} onChange={() => setUsePool(true)} /> a system on my numbers</label>
      </div>
      {usePool && (
        <div style={{ marginTop: 8 }}>
          <NumberPad min={min} max={max} selected={pool} onChange={setPool} randomCount={game.pick + 2} label="Pool" />
          {game.bonus && <div style={{ marginTop: 8 }}><NumberPad min={game.bonus.min} max={game.bonus.max} selected={bonusPool} onChange={setBonusPool} randomCount={game.bonus.pick + 1} label={`${game.bonus.label} pool`} /></div>}
        </div>
      )}
      <textarea rows={4} value={text} onChange={(e) => setText(e.target.value)} placeholder={`${example}   (one line per row)`}
        aria-label="Lines to find" className="code" style={{ marginTop: 8 }} />
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

/** Odd/even and last-digit make-up of a System entry, and number frequencies over the whole slip. */
export function AnalysisPanel({ game, body, entries }) {
  const systems = entries.map((e, i) => (e.type === "system" ? i : -1)).filter((i) => i >= 0);
  const [chosen, setChosen] = useState(-1);
  const entry = systems.includes(chosen) ? chosen : systems[0] ?? -1;
  const [data, setData] = useState(null);
  const [freq, setFreq] = useState(null);
  const [error, setError] = useState(null);
  const k = game.pick;
  const key = JSON.stringify([body, entry]);
  const current = data?.key === key ? data.value : null;
  const currentFreq = freq?.key === JSON.stringify(body) ? freq.value : null;

  async function run(fn) {
    setError(null);
    try {
      await fn();
    } catch (e) {
      setError(e.message);
    }
  }
  const pct = (v, total) => `${((Number(v) / Number(total)) * 100).toLocaleString("en-US", { maximumFractionDigits: 2 })}%`;
  return (
    <>
      <section className="card">
        <h2>Odd / even and last digit</h2>
        <p className="muted">For a System entry: how many of its lines have 0, 1, … odd numbers, and 0, 1, … numbers ending in each digit. Exact formulas -- no line is built.</p>
        {systems.length === 0 ? <p className="muted">Add a System entry first.</p> : (
          <div className="row">
            <label>Entry{" "}
              <select value={entry} onChange={(e) => setChosen(Number(e.target.value))} aria-label="System entry" style={{ maxWidth: "100%" }}>
                {systems.map((i) => <option key={i} value={i}>{i + 1}. {describeEntry(entries[i])}</option>)}
              </select>
            </label>
            <button onClick={() => run(async () => setData({ key, value: await pickAnalysis(body, entry) }))}>Analyse</button>
          </div>
        )}
        {current && (
          <>
            <p>Lines: <strong>{fmt(current.total)}</strong> · more odd: {fmt(current.oddEven.moreOdd)} · more even: {fmt(current.oddEven.moreEven)}{k % 2 === 0 && ` · equal: ${fmt(current.oddEven.equal)}`}</p>
            <div className="two-col">
              <div className="table-scroll">
                <table>
                  <thead><tr><th>Odd</th><th>Even</th><th>Lines</th><th>%</th></tr></thead>
                  <tbody>
                    {current.oddEven.byOdd.map((v, j) => ({ v, j })).filter((r) => r.v > 0).map(({ v, j }) => (
                      <tr key={j}><td>{j}</td><td>{k - j}</td><td>{fmt(v)}</td><td className="muted">{pct(v, current.total)}</td></tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="table-scroll">
                <table>
                  <thead><tr><th>Ends in</th><th>Numbers</th>{range(0, k).map((j) => <th key={j}>{j} of them</th>)}</tr></thead>
                  <tbody>
                    {current.lastDigit.filter((d) => d.size > 0).map((d) => (
                      <tr key={d.digit}><td><strong>{d.digit}</strong></td><td>{d.size}</td>{d.byCount.map((v, j) => <td key={j}>{v ? fmt(v) : "-"}</td>)}</tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </>
        )}
      </section>
      <section className="card">
        <h2>Frequency</h2>
        <p className="muted">In how many lines of the whole slip every number appears.</p>
        <button disabled={!entries.length} onClick={() => run(async () => setFreq({ key: JSON.stringify(body), value: await pickFrequency(body) }))}>Count</button>
        {currentFreq && (
          <div className="two-col" style={{ marginTop: 10 }}>
            {[["Numbers", currentFreq.main], ...(game.bonus ? [[game.bonus.label, currentFreq.bonus]] : [])].map(([title, rows]) => (
              <div key={title} className="table-scroll">
                <h3>{title}</h3>
                <table>
                  <thead><tr><th>Number</th><th>Lines</th><th>% of lines</th></tr></thead>
                  <tbody>{rows.map((r) => <tr key={r.number}><td><strong>{r.number}</strong></td><td>{fmt(r.count)}</td><td className="muted">{pct(r.count, currentFreq.total)}</td></tr>)}</tbody>
                </table>
              </div>
            ))}
          </div>
        )}
      </section>
      {error && <p className="error">{error}</p>}
    </>
  );
}
