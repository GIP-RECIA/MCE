<script setup lang="ts">
import { computed, nextTick, ref } from 'vue';
import {
  getJson, post, type ActivationConnexionResult, type ActivationStatus, type CharteStatus
} from '../api';
import { EMAIL_RE, CODE_RE, passwordStrength } from '../utils';

type Step = 'connexion' | 'form' | 'verify' | 'success';

const step = ref<Step>('connexion');
const message = ref<string | null>(null);
const messageError = ref(false);
const messageBox = ref<HTMLElement | null>(null);
const isLoading = ref(false);

const connexionLogin = ref('');
const connexionPassword = ref('');

const charteBlockVisible = ref(false);
const charteAccepted = ref(false);
const charteUrl = ref('#');

const emailVisible = ref(false);
const email = ref('');

const passwordVisible = ref(false);
const newPassword = ref('');
const confirmPassword = ref('');

const verifyCode = ref('');

const formTitle = ref("Finaliser l'activation");
const successTitle = ref('Compte activé');
const successText = ref('Votre compte a été activé avec succès. Vous pouvez maintenant vous connecter avec votre nouveau mot de passe.');

const activationUid = ref<string | null>(null);
const lastPayload = ref<Record<string, unknown> | null>(null);

const headerTitle = computed(() => {
  switch (step.value) {
    case 'connexion':
      return "Activation de votre compte";
    case 'form':
      return formTitle.value;
    case 'verify':
      return 'Vérifiez votre adresse email';
    case 'success':
      return successTitle.value;
  }
});

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}

function showMessage(text: string, isError?: boolean) {
  message.value = text;
  messageError.value = !!isError;
  if (text) {
    nextTick(() => { messageBox.value?.focus(); });
  }
}

function clearMessage() {
  message.value = null;
}

async function loadCharteUrl(uid: string): Promise<string> {
  try {
    const status = await getJson<CharteStatus>('/charte-status?uid=' + encodeURIComponent(uid));
    return status && status.charteUrl ? status.charteUrl : '#';
  } catch {
    return '#';
  }
}

async function onSubmitConnexion() {
  clearMessage();
  const login = connexionLogin.value.trim();
  const mdp = connexionPassword.value;

  if (!login || !mdp) {
    showMessage('Le login et le mot de passe sont obligatoires', true);
    return;
  }

  isLoading.value = true;
  try {
    const res = await post<ActivationConnexionResult>('/activation/connexion', { login, password: mdp });
    activationUid.value = res.uid;
    const status = await getJson<ActivationStatus>('/activation/status?uid=' + encodeURIComponent(res.uid));
    if (status.etapeSuivante === 'FIN') {
      step.value = 'success';
      return;
    }
    prepareForm(status);
    step.value = 'form';
  } catch (err) {
    showMessage(errorMessage(err), true);
  } finally {
    isLoading.value = false;
  }
}

function prepareForm(status: ActivationStatus) {
  const shown: string[] = [];

  charteBlockVisible.value = !!status.charteRequise;
  if (status.charteRequise) {
    charteAccepted.value = false;
    loadCharteUrl(activationUid.value as string).then((c) => { charteUrl.value = c; });
    shown.push('charte');
  }

  emailVisible.value = !!status.emailRequise;
  if (status.emailRequise) shown.push('email');

  passwordVisible.value = !!status.passwordRequise;
  if (status.passwordRequise) shown.push('mot de passe');

  let title: string;
  if (shown.includes('charte')) {
    title = "Acceptez la charte d'utilisation";
  } else if (shown.includes('mot de passe') && shown.includes('email')) {
    title = 'Créez votre mot de passe et renseignez votre email';
  } else if (shown.includes('mot de passe')) {
    title = 'Créez votre mot de passe définitif';
  } else if (shown.includes('email')) {
    title = 'Renseignez votre adresse email';
  } else {
    title = "Finalisez l'activation de votre compte";
  }
  formTitle.value = title;
}

