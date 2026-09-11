export const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
export const CODE_RE = /^\d{6}$/;

export function passwordStrength(pw: string): number {
  let types = 0;
  if (/[a-z]/.test(pw)) types++;
  if (/[A-Z]/.test(pw)) types++;
  if (/[0-9]/.test(pw)) types++;
  if (/[^a-zA-Z0-9]/.test(pw)) types++;
  return types;
}