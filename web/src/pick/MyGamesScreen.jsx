import { useEffect, useState } from "react";
import { pickParse, pickText } from "../api";
import { downloadText, loadLocal, pickTextFile, saveLocal, stamp } from "../storage";
import { PickWorkspace } from "./PickScreen";

const STORAGE_KEY = "wheellab.mygames";
const safeName = (s) => (s || "game").replace(/[^\p{L}\p{N}]+/gu, "_");

export const EXAMPLE = `# A game of your own. Lines starting with # are comments.
game "Star 5" numbers 1-50 pick 5 draw 5
bonus "Star" numbers 1-12 pick 2 draw 2
prize 5+2 = 1000000
prize 5+1 = 50000
prize 5 = 10000
prize 4+2 = 500
entry system 3 8 15 22 29 36 43 | 2 7 11
entry groups 1 2 3 4 take 2 ; 40 41 42 43 44 take 3 | 5 9
`;

let nextId = Date.now();
const newId = () => nextId++;

/** "5 from 1-50 + 2 Star from 1-12" */
export function describeGame(g) {
  return `${g.pick} from ${g.min}-${g.max}${g.draw !== g.pick ? ` (${g.draw} drawn)` : ""}${g.bonus ? ` + ${g.bonus.pick} ${g.bonus.label} from ${g.bonus.min}-${g.bonus.max}` : ""}`;
}

/** Client-side checks, same limits as the server. */
function gameErrors(g) {
  const out = [];
  const size = g.max - g.min + 1;
  if (!(g.min >= 0 && g.max > g.min && size <= 100)) out.push("Numbers: from < to, at most 100 numbers.");
  if (!(g.pick >= 1 && g.pick <= Math.min(20, size))) out.push(`Pick 1 to ${Math.min(20, Math.max(1, size))}.`);
  if (!(g.draw >= 1 && g.draw <= size)) out.push(`Draw 1 to ${Math.max(1, size)}.`);
  if (g.bonus) {
    const b = g.bonus, bs = b.max - b.min + 1;
    if (!b.label?.trim()) out.push("Give the bonus pool a name.");
    if (!(b.min >= 0 && b.max >= b.min && bs <= 100)) out.push("Bonus numbers: from <= to, at most 100.");
    if (!(b.pick >= 1 && b.pick <= Math.min(20, bs))) out.push(`Bonus pick 1 to ${Math.min(20, Math.max(1, bs))}.`);
    if (!(b.draw >= 1 && b.draw <= bs)) out.push(`Bonus draw 1 to ${Math.max(1, bs)}.`);
  }
  if (!g.title?.trim()) out.push("Give the game a name.");
  return out;
}

function GameForm({ initial, onSave, onCancel }) {
  const [g, setG] = useState(() => structuredClone(initial));
  const num = (v) => (v === "" ? "" : Number(v));
  const set = (patch) => setG({ ...g, ...patch });
  const setB = (patch) => setG({ ...g, bonus: { ...g.bonus, ...patch } });
  const clean = { ...g, min: Number(g.min), max: Number(g.max), pick: Number(g.pick), draw: Number(g.draw),
    ...(g.bonus ? { bonus: { ...g.bonus, min: Number(g.bonus.min), max: Number(g.bonus.max), pick: Number(g.bonus.pick), draw: Number(g.bonus.draw) } } : {}) };
  const errors = gameErrors(clean);
  const field = (label, value, onChange, width = 70, type = "number") => (
    <label>{label}{" "}<input type={type} value={value} style={{ width }} aria-label={label} onChange={(e) => onChange(type === "number" ? num(e.target.value) : e.target.value)} /></label>
  );
  return (
    <section className="card">
      <h2>Game settings</h2>
      <div className="row">{field("Name", g.title, (v) => set({ title: v }), 200, "text")}</div>
      <div className="row" style={{ marginTop: 8 }}>
        {field("Numbers from", g.min, (v) => set({ min: v }))}
        {field("to", g.max, (v) => set({ max: v }))}
        {field("Pick", g.pick, (v) => set({ pick: v }), 60)}
        {field("Drawn", g.draw, (v) => set({ draw: v }), 60)}
      </div>
      <label className="row" style={{ marginTop: 10 }}>
        <input type="checkbox" checked={!!g.bonus} onChange={(e) => set({ bonus: e.target.checked ? { label: "Bonus", min: 1, max: 10, pick: 1, draw: 1 } : undefined })} />
        A second pool of numbers (bonus, stars, …)
      </label>
      {g.bonus && (
        <div className="row" style={{ marginTop: 8 }}>
          {field("Bonus name", g.bonus.label, (v) => setB({ label: v }), 110, "text")}
          {field("from", g.bonus.min, (v) => setB({ min: v }))}
          {field("to", g.bonus.max, (v) => setB({ max: v }))}
          {field("Bonus pick", g.bonus.pick, (v) => setB({ pick: v }), 60)}
          {field("Bonus drawn", g.bonus.draw, (v) => setB({ draw: v }), 60)}
        </div>
      )}
      {errors.length > 0 && <ul className="error small">{errors.map((e) => <li key={e}>{e}</li>)}</ul>}
      <div className="row" style={{ marginTop: 10 }}>
        <button disabled={errors.length > 0} onClick={() => onSave(clean)}>Save game</button>
        <button className="pill" onClick={onCancel}>Cancel</button>
      </div>
    </section>
  );
}

