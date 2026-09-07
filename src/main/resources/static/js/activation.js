(function () {
    'use strict';

    // ============================================================
    //  Constantes
    // ============================================================

    var BASE = (window.MCE_BASE || '/');
    var EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    var CODE_RE = /^\d{6}$/;

    var STEP_CONNEXION = 'connexion';
    var STEP_FORM = 'form';
    var STEP_VERIFY = 'verify';
    var STEP_SUCCESS = 'success';

    // ============================================================
    //  Utilitaires
    // ============================================================

    function api(path) {
        return BASE.replace(/\/$/, '') + '/api/personne/mce' + path;
    }

    function toJson(resp) {
        return resp.json().catch(function () { return {}; });
    }

    function getJson(url) {
        return fetch(url).then(function (resp) {
            if (!resp.ok) { throw new Error('Erreur de chargement (' + resp.status + ')'); }
            return resp.json();
        });
    }

    function post(url, body) {
        return fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        }).then(function (resp) {
            return toJson(resp).then(function (data) {
                if (!resp.ok) {
                    var err = new Error((data && data.message) ? data.message : 'Une erreur est survenue');
                    err.code = data && data.code;
                    err.charteUrl = data && data.charteUrl;
                    err.retryAfterSeconds = data && data.retryAfterSeconds;
                    throw err;
                }
                return data;
            });
        });
    }

    function passwordStrength(pw) {
        var types = 0;
        if (/[a-z]/.test(pw)) { types++; }
        if (/[A-Z]/.test(pw)) { types++; }
        if (/[0-9]/.test(pw)) { types++; }
        if (/[^a-zA-Z0-9]/.test(pw)) { types++; }
        return types;
    }

    // ============================================================
    //  Navigation & messages
    // ============================================================

    var steps = {
        connexion: document.getElementById('step-connexion'),
        form:      document.getElementById('step-form'),
        verify:    document.getElementById('step-verify'),
        success:   document.getElementById('step-success')
    };

    var messageEl = document.getElementById('message');

    function showStep(name) {
        Object.keys(steps).forEach(function (k) {
            steps[k].hidden = (k !== name);
        });
    }

    function showMessage(text, error) {
        messageEl.hidden = false;
        messageEl.textContent = text;
        messageEl.className = 'message ' + (error ? 'message-error' : 'message-success');
        messageEl.focus();
    }

    function clearMessage() {
        messageEl.hidden = true;
        messageEl.textContent = '';
    }

    // ============================================================
    //  État du parcours
    // ============================================================

    var activationUid = null;
    var lastPayload = null;

    // ============================================================
    //  Étape 1 — Connexion avec identifiants temporaires
    // ============================================================

    document.getElementById('form-connexion').addEventListener('submit', function (e) {
        e.preventDefault();
        clearMessage();

        var login = document.getElementById('connexion-login').value.trim();
        var mdp = document.getElementById('connexion-password').value;

        if (!login || !mdp) {
            showMessage('Le login et le mot de passe sont obligatoires', true);
            return;
        }

        post(api('/activation/connexion'), { login: login, password: mdp })
            .then(function (res) {
                activationUid = res.uid;
                return loadStatus(activationUid);
            })
            .then(function (status) {
                if (status.etapeSuivante === 'FIN') {
                    showStep(STEP_SUCCESS);
                    return;
                }
                prepareForm(status);
                showStep(STEP_FORM);
            })
            .catch(function (err) {
                showMessage(err.message, true);
            });
    });

    function loadStatus(uid) {
        return getJson(api('/activation/status?uid=' + encodeURIComponent(uid)));
    }

    function loadCharteUrl(uid) {
        return getJson(api('/charte-status?uid=' + encodeURIComponent(uid)))
            .then(function (status) { return status && status.charteUrl ? status.charteUrl : '#'; })
            .catch(function () { return '#'; });
    }

    // ============================================================
    //  Étape 2 — Charte + email + mot de passe définitif
    // ============================================================

    var charteBlock = document.getElementById('charte-block');
    var charteCheck = document.getElementById('activation-charte');
    var charteLink = document.getElementById('charte-link');
    var emailField = document.getElementById('email-field');
    var emailInput = document.getElementById('activation-email');
    var passwordField = document.getElementById('password-field');
    var passwordInput = document.getElementById('activation-newpass');
    var confirmField = document.getElementById('confirm-field');
    var confirmInput = document.getElementById('activation-confirm');

    function prepareForm(status) {
        var shown = [];

        if (status.charteRequise) {
            charteBlock.hidden = false;
            loadCharteUrl(activationUid).then(function (url) {
                charteLink.href = url;
            });
            shown.push('charte');
        } else {
            charteBlock.hidden = true;
        }

        if (status.emailRequise) {
            emailField.hidden = false;
            emailInput.required = true;
            shown.push('email');
        } else {
            emailField.hidden = true;
            emailInput.required = false;
        }

        if (status.passwordRequise) {
            passwordField.hidden = false;
            confirmField.hidden = false;
            passwordInput.required = true;
            confirmInput.required = true;
            shown.push('mot de passe');
        } else {
            passwordField.hidden = true;
            confirmField.hidden = true;
            passwordInput.required = false;
            confirmInput.required = false;
        }

        var title;
        if (shown.indexOf('charte') !== -1) {
            title = 'Acceptez la charte d\'utilisation';
        } else if (shown.indexOf('mot de passe') !== -1 && shown.indexOf('email') !== -1) {
            title = 'Créez votre mot de passe et renseignez votre email';
        } else if (shown.indexOf('mot de passe') !== -1) {
            title = 'Créez votre mot de passe définitif';
        } else if (shown.indexOf('email') !== -1) {
            title = 'Renseignez votre adresse email';
        } else {
            title = 'Finalisez l\'activation de votre compte';
        }
        document.getElementById('form-title').textContent = title;
    }

    document.getElementById('form-activation').addEventListener('submit', function (e) {
        e.preventDefault();
        clearMessage();

        if (!activationUid) {
            showMessage('Veuillez d\'abord vous identifier', true);
            showStep(STEP_CONNEXION);
            return;
        }

        var charteAccepted = charteBlock.hidden || charteCheck.checked;
        if (!charteBlock.hidden && !charteCheck.checked) {
            showMessage('Vous devez accepter la charte d\'utilisation', true);
            return;
        }

        var email = null;
        if (!emailField.hidden) {
            email = emailInput.value.trim();
            if (!email) {
                showMessage('L\'adresse email est obligatoire', true);
                return;
            }
            if (!EMAIL_RE.test(email)) {
                showMessage('Le format de l\'adresse email est invalide', true);
                return;
            }
        }

        var newPassword = null;
        if (!passwordField.hidden) {
            newPassword = passwordInput.value;
            var confirmPassword = confirmInput.value;
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
        }

        var payload = {
            uid: activationUid,
            charteAccepted: charteAccepted,
            email: email,
            newPassword: newPassword,
            confirmPassword: newPassword
        };
        lastPayload = payload;

        post(api('/activation/password'), payload)
            .then(function (res) {
                if (res.emailEnAttenteDeVerification) {
                    showStep(STEP_VERIFY);
                } else {
                    showSuccess();
                }
            })
            .catch(function (err) {
                showMessage(err.message, true);
            });
    });

    function showSuccess() {
        document.getElementById('success-title').textContent = 'Compte activé';
        document.getElementById('success-text').textContent =
            'Votre compte a été activé avec succès. Vous pouvez maintenant vous connecter avec votre nouveau mot de passe.';
        showStep(STEP_SUCCESS);
    }

    // ============================================================
    //  Étape 3 — Vérification de l'adresse email
    // ============================================================

    document.getElementById('form-verify').addEventListener('submit', function (e) {
        e.preventDefault();
        clearMessage();

        var code = document.getElementById('verify-code').value.trim();
        if (!CODE_RE.test(code)) {
            showMessage('Le code doit comporter 6 chiffres', true);
            return;
        }

        post(api('/verify-email'), { uid: activationUid, code: code })
            .then(function () {
                document.getElementById('success-title').textContent = 'Adresse email vérifiée';
                document.getElementById('success-text').textContent =
                    'Votre adresse email a été vérifiée et votre compte est activé. Vous pouvez vous connecter.';
                showStep(STEP_SUCCESS);
            })
            .catch(function (err) {
                showMessage(err.message, true);
            });
    });

    document.getElementById('btn-resend-code').addEventListener('click', function () {
        if (!lastPayload) {
            showMessage('Demandez d\'abord l\'activation de votre compte.', true);
            return;
        }
        clearMessage();

        post(api('/activation/password'), lastPayload)
            .then(function () {
                showMessage('Un nouveau code vous a été envoyé. Vérifiez votre boîte mail.', false);
            })
            .catch(function (err) {
                showMessage(err.message, true);
            });
    });

    // ============================================================
    //  Effacement automatique des messages
    // ============================================================

    document.querySelectorAll('input').forEach(function (el) {
        el.addEventListener('input', clearMessage);
        el.addEventListener('change', clearMessage);
    });
})();