async function onSubmitActivation() {
  clearMessage();

  if (!activationUid.value) {
    showMessage('Veuillez d\'abord vous identifier', true);
    step.value = 'connexion';
    return;
  }

  if (charteBlockVisible.value && !charteAccepted.value) {
    showMessage("Vous devez accepter la charte d'utilisation", true);
    return;
  }

  let emailPayload: string | null = null;
  if (emailVisible.value) {
    emailPayload = email.value.trim();
    if (!emailPayload) {
      showMessage("L'adresse email est obligatoire", true);
      return;
    }
    if (!EMAIL_RE.test(emailPayload)) {
      showMessage("Le format de l'adresse email est invalide", true);
      return;
    }
  }

  let newPasswordPayload: string | null = null;
  if (passwordVisible.value) {
    newPasswordPayload = newPassword.value;
    if (newPasswordPayload.length < 12) {
      showMessage('Le mot de passe doit contenir au moins 12 caractères', true);
      return;
    }
    if (passwordStrength(newPasswordPayload) < 3) {
      showMessage('Le mot de passe doit contenir au moins 3 types de caractères (minuscules, majuscules, chiffres, symboles)', true);
      return;
    }
    if (newPasswordPayload !== confirmPassword.value) {
      showMessage('Les mots de passe ne correspondent pas', true);
      return;
    }
  }

  const payload: Record<string, unknown> = {
    uid: activationUid.value,
    charteAccepted: charteBlockVisible.value ? charteAccepted.value : true,
    email: emailPayload,
    newPassword: newPasswordPayload,
    confirmPassword: newPasswordPayload
  };
  lastPayload.value = payload;

  isLoading.value = true;
  try {
    const res = await post<{ emailEnAttenteDeVerification?: boolean }>('/activation/password', payload);
    if (res.emailEnAttenteDeVerification) {
      step.value = 'verify';
    } else {
      showSuccess();
    }
  } catch (err) {
    showMessage(errorMessage(err), true);
  } finally {
    isLoading.value = false;
  }
}

function showSuccess() {
  successTitle.value = 'Compte activé';
  successText.value = 'Votre compte a été activé avec succès. Vous pouvez maintenant vous connecter avec votre nouveau mot de passe.';
  step.value = 'success';
}

async function onSubmitVerify() {
  clearMessage();
  const code = verifyCode.value.trim();
  if (!CODE_RE.test(code)) {
    showMessage('Le code doit comporter 6 chiffres', true);
    return;
  }

  isLoading.value = true;
  try {
    await post('/verify-email', { uid: activationUid.value, code });
    successTitle.value = 'Adresse email vérifiée';
    successText.value = 'Votre adresse email a été vérifiée et votre compte est activé. Vous pouvez vous connecter.';
    step.value = 'success';
  } catch (err) {
    showMessage(errorMessage(err), true);
  } finally {
    isLoading.value = false;
  }
}

async function onResendCode() {
  if (!lastPayload.value) {
    showMessage('Demandez d\'abord l\'activation de votre compte.', true);
    return;
  }
  clearMessage();

  try {
    await post('/activation/password', lastPayload.value);
    showMessage('Un nouveau code vous a été envoyé. Vérifiez votre boîte mail.', false);
  } catch (err) {
    showMessage(errorMessage(err), true);
  }
}
</script>