function TextCard({ onAdd, onCancel }) {
  const [text, setText] = useState(() => loadLocal("wheellab.mygames.text", EXAMPLE));
  const [error, setError] = useState(null);
  useEffect(() => saveLocal("wheellab.mygames.text", text), [text]);

  async function create() {
    setError(null);
    try {
      const r = await pickParse(text);
      onAdd({ game: r.game, entries: r.entries, prizes: Object.fromEntries(Object.entries(r.prizes).map(([k, v]) => [k, String(v)])) });
    } catch (e) {
      setError(e.message);
    }
  }
  return (
    <section className="card">
      <h2>Game from text</h2>
      <p className="muted small">
        <code>game "Name" numbers 1-50 pick 5 draw 5</code> · optional <code>bonus "Star" numbers 1-12 pick 2 draw 2</code> ·
        {" "}<code>prize 5+2 = 1000000</code> (or <code>prize 5 = …</code> for any bonus hits) ·
        {" "}<code>entry single|system|wheel … | bonus</code>, <code>entry groups 1 2 3 take 2 ; 7 8 9 take 3</code>,
        {" "}<code>entry wheel 1 2 … 12 guarantee 3</code>.
      </p>
      <textarea className="code" rows={12} value={text} onChange={(e) => setText(e.target.value)} spellCheck={false} aria-label="Game text" />
      <div className="row" style={{ marginTop: 8 }}>
        <button onClick={create} disabled={!text.trim()}>Create game</button>
        <button className="pill" onClick={() => setText(EXAMPLE)}>Example</button>
        <button className="pill" onClick={async () => { const t = await pickTextFile(".txt,text/plain"); if (t != null) setText(t); }}>📂 Open .txt</button>
        <button className="pill" onClick={onCancel}>Cancel</button>
      </div>
      {error && <pre className="error">{error}</pre>}
    </section>
  );
}

