/** Round each displayed endpoint outwards; exact server numbers remain available below. */
export function intervalRange(lower: number, upper: number): string {
  const endpoint = (value: number, up: boolean) => {
    if (value === 0) return "0";
    const [mantissa, power] = Math.abs(value).toExponential(17).split("e");
    const source = mantissa.replace(".", "");
    let digits = source.slice(0, 6);
    let exponent = Number(power);
    if ((value > 0 ? up : !up) && /[1-9]/.test(source.slice(6))) digits = String(BigInt(digits) + 1n);
    if (digits.length > 6) { digits = digits.slice(0, 6); exponent++; }
    const sign = value < 0 ? "-" : "";
    if (exponent < -5 || exponent > 8) return `${sign}${digits[0]}.${digits.slice(1)}e${exponent}`;
    const place = exponent + 1;
    const decimal = place <= 0 ? `0.${"0".repeat(-place)}${digits}` : place >= digits.length
      ? digits + "0".repeat(place - digits.length) : `${digits.slice(0, place)}.${digits.slice(place)}`;
    return sign + decimal.replace(/(\.\d*?)0+$/, "$1").replace(/\.$/, "");
  };
  return `${endpoint(lower, false)} – ${endpoint(upper, true)} bb`;
}
