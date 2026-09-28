import { useEffect, useState } from "react";
import { fetchLines } from "../api";
import { downloadText, stamp } from "../storage";

const PAGE = 50;
const EXPORT_MAX = 100_000;
const fmt = (v) => Number(v).toLocaleString("en-US");

/**
 * The lines of a built system, one page at a time from the server (nothing else is kept in the browser).
 * {@code hits(line)} may return, per cell, whether it is a hit -- those cells are highlighted and a hit
 * count column is added.
 */
export default function LinesGrid({ id, total, name = "wheellab", hits }) {
  const [page, setPage] = useState(0);
  const [data, setData] = useState({ key: null, lines: [], error: null });
  const [busy, setBusy] = useState(false);
  const pages = Math.max(1, Math.ceil(total / PAGE));
  const key = `${id}:${page}`;

  useEffect(() => {
    let cancelled = false;
    fetchLines(id, page * PAGE, PAGE)
      .then((r) => !cancelled && setData({ key, lines: r.lines, error: null }))
      .catch((e) => !cancelled && setData({ key, lines: [], error: e.message }));
    return () => {
      cancelled = true;
    };
  }, [id, page, key]);

  async function exportAll(kind) {
    setBusy(true);
    try {
      const all = [];
      for (let start = 0; start < Math.min(total, EXPORT_MAX); start += 1000) {
        const r = await fetchLines(id, start, Math.min(1000, EXPORT_MAX - start));
        all.push(...r.lines);
      }
      const text = kind === "csv"
        ? ["#," + all[0].map((_, i) => `M${i + 1}`).join(","), ...all.map((l, i) => `${i + 1},${l.join(",")}`)].join("\n")
        : all.map((l, i) => `${String(i + 1).padStart(String(all.length).length)}  ${l.join(" ")}`).join("\n");
      downloadText(`${name}_${stamp()}.${kind}`, text + "\n", kind === "csv" ? "text/csv" : "text/plain");
    } finally {
      setBusy(false);
    }
  }

  const loading = data.key !== key;
  const lines = loading ? [] : data.lines;
  return (
    <div>
      <div className="row" style={{ marginBottom: 8 }}>
        <button className="pill" onClick={() => setPage(0)} disabled={page === 0}>⇤ First</button>
        <button className="pill" onClick={() => setPage(page - 1)} disabled={page === 0}>← Prev</button>
        <span className="small">
          Page{" "}
          <input type="number" min={1} max={pages} value={page + 1} style={{ width: 80 }} aria-label="Page"
            onChange={(e) => setPage(Math.min(pages, Math.max(1, Number(e.target.value) || 1)) - 1)} />{" "}
          of {fmt(pages)} · {fmt(total)} lines
        </span>
        <button className="pill" onClick={() => setPage(page + 1)} disabled={page >= pages - 1}>Next →</button>
        <button className="pill" onClick={() => setPage(pages - 1)} disabled={page >= pages - 1}>Last ⇥</button>
        <button className="pill" onClick={() => exportAll("csv")} disabled={busy || total === 0}>⬇ CSV</button>
        <button className="pill" onClick={() => exportAll("txt")} disabled={busy || total === 0}>⬇ TXT</button>
      </div>
      {total > EXPORT_MAX && <p className="muted small">Downloads include the first {fmt(EXPORT_MAX)} lines.</p>}
      {data.error && data.key === key && <p className="error">{data.error}</p>}
      <div className="table-scroll">
        <table className="lines">
          <thead>
            <tr>
              <th>#</th>
              <th>Line</th>
              {hits && <th>Hits</th>}
            </tr>
          </thead>
          <tbody>
            {loading && <tr><td colSpan={3} className="muted">Loading…</td></tr>}
            {lines.map((line, i) => {
              const marks = hits ? hits(line) : null;
              return (
                <tr key={i}>
                  <td className="muted">{fmt(page * PAGE + i + 1)}</td>
                  <td>{line.map((s, j) => <span key={j} className={marks?.[j] ? "chip hit" : "chip"}>{s}</span>)}</td>
                  {marks && <td><strong>{marks.filter(Boolean).length}/{line.length}</strong></td>}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
