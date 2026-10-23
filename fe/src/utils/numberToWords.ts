const DIGITS = ['không', 'một', 'hai', 'ba', 'bốn', 'năm', 'sáu', 'bảy', 'tám', 'chín'];

/** đọc một nhóm 3 chữ số; full = có nhóm cao hơn đứng trước nên phải đọc đủ “không trăm”, “lẻ” */
function readGroup(n: number, full: boolean): string {
  const h = Math.floor(n / 100), t = Math.floor((n % 100) / 10), u = n % 10;
  const w: string[] = [];
  if (h > 0 || full) w.push(`${DIGITS[h]} trăm`);
  if (t > 1) w.push(`${DIGITS[t]} mươi`);
  else if (t === 1) w.push('mười');
  else if (u > 0 && (h > 0 || full)) w.push('lẻ');
  if (u > 0) w.push(u === 1 && t > 1 ? 'mốt' : u === 5 && t > 0 ? 'lăm' : DIGITS[u]);
  return w.join(' ');
}

const groupUnit = (i: number) => (i === 0 ? '' : i % 3 === 0 ? 'tỷ' : i % 3 === 1 ? 'nghìn' : 'triệu');

/**
 * Đọc số tiền VND bằng chữ tiếng Việt, chữ cái đầu viết hoa.
 * 150000000 -> "Một trăm năm mươi triệu đồng"; 1005000 -> "Một triệu không trăm lẻ năm nghìn đồng";
 * 21 -> "Hai mươi mốt đồng"; 15 -> "Mười lăm đồng"; 0 -> "Không đồng"; 2000000000 -> "Hai tỷ đồng".
 */
export function moneyInWords(value: number): string {
  const n = Math.max(0, Math.round(value));
  if (n === 0) return 'Không đồng';
  const groups: number[] = [];
  for (let r = n; r > 0; r = Math.floor(r / 1000)) groups.push(r % 1000);
  const parts: string[] = [];
  for (let i = groups.length - 1; i >= 0; i--) {
    const g = groups[i];
    const higherNonZero = groups.slice(i + 1).some(x => x > 0);
    const unit = groupUnit(i);
    if (g === 0) {
      if (i > 0 && i % 3 === 0 && higherNonZero) parts.push(unit); // “nghìn tỷ”
      continue;
    }
    parts.push(readGroup(g, i < groups.length - 1), unit);
  }
  const s = `${parts.filter(Boolean).join(' ')} đồng`;
  return s.charAt(0).toUpperCase() + s.slice(1);
}
