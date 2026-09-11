<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue';
import {
  getJson, post, type CharteStatus, type RecoverUidResult, type StructuresResponse
} from '../api';
import { EMAIL_RE, CODE_RE, passwordStrength } from '../utils';

type Step = 'choice' | 'known' | 'recover' | 'reset' | 'success';

const step = ref<Step>('choice');
const message = ref<string | null>(null);
const messageError = ref(false);
const messageBox = ref<HTMLElement | null>(null);

const knownUid = ref('');
const knownEmail = ref('');
const knownProfil = ref('');
const knownProfils = ref<string[]>([]);

const recoverNom = ref('');
const recoverPrenom = ref('');
const recoverEmail = ref('');
const recoverProfil = ref('');
const recoverTypes = ref<string[]>([]);
const recoverVilles = ref<string[]>([]);
const recoverEtabs = ref<{ value: string; label: string }[]>([]);
const recoverType = ref('');
const recoverVille = ref('');
const recoverEtab = ref('');
const typesLoaded = ref(false);

const resetUid = ref('');
const resetCode = ref('');
const resetNewPass = ref('');
const resetConfirm = ref('');
const resetCharteVisible = ref(false);
const resetCharteAccepted = ref(false);
const resetCharteUrl = ref('#');
const resendPending = ref(false);

const structuresCache = ref<StructuresResponse[]>([]);
const resetOrigin = ref<'known' | 'recover'>('known');
const lastSendContext = ref<{
  type: 'known' | 'recover';
  uid?: string;
  email?: string;
  profil?: string;
  payload?: Record<string, unknown>;
  resetToken?: string;
} | null>(null);

