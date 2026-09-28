import { useEffect, useRef, useState } from "react";
import { pickBuild, pickCount } from "../api";
import LinesGrid from "../components/LinesGrid";
import { downloadText, loadLocal, pickTextFile, saveLocal, stamp } from "../storage";
import EntryEditor, { describeEntry } from "./EntryEditor";
import { AnalysisPanel, CheckPanel, OddsPanel, PositionPanel } from "./PickPanels";

const fmt = (v) => Number(v).toLocaleString("en-US");
const TABS = [["build", "🎯 Build"], ["check", "✅ Check"], ["odds", "📊 Odds"], ["analysis", "📈 Analysis"], ["position", "🔍 Position"]];

/**
 * The work area of a number game: entries (single lines, systems, groups, wheels), check a draw, odds,
 * analyses and line positions. The state {game, entries, prizes} is owned by the caller.
 * {@code settings} (optional) renders the game settings row.
 */
export function PickWorkspace({ state, onState, settings, fileName = "game" }) {
  const [tab, setTab] = useState("build");
  const [counted, setCounted] = useState(null);
  const [build, setBuild] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const seq = useRef(0);
  const { game, entries } = state;
  const prizes = state.prizes ?? {};
  const body = { game, entries };
  const bodyKey = JSON.stringify(body);

  useEffect(() => {
    const my = ++seq.current;
    if (!JSON.parse(bodyKey).entries.length) return;
    const timer = setTimeout(() => {
      pickCount(JSON.parse(bodyKey))
        .then((r) => my === seq.current && setCounted({ key: bodyKey, data: r, error: null }))
        .catch((e) => my === seq.current && setCounted({ key: bodyKey, data: null, error: e.message }));
    }, 250);
    return () => clearTimeout(timer);
  }, [bodyKey]);
  const count = entries.length && counted?.key === bodyKey ? counted : null;

  function change(patch) {
    onState({ ...state, ...patch });
    setBuild(null);
  }

  async function doBuild() {
    setBusy(true);
    setError(null);
    try {
      setBuild(await pickBuild(body));
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <div className="tabs" role="tablist">
        {TABS.map(([id, label]) => (
          <button key={id} role="tab" aria-selected={tab === id} className={tab === id ? "pill selected" : "pill"} onClick={() => setTab(id)}>{label}</button>
        ))}
      </div>
      {settings}

      {tab === "build" && (
        <>
          <EntryEditor key={JSON.stringify(game)} game={game} onAdd={(e) => change({ entries: [...entries, e] })} />
          <section className="card">
            <h2>Entries -- {entries.length}{count?.data ? ` · ${fmt(count.data.total)} lines` : ""}</h2>
            {entries.length === 0 && <p className="muted">No entries yet -- add one above.</p>}
            <ol className="entry-list">
              {entries.map((e, i) => (
                <li key={i}>
                  {describeEntry(e)}{count?.data && <span className="muted"> · {fmt(count.data.entries[i].count)} lines</span>}{" "}
                  <button className="icon" aria-label={`Remove entry ${i + 1}`} onClick={() => change({ entries: entries.filter((_, j) => j !== i) })}>✕</button>
                </li>
              ))}
            </ol>
            {count?.error && <p className="error">{count.error}</p>}
            <div className="row" style={{ marginTop: 8 }}>
              <button onClick={doBuild} disabled={busy || !entries.length || !count?.data}>{busy ? "Building…" : "Build lines"}</button>
              {entries.length > 0 && <button className="pill" onClick={() => window.confirm("Remove all entries?") && change({ entries: [] })}>Clear all</button>}
            </div>
            {error && <p className="error">{error}</p>}
          </section>
          {build && (
            <section className="card">
              <h2>Lines -- {fmt(build.total)}</h2>
              <LinesGrid key={build.id} id={build.id} total={build.total} name={fileName} />
            </section>
          )}
        </>
      )}
      {tab === "check" && <CheckPanel game={game} body={body} entries={entries} prizes={prizes} onPrizes={(p) => onState({ ...state, prizes: p })} disabled={!entries.length || !count?.data} />}
      {tab === "odds" && <OddsPanel game={game} prizes={prizes} />}
      {tab === "analysis" && <AnalysisPanel game={game} body={body} entries={entries} />}
      {tab === "position" && <PositionPanel game={game} />}
    </>
  );
}

/**
 * LOTTO / KENO: a preset number game, remembered in the browser. {@code preset}:
 *   {id, title, intro, game: {numbers, pick, draw}, drawEqualsPick, numbersRange: [min, max] | null, pickRange, pickLabel?}
 */
export default function PickScreen({ preset }) {
  const storageKey = `wheellab.${preset.id}`;
  const [state, setState] = useState(() => {
    const saved = loadLocal(storageKey, null);
    return saved && saved.game && Array.isArray(saved.entries) ? saved : { game: preset.game, entries: [], prizes: {} };
  });
  const [error, setError] = useState(null);
  const { game, entries } = state;

  useEffect(() => saveLocal(storageKey, state), [storageKey, state]);

  function setGame(patch) {
    const next = { ...game, ...patch };
    if (preset.drawEqualsPick) next.draw = next.pick;
    if (entries.length && !window.confirm("Changing the game clears your entries. Continue?")) return;
    setState({ game: next, entries: [], prizes: {} });
  }

  async function openFile() {
    setError(null);
    try {
      const text = await pickTextFile();
      if (text == null) return;
      const data = JSON.parse(text)[preset.id];
      if (!data?.game || !Array.isArray(data.entries)) throw new Error(`Not a ${preset.title} file.`);
      setState({ game: data.game, entries: data.entries, prizes: data.prizes ?? {} });
    } catch (e) {
      setError(e.message);
    }
  }

  const settings = (
    <section className="card">
      <h2>{preset.title}</h2>
      <p className="muted">{preset.intro}</p>
      <div className="row">
        {preset.numbersRange && (
          <label>
            Numbers 1 to{" "}
            <input type="number" min={preset.numbersRange[0]} max={preset.numbersRange[1]} value={game.numbers} style={{ width: 70 }}
              aria-label="Highest number" onChange={(e) => setGame({ numbers: Math.max(preset.numbersRange[0], Math.min(preset.numbersRange[1], Number(e.target.value) || preset.numbersRange[0])) })} />
          </label>
        )}
        <label>
          {preset.pickLabel ?? "Numbers per line"}{" "}
          <select value={game.pick} aria-label={preset.pickLabel ?? "Numbers per line"} onChange={(e) => setGame({ pick: Number(e.target.value) })}>
            {Array.from({ length: preset.pickRange[1] - preset.pickRange[0] + 1 }, (_, i) => preset.pickRange[0] + i)
              .filter((k) => k <= game.numbers).map((k) => <option key={k} value={k}>{k}</option>)}
          </select>
        </label>
        <span className="muted small">{game.draw} numbers drawn</span>
        <button className="pill" onClick={() => downloadText(`${preset.id}_${stamp()}.json`, JSON.stringify({ app: "WheelLab", [preset.id]: state }, null, 2), "application/json")}>💾 Save</button>
        <button className="pill" onClick={openFile}>📂 Open</button>
      </div>
      {error && <p className="error">{error}</p>}
    </section>
  );
  return <PickWorkspace state={state} onState={setState} settings={settings} fileName={preset.id} />;
}
