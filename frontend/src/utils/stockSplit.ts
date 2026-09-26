/**
 * Parse a stock-split ratio entered as "new:old" (e.g. "3:1" forward, "1:8" reverse). A plain number
 * is treated as N:1 (e.g. "3" → 3:1). Returns null when the input can't be parsed or isn't positive.
 */
export function parseSplitRatio(input: string): { numerator: number; denominator: number } | null {
  if (input == null) return null;
  const m = input.trim().match(/^(\d+(?:\.\d+)?)\s*(?::\s*(\d+(?:\.\d+)?))?$/);
  if (!m) return null;
  const numerator = Number(m[1]);
  const denominator = m[2] ? Number(m[2]) : 1;
  if (!(numerator > 0) || !(denominator > 0)) return null;
  return { numerator, denominator };
}
