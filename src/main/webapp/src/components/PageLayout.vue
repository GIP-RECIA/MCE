<!--
 Copyright (C) 2023 GIP-RECIA, Inc.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

<script setup lang="ts">
import { faArrowLeft } from '@fortawesome/free-solid-svg-icons'
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome'

defineProps<{
  title: string
}>()
</script>

<template>
  <div class="page-layout">
    <header>
      <div class="heading">
        <slot name="back">
          <router-link
            :to="{ name: 'index' }"
            class="btn-tertiary circle"
            title="Retour à l'accueil de l'application"
          >
            <FontAwesomeIcon
              :icon="faArrowLeft"
            />
          </router-link>
        </slot>
        <h1>{{ title }}</h1>
      </div>
      <slot name="header" />
    </header>
    <slot />
  </div>
</template>

<style scoped lang="scss">
@use 'sass:map';
@use '@/assets/scoped' as *;

.page-layout {
  display: grid;
  gap: 32px;

  > header {
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    align-items: center;
    gap: 24px;

    > .heading {
      align-self: flex-start;
      display: inline-flex;
      align-items: center;
      column-gap: 16px;

      > .btn-tertiary {
        align-self: flex-start;
        font-size: var(--#{$prefix}font-size-md);
      }

      > h1 {
        margin: 0;
      }
    }
  }

  @media (width >= map.get($grid-breakpoints, md)) {
    gap: 48px;

    > header {
      flex-direction: row;

      > .heading {
        align-self: unset;
      }
    }
  }
}
</style>
