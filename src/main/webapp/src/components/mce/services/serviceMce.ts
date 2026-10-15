/**
 * Copyright (C) 2023 GIP-RECIA, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

class ApiError extends Error {
  response: { data: any, status: number }

  constructor(data: any, status: number) {
    super(`API error ${status}`)
    this.response = { data, status }
  }
}

async function throwIfNotOk(response: Response, _requestUrl?: string,): Promise<void> {
  if (!response.ok) {
    const data = await response.json().catch(() => ({}))
    throw new ApiError(data, response.status)
  }
}

async function fetchJson(url: string): Promise<{ data: any }> {
  const response = await fetch(url);
  await throwIfNotOk(response, url)
  const text = await response.text()
  try {
    return { data: JSON.parse(text) }
  }
  catch {
    throw new Error(`L'API a retourné une réponse invalide (attendu JSON). URL: ${url}`)
  }
}

async function getMCE(url: string) {
  return fetchJson(url)
}

async function getServicesEnt(url: string) {
  return fetchJson(url)
}

async function getDetailEnfant(url: string) {
  return fetchJson(url)
}

async function postPassword(
  baseUrl: string,
  oldPass: string,
  newPass: string,
  confirmPass: string,
) {
  const response = await fetch(`${baseUrl}/change-password`, {
    method: 'POST',
    body: JSON.stringify({ oldPass, newPass, confirmPass }),
  })
  await throwIfNotOk(response, `${baseUrl}/change-password`)
  return { data: await response.json().catch(() => null) }
}

export interface NetworkPasswordResetStatus {
  eligible: boolean
}

/**
 * Statut du parcours « mot de passe réseau » (compte CVDL ntPass sans mot de passe local).
 * L'éligibilité étant calculée côté serveur (groupes LDAP), ce GET détermine l'écran à afficher.
 */
async function getNetworkPasswordStatus(
  baseUrl: string,
): Promise<{ data: NetworkPasswordResetStatus }> {
  const url = `${baseUrl}/network-password/status`
  return getMCE(url)
}

/** Change le mot de passe réseau directement (compte authentifié, sans ancien mot de passe ni code). */
async function resetNetworkPassword(
  baseUrl: string,
  newPass: string,
  confirmPass: string,
) {
  const response = await fetch(`${baseUrl}/network-password/reset`, {
    method: 'POST',
    body: JSON.stringify({ newPassword: newPass, confirmPassword: confirmPass }),
  })
  await throwIfNotOk(response, `${baseUrl}/network-password/reset`)
  return { data: await response.json().catch(() => null) }
}

async function updateEmail(
  baseUrl: string,
  email: string,
  confirmEmail: string,
) {
  const response = await fetch(`${baseUrl}/update-email`, {
    method: 'PUT',
    body: JSON.stringify({ email, confirmEmail }),
  })
  await throwIfNotOk(response, `${baseUrl}/update-email`)
  return { data: await response.json().catch(() => null) }
}

async function updateFonctionDateFin(
  baseUrl: string,
  idFonction: string | number,
  active: boolean,
) {
  const response = await fetch(`${baseUrl}/fonction/${idFonction}/dateFin`, {
    method: 'PUT',
    body: JSON.stringify(active),
  })
  await throwIfNotOk(response, `${baseUrl}/fonction/${idFonction}/dateFin`)
  return { data: await response.json().catch(() => null) }
}

/**
 * Met à jour l'avatar de l'utilisateur
 * @param file Le fichier image (File ou Blob)
 * @param baseUrl URL de base de l'API
 */
async function updateAvatar(
  file: File | Blob,
  baseUrl: string,
) {
  const formData = new FormData()
  formData.append('file', file)
  const response = await fetch(`${baseUrl}/avatar`, {
    method: 'POST',
    body: formData,
  })
  await throwIfNotOk(response, `${baseUrl}/avatar`)
  return { data: await response.json().catch(() => null) }
}

async function verifyEmail(
  baseUrl: string,
  code: string,
) {
  const response = await fetch(`${baseUrl}/verify-email`, {
    method: 'POST',
    body: JSON.stringify({ code }),
  })
  await throwIfNotOk(response, `${baseUrl}/verify-email`)
  return { data: await response.json().catch(() => null) }
}

async function getPreferences(url: string, preferencesData: any) {
  const response = await fetch(url, {
    method: 'GET',
  })

  await throwIfNotOk(response, url)

  const text = await response.text()
  try {
    return { data: JSON.parse(text) }
  }
  catch {
    return { data: text }
  }
}

async function postPreferences(url: string, preferencesData: any) {
  const response = await fetch(url, {
    method: 'POST',
    body: JSON.stringify(preferencesData),
  })

  await throwIfNotOk(response, url)

  const text = await response.text()
  try {
    return { data: JSON.parse(text) }
  }
  catch {
    return { data: text }
  }
}

export { getDetailEnfant, getMCE, getNetworkPasswordStatus, getPreferences, getServicesEnt, postPassword, postPreferences, resetNetworkPassword, updateAvatar, updateEmail, updateFonctionDateFin, verifyEmail }