const stepTitle = computed(() => {
  switch (step.value) {
    case 'choice': return 'Mot de passe oublié';
    case 'known': return 'Je connais mon identifiant';
    case 'recover': return 'Je ne connais pas mon identifiant';
    case 'reset': return 'Réinitialiser mon mot de passe';
    case 'success': return 'Mot de passe réinitialisé';
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

function loadProfils() {
  getJson<string[]>('/structures/profils')
    .then((profils) => { knownProfils.value = profils || []; })
    .catch((err) => showMessage(errorMessage(err), true));
}

onMounted(loadProfils);

function goKnown() {
  clearMessage();
  step.value = 'known';
}

function goRecover() {
  clearMessage();
  step.value = 'recover';
  recoverLoadTypes();
}

function goChoice() {
  clearMessage();
  step.value = 'choice';
}

function goResetFromOrigin() {
  clearMessage();
  if (resetOrigin.value === 'recover') {
    step.value = 'recover';
    recoverLoadTypes();
  } else {
    step.value = 'known';
  }
}

function onSubmitKnown() {
  clearMessage();
  const uid = knownUid.value.trim();
  const email = knownEmail.value.trim();
  const profil = knownProfil.value;

  if (!uid || !email || !profil) {
    showMessage('Tous les champs sont obligatoires', true);
    return;
  }
  if (!EMAIL_RE.test(email)) {
    showMessage("Le format de l'adresse email est invalide", true);
    return;
  }

  post('/forgot-password', { uid, email, profil })
    .then(() => {
      resetUid.value = uid;
      lastSendContext.value = { type: 'known', uid, email, profil };
      resetShowStep(uid, true, null);
    })
    .catch((err) => showMessage(errorMessage(err), true));
}

function recoverLoadTypes(): Promise<void> {
  if (typesLoaded.value) return Promise.resolve();
  return getJson<string[]>('/structures/types')
    .then((types) => {
      typesLoaded.value = true;
      recoverTypes.value = types || [];
      recoverClearVille();
    })
    .catch((err) => {
      showMessage(errorMessage(err), true);
      throw err;
    });
}

function recoverLoadStructures(): Promise<void> {
  if (structuresCache.value.length > 0) return Promise.resolve();
  return getJson<StructuresResponse[]>('/structures')
    .then((list) => { structuresCache.value = list || []; })
    .catch((err) => {
      showMessage(errorMessage(err), true);
      throw err;
    });
}

function onRecoverTypeChange() {
  recoverClearVille();
  if (recoverType.value) recoverLoadVilles();
}

function onRecoverVilleChange() {
  recoverClearEtab();
  if (recoverVille.value) recoverLoadEtablissements();
}

function recoverLoadVilles() {
  const type = recoverType.value;
  const villes: string[] = [];
  recoverLoadStructures().then(() => {
    structuresCache.value.forEach((s) => {
      if (type && s.surtype !== type) return;
      if (s.ville && villes.indexOf(s.ville) === -1) villes.push(s.ville);
    });
    villes.sort();
    recoverVilles.value = villes;
    recoverClearEtab();
  });
}

function recoverLoadEtablissements() {
  const type = recoverType.value;
  const ville = recoverVille.value;
  const list = structuresCache.value.filter((s) => {
    if (type && s.surtype !== type) return false;
    if (ville && s.ville !== ville) return false;
    return true;
  });
  list.sort((a, b) => (a.nom || '').localeCompare(b.nom || ''));
  recoverEtabs.value = list.map((s) => ({ value: s.siren || s.nom, label: s.nom || s.siren }));
}

function recoverClearVille() {
  recoverVilles.value = [];
  recoverVille.value = '';
  recoverClearEtab();
}

function recoverClearEtab() {
  recoverEtabs.value = [];
  recoverEtab.value = '';
}

function onSubmitRecover() {
  clearMessage();

  const payload: Record<string, unknown> = {
    nom: recoverNom.value.trim(),
    prenom: recoverPrenom.value.trim(),
    email: recoverEmail.value.trim(),
    profil: recoverProfil.value,
    typeEtablissement: recoverType.value,
    ville: recoverVille.value,
    etablissement: recoverEtab.value
  };

  const allFilled = Object.keys(payload).every((k) => payload[k]);
  if (!allFilled) {
    showMessage('Tous les champs sont obligatoires', true);
    return;
  }
  if (!EMAIL_RE.test(payload.email as string)) {
    showMessage("Le format de l'adresse email est invalide", true);
    return;
  }

  post<RecoverUidResult>('/recover-uid', payload)
    .then((res) => {
      showMessage('Si un compte correspond à ces informations et permet de réinitialiser son mot de passe, un code vous a été envoyé.', false);
      lastSendContext.value = { type: 'recover', payload, resetToken: res && res.resetToken };
      resetShowStep('', false, res);
    })
    .catch((err) => showMessage(errorMessage(err), true));
}

function onSubmitReset() {
  clearMessage();

  const uid = resetUid.value.trim();
  const resetToken = resetOrigin.value === 'recover' && lastSendContext.value
    ? lastSendContext.value.resetToken
    : null;
  const code = resetCode.value.trim();
  const newPassword = resetNewPass.value;
  const confirmPassword = resetConfirm.value;
  const charteAccepted = !resetCharteVisible.value || resetCharteAccepted.value;

  if (!code) {
    showMessage('Le code est obligatoire', true);
    return;
  }
  if (!uid && !(resetOrigin.value === 'recover')) {
    showMessage("L'identifiant est obligatoire", true);
    return;
  }
  if (!CODE_RE.test(code)) {
    showMessage('Le code doit comporter 6 chiffres', true);
    return;
  }
  if (newPassword.length < 12) {
    showMessage('Le mot de passe doit contenir au moins 12 caractères', true);
    return;
  }
  if (passwordStrength(newPassword) < 3) {
    showMessage('Le mot de passe doit contenir au moins 3 types de caractères (minuscules, majuscules, chiffres, symboles)', true);
    return;
  }
  if (newPassword !== confirmPassword) {
    showMessage('Les mots de passe ne correspondent pas', true);
    return;
  }

  post('/reset-password', {
    uid,
    resetToken,
    code,
    newPassword,
    confirmPassword,
    charteAccepted
  })
    .then(() => { step.value = 'success'; })
    .catch((err) => {
      showMessage(errorMessage(err), true);
      if (err.code === 'CHARTE_REQUIRED' && err.charteUrl) {
        resetCharteUrl.value = err.charteUrl;
        resetCharteVisible.value = true;
      }
    });
}

function onResendCode() {
  if (!lastSendContext.value) {
    showMessage('Demandez d\'abord un code de réinitialisation.', true);
    return;
  }
  if (resendPending.value) return;
  clearMessage();
  resendPending.value = true;

  const onDone = (res: RecoverUidResult) => {
    resendPending.value = false;
    if (lastSendContext.value?.type === 'recover') {
      resetShowStep('', false, res);
    }
    showMessage('Un nouveau code vous a été envoyé. Vérifiez votre boîte mail.', false);
  };
  const onError = (err: Error) => {
    resendPending.value = false;
    showMessage(errorMessage(err), true);
  };

  if (lastSendContext.value.type === 'known') {
    post<RecoverUidResult>('/forgot-password', {
      uid: lastSendContext.value.uid,
      email: lastSendContext.value.email,
      profil: lastSendContext.value.profil
    }).then(onDone).catch(onError);
  } else {
    post<RecoverUidResult>('/recover-uid', lastSendContext.value.payload)
      .then(onDone)
      .catch(onError);
  }
}

function resetShowStep(uid: string, showUid: boolean, recoverResult: RecoverUidResult | null) {
  resetOrigin.value = showUid ? 'known' : 'recover';
  resetUid.value = uid;
  resetLoadCharteStatus(uid, recoverResult);
  step.value = 'reset';
}

function resetLoadCharteStatus(uid: string, recoverResult: RecoverUidResult | null) {
  resetCharteVisible.value = false;
  if (recoverResult && typeof recoverResult.charteRequired === 'boolean') {
    if (recoverResult.charteRequired) {
      if (recoverResult.charteUrl) resetCharteUrl.value = recoverResult.charteUrl;
      resetCharteVisible.value = true;
    }
    return;
  }
  if (!uid) return;

  getJson<CharteStatus>('/charte-status?uid=' + encodeURIComponent(uid))
    .then((status) => {
      if (status && status.charteRequired) {
        resetCharteUrl.value = status.charteUrl || '#';
        resetCharteVisible.value = true;
      }
    })
    .catch(() => { resetCharteVisible.value = false; });
}
</script>

<template>
  <main class="forgot-password-panel">
    <div class="card-header">
      <h3>{{ stepTitle }}</h3>
    </div>

    <div class="card-body">
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
      <template v-if="step === 'choice'">
        <p>
          Choisissez une option pour réinitialiser votre mot de passe.
        </p>
        <div class="action-row">
          <button type="button" class="btn-primary small" @click="goKnown">
            Je connais mon identifiant
          </button>
          <button type="button" class="btn-secondary small" @click="goRecover">
            Je ne connais pas mon identifiant
          </button>
        </div>
      </template>

      <template v-else-if="step === 'known'">
        <form novalidate @submit.prevent="onSubmitKnown">
          <div class="field">
            <div class="field-layout">
              <div class="field-container">
                <div class="middle">
                  <label for="known-uid">Identifiant (UID)</label>
                  <input
                    id="known-uid"
                    v-model="knownUid"
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
                  <label for="known-email">Adresse email</label>
                  <input
                    id="known-email"
                    v-model="knownEmail"
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

          <div class="field field--select">
            <div class="field-layout">
              <div class="field-container">
                <div class="middle">
                  <label for="known-profil">Profil</label>
                  <select id="known-profil" v-model="knownProfil" @change="clearMessage">
                    <option value="" disabled selected hidden></option>
                    <option v-for="p in knownProfils" :key="p" :value="p">{{ p }}</option>
                  </select>
                </div>
              </div>
              <div class="active-indicator"></div>
            </div>
          </div>

          <div class="action-row">
            <button type="button" class="btn-secondary small" @click="goChoice">Retour</button>
            <button type="submit" class="btn-primary small">Envoyer un code</button>
          </div>
        </form>
      </template>

      <template v-else-if="step === 'recover'">
        <form novalidate @submit.prevent="onSubmitRecover">
          <div class="field">
            <div class="field-layout">
              <div class="field-container">
                <div class="middle">
                  <label for="recover-nom">Nom</label>
                  <input
                    id="recover-nom"
                    v-model="recoverNom"
                    type="text"
                    placeholder=" "
                    autocomplete="family-name"
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
                  <label for="recover-prenom">Prénom</label>
                  <input
                    id="recover-prenom"
                    v-model="recoverPrenom"
                    type="text"
                    placeholder=" "
                    autocomplete="given-name"
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
                  <label for="recover-email">Adresse email</label>
                  <input
                    id="recover-email"
                    v-model="recoverEmail"
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

          <div class="field field--select">
            <div class="field-layout">
              <div class="field-container">
                <div class="middle">
                  <label for="recover-profil">Profil</label>
                  <select id="recover-profil" v-model="recoverProfil" @change="clearMessage">
                    <option value="" disabled selected hidden></option>
                    <option v-for="p in knownProfils" :key="p" :value="p">{{ p }}</option>
                  </select>
                </div>
              </div>
              <div class="active-indicator"></div>
            </div>
          </div>

          <div class="field field--select">
            <div class="field-layout">
              <div class="field-container">
                <div class="middle">
                  <label for="recover-type">Type d'établissement</label>
                  <select id="recover-type" v-model="recoverType" @change="onRecoverTypeChange">
                    <option value="" disabled selected hidden></option>
                    <option v-for="t in recoverTypes" :key="t" :value="t">{{ t }}</option>
                  </select>
                </div>
              </div>
              <div class="active-indicator"></div>
            </div>
          </div>

          <div class="field field--select">
            <div class="field-layout">
              <div class="field-container">
                <div class="middle">
                  <label for="recover-ville">Ville</label>
                  <select
                    id="recover-ville"
                    v-model="recoverVille"
                    :disabled="recoverVilles.length === 0"
                    @change="onRecoverVilleChange"
                  >
                    <option value="" disabled selected hidden></option>
                    <option v-for="v in recoverVilles" :key="v" :value="v">{{ v }}</option>
                  </select>
                </div>
              </div>
              <div class="active-indicator"></div>
            </div>
          </div>

          <div class="field field--select">
            <div class="field-layout">
              <div class="field-container">
                <div class="middle">
                  <label for="recover-etab">Établissement</label>
                  <select
                    id="recover-etab"
                    v-model="recoverEtab"
                    :disabled="recoverEtabs.length === 0"
                    @change="clearMessage"
                  >
                    <option value="" disabled selected hidden></option>
                    <option v-for="e in recoverEtabs" :key="e.value" :value="e.value">{{ e.label }}</option>
                  </select>
                </div>
              </div>
              <div class="active-indicator"></div>
            </div>
          </div>

          <div class="action-row">
            <button type="button" class="btn-secondary small" @click="goChoice">Retour</button>
            <button type="submit" class="btn-primary small">Envoyer un code</button>
          </div>
        </form>
      </template>

      <template v-else-if="step === 'reset'">
        <form novalidate @submit.prevent="onSubmitReset">
          <div v-if="resetOrigin === 'known'" class="field">
            <div class="field-layout">
              <div class="field-container">
                <div class="middle">
                  <label for="reset-uid">Identifiant (UID)</label>
                  <input
                    id="reset-uid"
                    v-model="resetUid"
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
                  <label for="reset-code">Code reçu par email (6 chiffres)</label>
                  <input
                    id="reset-code"
                    v-model="resetCode"
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

          <button type="button" class="btn-tertiary" :disabled="resendPending" @click="onResendCode">
            Renvoyer un nouveau code
          </button>

          <div class="field">
            <div class="field-layout">
              <div class="field-container">
                <div class="middle">
                  <label for="reset-new-password">Nouveau mot de passe</label>
                  <input
                    id="reset-new-password"
                    v-model="resetNewPass"
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

          <div class="field">
            <div class="field-layout">
              <div class="field-container">
                <div class="middle">
                  <label for="reset-confirm-password">Confirmation du mot de passe</label>
                  <input
                    id="reset-confirm-password"
                    v-model="resetConfirm"
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

          <div v-if="resetCharteVisible" class="charte-block">
            <input
              id="reset-charte"
              v-model="resetCharteAccepted"
              type="checkbox"
              @change="clearMessage"
            >
            <label for="reset-charte">J'accepte la <a id="charte-link" :href="resetCharteUrl" target="_blank" rel="noopener">charte d'utilisation</a></label>
          </div>

          <div class="action-row">
            <button type="button" class="btn-secondary small" @click="goResetFromOrigin">Retour</button>
            <button type="submit" class="btn-primary small">Réinitialiser mon mot de passe</button>
          </div>
        </form>
      </template>

      <template v-else-if="step === 'success'">
        <p>
          Votre mot de passe a été réinitialisé avec succès. Vous pouvez vous connecter.
        </p>
      </template>
    </div>
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

.forgot-password-panel {
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

.form-group,
form {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
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
