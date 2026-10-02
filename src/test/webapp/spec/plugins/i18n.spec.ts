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

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { defineComponent, h } from 'vue'
import { useI18n } from 'vue-i18n'

describe('i18n', () => {
  it('does it woks?', async () => {
    const wrapper = mount(
      defineComponent(() => {
        const { t } = useI18n()

        return () => {
          return h('div', t('toast.error.unknown'))
        }
      }),
    )
    expect(wrapper.text()).toBe('An unknown error has occurred')
  })
})
