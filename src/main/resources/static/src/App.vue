<script setup lang="ts">
import { computed } from 'vue';
import ActivationView from './views/ActivationView.vue';
import MotDePasseOublieView from './views/MotDePasseOublieView.vue';

const isMotDePasseOublie = computed(() => window.location.pathname.includes('/mot-de-passe-oublie'));
</script>

<template>
  <component :is="isMotDePasseOublie ? MotDePasseOublieView : ActivationView" />
</template>