<template>
  <main class="activation-panel">
    <div class="card-header">
      <h3>{{ headerTitle }}</h3>
    </div>

    <form class="card-body" novalidate @submit.prevent="onSubmitActivation">
      <div
        v-if="message"
        ref="messageBox"
        class="alert-message"
        :class="messageError ? 'alert-message--error' : 'alert-message--success'"
        role="alert"
        tabindex="-1"
      >
        {{ message }}
      </div>

      <template v-if="step === 'connexion'">
        <p class="activation-intro">
          Un compte vous a été créé. Saisissez les identifiants transmis dans votre courrier (ou par votre établissement) pour débuter l'activation.
        </p>

        <div class="field">
          <div class="field-layout">
            <div class="field-container">
              <div class="middle">
                <label for="connexion-login">Identifiant (UID)</label>
                <input
                  id="connexion-login"
                  v-model="connexionLogin"
                  type="text"
                  placeholder=" "
                  autocomplete="username"
                  @input="clearMessage"
                >
              </div>
            </div>
            <div class="active-indicator"></div>
          </div>
        </div>

        <div class="field">
          <div class="field-layout">
            <div class="field-container">
              <div class="middle">
                <label for="connexion-password">Mot de passe temporaire</label>
                <input
                  id="connexion-password"
                  v-model="connexionPassword"
                  type="password"
                  placeholder=" "
                  autocomplete="current-password"
                  @input="clearMessage"
                >
              </div>
            </div>
            <div class="active-indicator"></div>
          </div>
        </div>

        <div class="action-row">
          <button
            type="submit"
            class="btn-primary small"
            :disabled="isLoading"
            :aria-busy="isLoading ? 'true' : undefined"
          >
            <span v-if="isLoading">Connexion…</span>
            <span v-else>Activer mon compte</span>
          </button>
        </div>
      </template>

      <template v-else-if="step === 'form'">
        <div v-if="charteBlockVisible" class="charte-block">
          <input
            id="activation-charte"
            v-model="charteAccepted"
            type="checkbox"
            @change="clearMessage"
          >
          <label for="activation-charte">J'accepte la <a id="charte-link" :href="charteUrl" target="_blank" rel="noopener">charte d'utilisation</a></label>
        </div>

        <div v-if="emailVisible" class="field">
          <div class="field-layout">
            <div class="field-container">
              <div class="middle">
                <label for="activation-email">Adresse email</label>
                <input
                  id="activation-email"
                  v-model="email"
                  type="email"
                  placeholder=" "
                  autocomplete="email"
                  @input="clearMessage"
                >
              </div>
            </div>
            <div class="active-indicator"></div>
          </div>
        </div>

        <div v-if="passwordVisible" class="field">
          <div class="field-layout">
            <div class="field-container">
              <div class="middle">
                <label for="activation-new-password">Nouveau mot de passe</label>
                <input
                  id="activation-new-password"
                  v-model="newPassword"
                  type="password"
                  placeholder=" "
                  autocomplete="new-password"
                  @input="clearMessage"
                >
              </div>
            </div>
            <div class="active-indicator"></div>
          </div>
        </div>

        <div v-if="passwordVisible" class="field">
          <div class="field-layout">
            <div class="field-container">
              <div class="middle">
                <label for="activation-confirm-password">Confirmation du mot de passe</label>
                <input
                  id="activation-confirm-password"
                  v-model="confirmPassword"
                  type="password"
                  placeholder=" "
                  autocomplete="new-password"
                  @input="clearMessage"
                >
              </div>
            </div>
            <div class="active-indicator"></div>
          </div>
        </div>

        <div class="action-row">
          <button
            type="submit"
            class="btn-primary small"
            :disabled="isLoading"
            :aria-busy="isLoading ? 'true' : undefined"
          >
            <span v-if="isLoading">Envoi…</span>
            <span v-else>Activer mon compte</span>
          </button>
        </div>
      </template>

      <template v-else-if="step === 'verify'">
        <p class="activation-intro">
          Un code de vérification (6 chiffres) vous a été envoyé par email.
        </p>

        <div class="field">
          <div class="field-layout">
            <div class="field-container">
              <div class="middle">
                <label for="verify-code">Code reçu par email</label>
                <input
                  id="verify-code"
                  v-model="verifyCode"
                  type="text"
                  inputmode="numeric"
                  placeholder=" "
                  autocomplete="one-time-code"
                  pattern="\d{6}"
                  @input="clearMessage"
                >
              </div>
            </div>
            <div class="active-indicator"></div>
          </div>
        </div>

        <button type="button" class="btn-tertiary" @click="onResendCode">
          Renvoyer un nouveau code
        </button>

        <div class="action-row">
          <button
            type="submit"
            class="btn-primary small"
            :disabled="isLoading"
            :aria-busy="isLoading ? 'true' : undefined"
          >
            <span v-if="isLoading">Vérification…</span>
            <span v-else>Vérifier mon email</span>
          </button>
        </div>
      </template>

      <template v-else-if="step === 'success'">
        <p>{{ successText }}</p>
      </template>
    </form>
  </main>
</template>

<style lang="scss">
@use 'ress/dist/ress.min.css';
@use '@gip-recia/ui/core/variables' as *;
@use '@gip-recia/ui/functions' as *;
@use '@gip-recia/ui/mixins' as *;
@use '@gip-recia/ui/components/buttons';
@use '@gip-recia/ui/components/fields';
@use '../assets/mce-shared' as *;

.activation-panel {
  @include mce-card-base;
  @include mce-fields;
  max-width: 520px;
  margin: 40px auto;
}

.card-header {
  @include mce-card-header;
}

.card-body {
  @include mce-card-body;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.activation-intro {
  margin: 0;
}

.action-row {
  @include mce-action-row;
}

.alert-message {
  @include mce-alert-message;
}

.charte-block {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: var(--#{$prefix}font-size-sm);

  input[type="checkbox"] {
    width: auto;
    margin: 0;
  }

  a {
    color: var(--#{$prefix}system-blue);
    text-decoration: underline;
  }
}
</style>