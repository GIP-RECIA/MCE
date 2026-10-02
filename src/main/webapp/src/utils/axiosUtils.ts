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

import type { ToastContainerOptions } from 'vue3-toastify'
import axios from 'axios'
import { toast } from 'vue3-toastify'
import i18n from '@/plugins/i18n.ts'

const { t } = i18n.global

const {
  VITE_API_URI,
  VITE_AXIOS_TIMEOUT,
} = import.meta.env

const instance = axios.create({
  baseURL: VITE_API_URI,
  timeout: VITE_AXIOS_TIMEOUT,
  withCredentials: true,
})

function errorHandler(
  e: any,
  toastOrI18n?: boolean | string,
): void {
  let showToast: boolean = typeof toastOrI18n == 'boolean' && toastOrI18n
  const i18nHandled: number[] = [401, 404, 500]
  let message: string, error: any

  if (axios.isAxiosError(e)) {
    if (
      typeof toastOrI18n == 'string'
      && toastOrI18n.trim().length > 0
    ) {
      message = toastOrI18n
      showToast = true
    }
    else {
      message = t(
        i18nHandled.includes(e.response?.status ?? -1)
          ? `toast.error.${e.response!.status}`
          : 'toast.error.unmanaged',
      )
    }
    error = e.message
  }
  else if (e instanceof Error) {
    message = t('toast.error.stock')
    error = e.message
  }
  else {
    message = t('toast.error.unknown')
    error = e
  }

  if (showToast) {
    toast.error(
      message,
      {
      } as ToastContainerOptions,
    )
  }
  console.error(error)
}

export {
  errorHandler,
  instance,
}
