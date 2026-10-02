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

import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome'
import { ResizeObserver } from '@juggle/resize-observer'
import { config } from '@vue/test-utils'
// @ts-expect-error project location
import i18n from '@/plugins/i18n.ts'

globalThis.ResizeObserver = ResizeObserver

config.global.plugins = [
  i18n,
]

config.global.stubs = {
  FontAwesomeIcon,
}

if (!window.visualViewport) {
  Object.defineProperty(window, 'visualViewport', {
    value: {
      width: 1024,
      height: 768,
      offsetTop: 0,
      offsetLeft: 0,
      scale: 1,
      addEventListener: () => {},
      removeEventListener: () => {},
    },
  })
}
