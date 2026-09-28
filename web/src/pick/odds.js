// Exact probabilities with BigInt: one line of k numbers from N, d numbers drawn.

export function comb(n, k) {
  if (k < 0 || k > n) return 0n;
  let r = 1n;
  for (let i = 0; i < Math.min(k, n - k); i++) r = (r * BigInt(n - i)) / BigInt(i + 1);
  return r;
}

/** [{hits, ways, total}]: P(h hits) = C(k,h) C(N-k, d-h) / C(N, d). */
export function hitOdds(N, k, d) {
  const total = comb(N, d);
  return Array.from({ length: k + 1 }, (_, h) => ({ hits: h, ways: comb(k, h) * comb(N - k, d - h), total }));
}

/** "1 in 13,983,816" */
export function oneIn(ways, total) {
  if (ways === 0n) return "--";
  const x = Number(total) / Number(ways);
  return `1 in ${x >= 100 ? Math.round(x).toLocaleString("en-US") : x.toLocaleString("en-US", { maximumFractionDigits: 2 })}`;
}
