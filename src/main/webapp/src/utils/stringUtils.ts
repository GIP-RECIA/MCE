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

function capitalize(
  value: string,
): string {
  return value.charAt(0).toUpperCase() + value.slice(1).toLowerCase()
}

function concatenate(
  values: (string | undefined)[],
  separator = '',
): string {
  return values
    .filter((v): v is string => !!v && v.trim() !== '')
    .join(separator)
}

function slugify(
  value: string,
): string {
  return String(value)
    .normalize('NFKD') // split accented characters into their base characters and diacritical marks
    .replace(/[\u0300-\u036F]/g, '') // remove all the accents, which happen to be all in the \u03xx UNICODE block.
    .trim() // trim leading or trailing whitespace
    .toLowerCase() // convert to lowercase
    .replace(/[^a-z0-9 -]/g, '') // remove non-alphanumeric characters
    .replace(/\s+/g, '-') // replace spaces with hyphens
    .replace(/-+/g, '-') // remove consecutive hyphens
}

function normalize(value?: string): string {
  return (value ?? '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
}

function alphaSort(a: string, b: string, order: 'asc' | 'desc'): 0 | 1 | -1 {
  const normalizedA = normalize(a)
  const normalizedB = normalize(b)

  if (normalizedA === normalizedB)
    return 0

  if (order === 'asc')
    return normalizedA > normalizedB ? 1 : -1
  else
    return normalizedA < normalizedB ? 1 : -1
}

export {
  alphaSort,
  capitalize,
  concatenate,
  normalize,
  slugify,
}
