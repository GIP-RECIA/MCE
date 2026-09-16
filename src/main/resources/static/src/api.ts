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

const PAGE_SEGMENTS = /\/?(activation|mot-de-passe-oublie)\/?$/;

const TOKEN_STORAGE_KEY = 'mce-charte-token';

function readUrlToken(): string | null {
  return new URLSearchParams(window.location.search).get('token');
}

/**
 * Jeton OIDC transmis par le portlet via sessionStorage (primaire) ou le paramètre `token`
 * de l'URL (repli si sessionStorage indisponible). Le SPA d'activation est servi de façon
 * publique ; sans ce jeton, le backend ne peut pas résoudre l'utilisateur connecté
 * (l'écran LOGIN s'affiche au lieu du SSO).
 */
function readToken(): string | null {
  try {
    const stored = sessionStorage.getItem(TOKEN_STORAGE_KEY);
    if (stored) {
      return stored;
    }
  } catch {
    // sessionStorage indisponible : on s'appuie sur l'URL.
  }
  return readUrlToken();
}

const urlToken = readToken();

/**
 * Consomme le jeton : il est retiré de sessionStorage et de l'URL après lecture pour ne pas
 * le laisser transiter/dormir dans l'historique du navigateur. Il reste en mémoire pour
 * la session du SPA.
 */
function cleanToken() {
  try {
    sessionStorage.removeItem(TOKEN_STORAGE_KEY);
  } catch {
    // sessionStorage indisponible : rien à nettoyer.
  }
  const url = new URL(window.location.href);
  if (url.searchParams.has('token')) {
    url.searchParams.delete('token');
    window.history.replaceState({}, '', url.toString());
  }
}

function authHeaders(): Record<string, string> {
  return urlToken ? { 'Authorization': `Bearer ${urlToken}` } : {};
}

cleanToken();

export function rootPath(): string {
  const p = window.location.pathname || '/';
  return p.replace(PAGE_SEGMENTS, '').replace(/\/+$/, '');
}

function apiBase(): string {
  return rootPath() + '/api/personne/mce';
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
  err.retryAfterSeconds = payload.retryAfterSeconds as number | undefined;
  err.resetToken = typeof payload.resetToken === 'string' ? payload.resetToken : undefined;
  return err;
}

/** GET renvoyant du texte brut (ex. /debug-id qui répond en text/plain). */
export function getText(path: string): Promise<string> {
  return fetch(url(path), { headers: authHeaders() }).then((resp) => {
    if (!resp.ok) {
      return toJson<Record<string, unknown>>(resp).then((payload) => {
        throw toError(payload);
      });
    }
    return resp.text();
  });
}

export function getJson<T>(path: string): Promise<T> {
  return fetch(url(path), { headers: authHeaders() }).then((resp) => {
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
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(body)
  }).then((resp) => toJson<T>(resp).then((payload) => {
    if (!resp.ok) {
      throw toError(payload as unknown as Record<string, unknown>);
    }
    return payload;
  }));
}