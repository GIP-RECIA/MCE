(function () {
    'use strict';

    // ============================================================
    //  Constantes
    // ============================================================

    var BASE = (window.MCE_BASE || '/');
    var EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    var CODE_RE = /^\d{6}$/;

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
                    throw err;
                }
                return data;
            });
        });
    }

    function fillSelect(el, options) {
        el.innerHTML = '';
        options.forEach(function (opt) {
            var o = document.createElement('option');
            o.value = opt.value;
            o.textContent = opt.label;
            el.appendChild(o);
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
        choice:  document.getElementById('step-choice'),
        known:   document.getElementById('step-known'),
        recover: document.getElementById('step-recover'),
        reset:   document.getElementById('step-reset'),
        success: document.getElementById('step-success')
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
    //  Étape 0 — Choix
    // ============================================================

    document.getElementById('btn-known').addEventListener('click', function () {
        clearMessage();
        showStep('known');
    });

    document.getElementById('btn-recover').addEventListener('click', function () {
        clearMessage();
        showStep('recover');
        recoverLoadTypes();
    });

    document.querySelectorAll('[data-back-choice]').forEach(function (btn) {
        btn.addEventListener('click', function () {
            clearMessage();
            showStep('choice');
        });
    });

    // ============================================================
    //  Étape 1A — UID connu
    // ============================================================

    var knownProfil = document.getElementById('known-profil');

    loadProfils(knownProfil);

    document.getElementById('form-known').addEventListener('submit', function (e) {
        e.preventDefault();
        clearMessage();

        var uid = document.getElementById('known-uid').value.trim();
        var email = document.getElementById('known-email').value.trim();
        var profil = knownProfil.value;

        if (!uid || !email || !profil) {
            showMessage('Tous les champs sont obligatoires', true);
            return;
        }
        if (!EMAIL_RE.test(email)) {
            showMessage('Le format de l\'adresse email est invalide', true);
            return;
        }

        post(api('/forgot-password'), { uid: uid, email: email, profil: profil })
            .then(function () {
                document.getElementById('reset-uid').value = uid;
                resetShowStep(uid, true);
            })
            .catch(function (err) {
                showMessage(err.message, true);
            });
    });

    // ============================================================
    //  Étape 1B — Recherche UID (cascades type → ville → établissement)
    // ============================================================

    var typeSel = document.getElementById('recover-type');
    var villeSel = document.getElementById('recover-ville');
    var etabSel = document.getElementById('recover-etab');
    var structuresCache = [];

    loadProfils(document.getElementById('recover-profil'));

    typeSel.addEventListener('change', function () {
        recoverClearVille();
        if (typeSel.value) { recoverLoadVilles(); }
    });

    villeSel.addEventListener('change', function () {
        recoverClearEtab();
        if (villeSel.value) { recoverLoadEtablissements(); }
    });

    function recoverLoadTypes() {
        getJson(api('/structures/types'))
            .then(function (types) {
                fillSelect(typeSel, (types || []).map(function (t) {
                    return { value: t, label: t };
                }));
                recoverClearVille();
            })
            .catch(function (err) { showMessage(err.message, true); });
    }

    function recoverLoadStructures() {
        if (structuresCache.length > 0) { return Promise.resolve(); }
        return getJson(api('/structures'))
            .then(function (list) { structuresCache = list || []; })
            .catch(function (err) { showMessage(err.message, true); throw err; });
    }

    function recoverLoadVilles() {
        var type = typeSel.value;
        recoverLoadStructures().then(function () {
            var villes = [];
            structuresCache.forEach(function (s) {
                if (type && s.surtype !== type) { return; }
                if (s.ville && villes.indexOf(s.ville) === -1) { villes.push(s.ville); }
            });
            villes.sort();
            fillSelect(villeSel, villes.map(function (v) { return { value: v, label: v }; }));
            villeSel.disabled = false;
            recoverClearEtab();
        });
    }

    function recoverLoadEtablissements() {
        var type = typeSel.value;
        var ville = villeSel.value;
        var list = structuresCache.filter(function (s) {
            if (type && s.surtype !== type) { return false; }
            if (ville && s.ville !== ville) { return false; }
            return true;
        });
        list.sort(function (a, b) { return (a.nom || '').localeCompare(b.nom || ''); });
        fillSelect(etabSel, list.map(function (s) {
            return { value: s.siren || s.nom, label: s.nom || s.siren };
        }));
        etabSel.disabled = false;
    }

    function recoverClearVille() {
        villeSel.innerHTML = '';
        villeSel.disabled = true;
        recoverClearEtab();
    }

    function recoverClearEtab() {
        etabSel.innerHTML = '';
        etabSel.disabled = true;
    }

    document.getElementById('form-recover').addEventListener('submit', function (e) {
        e.preventDefault();
        clearMessage();

        var payload = {
            nom: document.getElementById('recover-nom').value.trim(),
            prenom: document.getElementById('recover-prenom').value.trim(),
            email: document.getElementById('recover-email').value.trim(),
            profil: document.getElementById('recover-profil').value,
            typeEtablissement: typeSel.value,
            ville: villeSel.value,
            etablissement: etabSel.value
        };

        var allFilled = Object.keys(payload).every(function (k) { return payload[k]; });
        if (!allFilled) {
            showMessage('Tous les champs sont obligatoires', true);
            return;
        }
        if (!EMAIL_RE.test(payload.email)) {
            showMessage('Le format de l\'adresse email est invalide', true);
            return;
        }

        post(api('/recover-uid'), payload)
            .then(function () {
                showMessage('Si un compte correspond à ces informations et permet de réinitialiser son mot de passe, un code vous a été envoyé.', false);
                resetShowStep('', false);
            })
            .catch(function (err) {
                showMessage(err.message, true);
            });
    });

    // ============================================================
    //  Étape 2 — Réinitialisation du mot de passe
    // ============================================================

    var charteBlock = document.getElementById('charte-block');
    var resetUidField = document.getElementById('reset-uid-field');

    document.getElementById('form-reset').addEventListener('submit', function (e) {
        e.preventDefault();
        clearMessage();

        var uid = document.getElementById('reset-uid').value.trim();
        var code = document.getElementById('reset-code').value.trim();
        var newPassword = document.getElementById('reset-newpass').value;
        var confirmPassword = document.getElementById('reset-confirm').value;
        var charteAccepted = charteBlock.hidden || document.getElementById('reset-charte').checked;

        if (!code) {
            showMessage('Le code est obligatoire', true);
            return;
        }
        if (!uid && !resetUidField.style.display) {
            showMessage('L\'identifiant est obligatoire', true);
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

        post(api('/reset-password'), {
            uid: uid,
            code: code,
            newPassword: newPassword,
            confirmPassword: confirmPassword,
            charteAccepted: charteAccepted
        })
            .then(function () {
                showStep('success');
            })
            .catch(function (err) {
                showMessage(err.message, true);
                if (err.code === 'CHARTE_REQUIRED') {
                    charteBlock.hidden = false;
                }
            });
    });

    function resetShowStep(uid, showUid) {
        resetUidField.style.display = showUid ? '' : 'none';
        resetLoadCharteStatus(uid);
        showStep('reset');
    }

    function resetLoadCharteStatus(uid) {
        charteBlock.hidden = true;
        if (!uid) { return; }

        getJson(api('/charte-status?uid=' + encodeURIComponent(uid)))
            .then(function (status) {
                if (status && status.charteRequired) {
                    document.getElementById('charte-link').href = status.charteUrl || '#';
                    charteBlock.hidden = false;
                }
            })
            .catch(function () { charteBlock.hidden = true; });
    }

    // ============================================================
    //  Profils (partagé entre les deux formulaires)
    // ============================================================

    function loadProfils(select) {
        getJson(api('/structures/profils'))
            .then(function (profils) {
                fillSelect(select, (profils || []).map(function (p) {
                    return { value: p, label: p };
                }));
            })
            .catch(function (err) { showMessage(err.message, true); });
    }

    // ============================================================
    //  Effacement automatique des messages
    // ============================================================

    document.querySelectorAll('input, select').forEach(function (el) {
        el.addEventListener('input', clearMessage);
        el.addEventListener('change', clearMessage);
    });
})();
