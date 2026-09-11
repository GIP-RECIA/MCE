export interface ActivationConnexionResult {
  uid: string;
}

export interface ActivationStatus {
  etapeSuivante: string;
  charteRequise: boolean;
  emailRequise: boolean;
  passwordRequise: boolean;
}

export interface CharteStatus {
  charteRequired: boolean;
  charteUrl?: string;
  charteSignee?: boolean;
}

export interface ReportedError extends Error {
  code?: string;
  charteUrl?: string;
  charteRequired?: boolean;
  retryAfterSeconds?: number;
  resetToken?: string;
}

export interface StructuresResponse {
  nom: string;
  siren: string;
  ville: string;
  surtype: string;
}

export interface RecoverUidResult {
  resetToken?: string;
  charteRequired?: boolean;
  charteUrl?: string;
}

function apiBase(): string {
  const p = window.location.pathname || '/';
  const stripped = p.replace(/\/?(activation|mot-de-passe-oublie)\/?$/, '') || '/';
  return stripped.replace(/\/$/, '') + '/api/personne/mce';
}

export function url(path: string): string {
  return apiBase() + path;
}

function toJson<T>(resp: Response): Promise<T> {
  return resp.json().catch(() => ({}) as T);
}

function toError(payload: Record<string, unknown>): ReportedError {
  const err = new Error((payload && payload.message) ? String(payload.message) : 'Une erreur est survenue') as ReportedError;
  err.code = typeof payload.code === 'string' ? payload.code : undefined;
  err.charteUrl = typeof payload.charteUrl === 'string' ? payload.charteUrl : undefined;
  err.charteRequired = payload.charteRequired as boolean | undefined;
  err.retryAfterSeconds = payload.retryAfterSeconds as number | undefined;
  err.resetToken = typeof payload.resetToken === 'string' ? payload.resetToken : undefined;
  return err;
}

export function getJson<T>(path: string): Promise<T> {
  return fetch(url(path)).then((resp) => {
    if (!resp.ok) {
      return toJson<Record<string, unknown>>(resp).then((payload) => {
        throw toError(payload);
      });
    }
    return resp.json() as Promise<T>;
  });
}

export function post<T>(path: string, body: unknown): Promise<T> {
  return fetch(url(path), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  }).then((resp) => toJson<T>(resp).then((payload) => {
    if (!resp.ok) {
      throw toError(payload as unknown as Record<string, unknown>);
    }
    return payload;
  }));
}