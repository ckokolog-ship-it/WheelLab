/**
 * A grid of the numbers min..max to toggle. {@code blocked} numbers are shown crossed out and cannot be
 * chosen; {@code limit} stops choosing more than that many; "Random" picks {@code randomCount} numbers.
 */
export default function NumberPad({ min = 1, max, selected, onChange, blocked = [], limit, randomCount, label = "Number" }) {
  const chosen = new Set(selected);
  const off = new Set(blocked);
  const nums = Array.from({ length: max - min + 1 }, (_, i) => min + i);

  function toggle(n) {
    if (chosen.has(n)) return onChange(selected.filter((x) => x !== n));
    if (off.has(n) || (limit && selected.length >= limit)) return;
    onChange([...selected, n].sort((a, b) => a - b));
  }
  function random() {
    const free = nums.filter((n) => !off.has(n));
    for (let i = free.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [free[i], free[j]] = [free[j], free[i]];
    }
    onChange(free.slice(0, randomCount).sort((a, b) => a - b));
  }

  return (
    <div>
      <div className="pad">
        {nums.map((n) => (
          <button key={n} type="button" className={chosen.has(n) ? "num on" : off.has(n) ? "num blocked" : "num"}
            disabled={off.has(n) && !chosen.has(n)} aria-pressed={chosen.has(n)} aria-label={`${label} ${n}`} onClick={() => toggle(n)}>
            {n}
          </button>
        ))}
      </div>
      <div className="row small" style={{ marginTop: 6 }}>
        <span className="muted">{selected.length} chosen{limit ? ` of ${limit}` : ""}</span>
        {randomCount > 0 && <button type="button" className="pill" onClick={random}>🎲 Random {randomCount}</button>}
        {selected.length > 0 && <button type="button" className="pill" onClick={() => onChange([])}>Clear</button>}
      </div>
    </div>
  );
}