/** "My Games": number games you define -- any range, pick, draw, and an optional bonus pool. */
export default function MyGamesScreen() {
  const [store, setStore] = useState(() => {
    const saved = loadLocal(STORAGE_KEY, null);
    return saved && Array.isArray(saved.games) ? saved : { games: [], selected: null };
  });
  const [mode, setMode] = useState(null); // null | "new" | "edit" | "text"
  const [note, setNote] = useState(null);
  const [error, setError] = useState(null);
  const { games } = store;
  const current = games.find((g) => g.id === store.selected) ?? null;

  useEffect(() => saveLocal(STORAGE_KEY, store), [store]);

  const update = (id, patch) => setStore({ ...store, games: games.map((g) => (g.id === id ? { ...g, ...patch } : g)) });
  const add = (g) => {
    const id = newId();
    setStore({ games: [...games, { id, entries: [], prizes: {}, ...g }], selected: id });
    setMode(null);
  };

  function saveSettings(game) {
    if (mode === "new") return add({ game });
    const same = JSON.stringify({ ...current.game, title: "" }) === JSON.stringify({ ...game, title: "" });
    if (!same && current.entries.length && !window.confirm("The numbers changed -- the entries of this game will be removed. Continue?")) return;
    update(current.id, same ? { game } : { game, entries: [], prizes: {} });
    setMode(null);
  }

  async function exportText(g) {
    setError(null);
    try {
      const r = await pickText({ game: g.game, entries: g.entries.length ? g.entries : undefined, prizes: g.prizes });
      downloadText(`${safeName(g.game.title)}.txt`, r.text);
    } catch (e) {
      setError(g.entries.length ? e.message : "Add at least one entry before exporting as text.");
    }
  }

  async function openFile() {
    setError(null);
    setNote(null);
    try {
      const text = await pickTextFile(".json,.txt,application/json,text/plain");
      if (text == null) return;
      if (text.trim().startsWith("{")) {
        const data = JSON.parse(text);
        const g = data.myGame ?? data;
        if (!g.game || !Array.isArray(g.entries)) throw new Error("Not a WheelLab game file.");
        add({ game: g.game, entries: g.entries, prizes: g.prizes ?? {} });
      } else {
        const r = await pickParse(text);
        add({ game: r.game, entries: r.entries, prizes: Object.fromEntries(Object.entries(r.prizes).map(([k, v]) => [k, String(v)])) });
      }
      setNote("Opened.");
    } catch (e) {
      setError(e.message);
    }
  }

  const blank = { title: "My game", min: 1, max: 40, pick: 5, draw: 5 };
  return (
    <>
      <section className="card">
        <h2>My Games</h2>
        <p className="muted">
          Number games you define: numbers from-to, how many per line, how many are drawn, and optionally a second pool (bonus, stars, …) --
          then play them with systems, groups and wheels, with your own prizes. They stay in this browser; save them as files to keep them.
        </p>
        <div className="row">
          <button className="pill" onClick={() => setMode("new")}>+ New game</button>
          <button className="pill" onClick={() => setMode("text")}>📝 From text</button>
          <button className="pill" onClick={openFile}>📂 Open file</button>
          {note && <span className="muted small">{note}</span>}
        </div>
        {error && <p className="error">{error}</p>}
        {games.length > 0 && (
          <div className="table-scroll" style={{ marginTop: 10 }}>
            <table>
              <thead><tr><th></th><th>Game</th><th>Numbers</th><th>Entries</th><th></th></tr></thead>
              <tbody>
                {games.map((g) => (
                  <tr key={g.id}>
                    <td><input type="radio" name="my-game" checked={g.id === store.selected} aria-label={`Select ${g.game.title}`}
                      onChange={() => { setStore({ ...store, selected: g.id }); setMode(null); }} /></td>
                    <td><strong>{g.game.title}</strong></td>
                    <td className="small">{describeGame(g.game)}</td>
                    <td>{g.entries.length}</td>
                    <td className="row" style={{ gap: 4, flexWrap: "nowrap" }}>
                      <button className="icon" title="Save as a file (JSON)" aria-label={`Save ${g.game.title}`}
                        onClick={() => downloadText(`${safeName(g.game.title)}_${stamp()}.json`, JSON.stringify({ app: "WheelLab", myGame: { game: g.game, entries: g.entries, prizes: g.prizes } }, null, 2), "application/json")}>💾</button>
                      <button className="icon" title="Export as text" aria-label={`Export ${g.game.title} as text`} onClick={() => exportText(g)}>⬇</button>
                      <button className="icon" title="Copy" aria-label={`Copy ${g.game.title}`}
                        onClick={() => add({ ...structuredClone(g), game: { ...g.game, title: `${g.game.title} (copy)` } })}>⧉</button>
                      <button className="icon" title="Delete" aria-label={`Delete ${g.game.title}`}
                        onClick={() => window.confirm(`Delete "${g.game.title}" from this browser?`) && setStore({ games: games.filter((x) => x.id !== g.id), selected: store.selected === g.id ? null : store.selected })}>✕</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {mode === "new" && <GameForm initial={blank} onSave={saveSettings} onCancel={() => setMode(null)} />}
      {mode === "edit" && current && <GameForm initial={current.game} onSave={saveSettings} onCancel={() => setMode(null)} />}
      {mode === "text" && <TextCard onAdd={add} onCancel={() => setMode(null)} />}

      {!mode && current && (
        <PickWorkspace key={current.id} state={current} fileName={safeName(current.game.title)}
          onState={(s) => update(current.id, { entries: s.entries, prizes: s.prizes })}
          settings={(
            <section className="card">
              <div className="row" style={{ justifyContent: "space-between" }}>
                <h2>{current.game.title} <span className="muted small" style={{ fontWeight: 400 }}>{describeGame(current.game)}</span></h2>
                <button className="pill" onClick={() => setMode("edit")}>⚙ Settings</button>
              </div>
            </section>
          )} />
      )}
      {!mode && !current && games.length > 0 && <p className="muted">Select a game above.</p>}
    </>
  );
}
