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

import oidc from '@uportal/open-id-connect'

class ApiError extends Error {
  response: { data: any, status: number }

  constructor(data: any, status: number) {
    super(`API error ${status}`)
    this.response = { data, status }
  }
}

function getCookie(name: string): string | null {
  const match = document.cookie.match(
    new RegExp('(?:^|; )' + name + '=([^;]*)')
  )
  return match ? decodeURIComponent(match[1]) : null
}

async function getToken(userInfoApiUrl: string): Promise<string | undefined> {
  try {
    const { encoded } = await oidc({ userInfoApiUrl })

    return encoded
  }
  catch (error) {
    console.error('error: ', error)
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
  const csrfToken = getCookie('XSRF-TOKEN')
  const response = await fetch(`${baseUrl}/change-password`, {
    method: 'POST',
    body: JSON.stringify({ oldPass, newPass, confirmPass }),
    headers: {
      'content-type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
  })
  await throwIfNotOk(response, `${baseUrl}/change-password`)
  return { data: await response.json().catch(() => null) }
}

async function updateEmail(
  baseUrl: string,
  email: string,
  confirmEmail: string,
) {
  const csrfToken = getCookie('XSRF-TOKEN')
  const response = await fetch(`${baseUrl}/update-email`, {
    method: 'PUT',
    body: JSON.stringify({ email, confirmEmail }),
    headers: {
      'content-type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
  })
  await throwIfNotOk(response, `${baseUrl}/update-email`)
  return { data: await response.json().catch(() => null) }
}

async function updateFonctionDateFin(
  baseUrl: string,
  idFonction: string | number,
  active: boolean,
) {
  const csrfToken = getCookie('XSRF-TOKEN')
  const response = await fetch(`${baseUrl}/fonction/${idFonction}/dateFin`, {
    method: 'PUT',
    body: JSON.stringify(active),
    headers: {
      'content-type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
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
  const csrfToken = getCookie('XSRF-TOKEN')
  const response = await fetch(`${baseUrl}/avatar`, {
    method: 'POST',
    headers: {
      'X-XSRF-TOKEN': csrfToken,
    },
    body: formData,
  })
  await throwIfNotOk(response, `${baseUrl}/avatar`)
  return { data: await response.json().catch(() => null) }
}

async function verifyEmail(
  baseUrl: string,
  code: string,
) {
  const csrfToken = getCookie('XSRF-TOKEN')
  const response = await fetch(`${baseUrl}/verify-email`, {
    method: 'POST',
    body: JSON.stringify({ code }),
    headers: {
      'content-type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
  })
  await throwIfNotOk(response, `${baseUrl}/verify-email`)
  return { data: await response.json().catch(() => null) }
}

async function getPreferences(url: string, preferencesData: any, userInfoApiUrl: string) {
  const token = await getToken(userInfoApiUrl)
  const response = await fetch(url, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
      'content-type': 'application/json',
    },
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

async function postPreferences(url: string, preferencesData: any, userInfoApiUrl: string) {
  const token = await getToken(userInfoApiUrl)
  const csrfToken = getCookie('XSRF-TOKEN')
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'content-type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
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

export { getDetailEnfant, getMCE, getPreferences, getServicesEnt, postPassword, postPreferences, updateAvatar, updateEmail, updateFonctionDateFin, verifyEmail }
