import {
    apiFetch,
    renderNavbar,
    loginWithCredentials,
    redirectByRole,
    showSuccessToast
} from './auth.js';

const REMEMBERED_EMAIL_KEY = 'rentatools_remembered_email';

// Expresiones regulares de validación esencial
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
const PHONE_DIGITS_REGEX = /^[0-9]{7,15}$/;
const TAX_ID_REGEX = /^[A-Z0-9-]{5,20}$/;

document.addEventListener('DOMContentLoaded', () => {
    // Inicializar navbar (mantiene compatibilidad si se activa o consulta el host)
    renderNavbar('auth');

    // Comprobar parámetro de expiración de sesión (HTTP 401)
    const params = new URLSearchParams(window.location.search);
    if (params.get('expired') === 'true') {
        const expiredBanner = document.getElementById('expiredSessionBanner');
        if (expiredBanner) {
            expiredBanner.classList.remove('d-none');
        }
    }

    // Inicializar funcionalidad "Recordarme"
    initRememberMe();

    // Inicializar alternador de contraseña
    initPasswordToggle();

    // Inicializar selector de roles Cliente / Proveedor
    initRoleTabs();

    // Inicializar sanitizadores y validaciones en tiempo real (solo números en teléfono, etc.)
    initInputSanitizers();

    // Inicializar enlaces de cambio de vista (Login <-> Registros)
    initViewTransitions();

    // Inicializar enlace de recuperación de contraseña
    initForgotPassword();

    // Vincular eventos de formularios y semillas
    bindEvents();
});

/* ==========================================================================
   Funciones de Asistencia de Validación e Interfaz
   ========================================================================== */

export function setFieldError(inputEl, message) {
    if (!inputEl) return;
    inputEl.classList.add('is-invalid');
    const fieldContainer = inputEl.closest('.rt-auth-field');
    if (!fieldContainer) return;

    let errorEl = fieldContainer.querySelector('.rt-auth-field-error');
    if (!errorEl) {
        errorEl = document.createElement('div');
        errorEl.className = 'rt-auth-field-error';
        fieldContainer.appendChild(errorEl);
    }
    errorEl.innerHTML = `<i class="bi bi-exclamation-circle-fill"></i><span>${message}</span>`;
}

export function clearFieldError(inputEl) {
    if (!inputEl) return;
    inputEl.classList.remove('is-invalid');
    const fieldContainer = inputEl.closest('.rt-auth-field');
    if (fieldContainer) {
        const errorEl = fieldContainer.querySelector('.rt-auth-field-error');
        if (errorEl) {
            errorEl.remove();
        }
    }
}

export function clearFormErrors(formEl) {
    if (!formEl) return;
    formEl.querySelectorAll('.is-invalid').forEach(el => el.classList.remove('is-invalid'));
    formEl.querySelectorAll('.rt-auth-field-error').forEach(el => el.remove());
}

/* ==========================================================================
   Sanitización y Validación en Tiempo Real (Input Events)
   ========================================================================== */

function initInputSanitizers() {
    // 1. Teléfonos: permitir EXCLUSIVAMENTE números (0-9) en tiempo real
    const phoneInputs = [
        document.getElementById('clientPhone'),
        document.getElementById('provPhone')
    ];

    phoneInputs.forEach(input => {
        if (!input) return;

        // Limpiar al escribir
        input.addEventListener('input', (e) => {
            const rawVal = e.target.value;
            const cleaned = rawVal.replace(/\D/g, ''); // Eliminar cualquier caracter que no sea dígito
            if (rawVal !== cleaned) {
                e.target.value = cleaned;
            }
            if (input.classList.contains('is-invalid')) {
                if (cleaned === '' || PHONE_DIGITS_REGEX.test(cleaned)) {
                    clearFieldError(input);
                }
            }
        });

        // Limpiar al pegar desde portapapeles
        input.addEventListener('paste', () => {
            setTimeout(() => {
                input.value = input.value.replace(/\D/g, '');
            }, 0);
        });

        // Validación al desenfocar el campo (blur)
        input.addEventListener('blur', () => {
            const val = input.value.trim();
            if (val !== '' && !PHONE_DIGITS_REGEX.test(val)) {
                setFieldError(input, 'El teléfono debe contener entre 7 y 15 dígitos numéricos');
            } else {
                clearFieldError(input);
            }
        });
    });

    // 2. Nombres de persona: restringir números y caracteres especiales no válidos
    const nameInputs = [
        document.getElementById('clientFullName'),
        document.getElementById('provFullName')
    ];

    nameInputs.forEach(input => {
        if (!input) return;
        input.addEventListener('input', (e) => {
            // Permitir letras del abecedario, vocales tildadas, diéresis, ñ, espacios, puntos o guiones
            const cleaned = e.target.value.replace(/[^a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\s.'-]/g, '');
            if (e.target.value !== cleaned) {
                e.target.value = cleaned;
            }
            if (input.classList.contains('is-invalid') && cleaned.trim().length >= 3) {
                clearFieldError(input);
            }
        });
    });

    // 3. NIT / RUC: convertir a mayúsculas y permitir solo caracteres alfanuméricos y guiones
    const taxIdInput = document.getElementById('provTaxId');
    if (taxIdInput) {
        taxIdInput.addEventListener('input', (e) => {
            const cleaned = e.target.value.toUpperCase().replace(/[^A-Z0-9-]/g, '');
            if (e.target.value !== cleaned) {
                e.target.value = cleaned;
            }
            if (taxIdInput.classList.contains('is-invalid') && TAX_ID_REGEX.test(cleaned)) {
                clearFieldError(taxIdInput);
            }
        });
    }

    // 4. Limpieza dinámica de error al escribir en cualquier campo de texto
    document.querySelectorAll('.rt-auth-text-input').forEach(input => {
        input.addEventListener('input', () => {
            if (input.classList.contains('is-invalid')) {
                clearFieldError(input);
            }
        });
    });
}

function initRememberMe() {
    const rememberMeCheckbox = document.getElementById('rememberMeCheckbox');
    const emailInput = document.getElementById('loginEmail');
    if (!rememberMeCheckbox || !emailInput) return;

    try {
        const savedEmail = localStorage.getItem(REMEMBERED_EMAIL_KEY);
        if (savedEmail && savedEmail.trim() !== '') {
            emailInput.value = savedEmail.trim();
            rememberMeCheckbox.checked = true;
        }
    } catch {
        // En caso de que el almacenamiento local esté bloqueado
    }
}

function handleRememberMeSave(email) {
    const rememberMeCheckbox = document.getElementById('rememberMeCheckbox');
    if (!rememberMeCheckbox) return;

    try {
        if (rememberMeCheckbox.checked && email) {
            localStorage.setItem(REMEMBERED_EMAIL_KEY, email);
        } else {
            localStorage.removeItem(REMEMBERED_EMAIL_KEY);
        }
    } catch {
        // ignorar fallo en localStorage
    }
}

function initPasswordToggle() {
    const toggleBtn = document.getElementById('togglePasswordBtn');
    const passwordInput = document.getElementById('loginPassword');
    if (!toggleBtn || !passwordInput) return;

    toggleBtn.addEventListener('click', () => {
        const isPassword = passwordInput.type === 'password';
        passwordInput.type = isPassword ? 'text' : 'password';
        const icon = toggleBtn.querySelector('i');
        if (icon) {
            icon.className = isPassword ? 'bi bi-eye-slash text-warning' : 'bi bi-eye';
        }
    });
}

function initRoleTabs() {
    const clientTab = document.getElementById('roleTabClient');
    const providerTab = document.getElementById('roleTabProvider');

    if (!clientTab || !providerTab) return;

    const setRole = (role) => {
        if (role === 'CLIENTE') {
            clientTab.classList.add('active');
            clientTab.setAttribute('aria-selected', 'true');
            providerTab.classList.remove('active');
            providerTab.setAttribute('aria-selected', 'false');
        } else {
            providerTab.classList.add('active');
            providerTab.setAttribute('aria-selected', 'true');
            clientTab.classList.remove('active');
            clientTab.setAttribute('aria-selected', 'false');
        }
    };

    clientTab.addEventListener('click', () => setRole('CLIENTE'));
    providerTab.addEventListener('click', () => setRole('PROVEEDOR'));
}

function initViewTransitions() {
    const viewLogin = document.getElementById('authViewLogin');
    const viewRegisterClient = document.getElementById('authViewRegisterClient');
    const viewRegisterProvider = document.getElementById('authViewRegisterProvider');

    const showView = (targetView) => {
        // Limpiar errores visuales al cambiar de pantalla
        clearFormErrors(viewLogin?.querySelector('form'));
        clearFormErrors(viewRegisterClient?.querySelector('form'));
        clearFormErrors(viewRegisterProvider?.querySelector('form'));

        [viewLogin, viewRegisterClient, viewRegisterProvider].forEach(v => {
            if (v) v.classList.add('d-none');
        });
        if (targetView) {
            targetView.classList.remove('d-none');
            targetView.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        }
    };

    document.getElementById('btnGoRegisterClient')?.addEventListener('click', () => {
        showView(viewRegisterClient);
    });

    document.getElementById('btnGoRegisterProvider')?.addEventListener('click', () => {
        showView(viewRegisterProvider);
    });

    document.querySelectorAll('.btnBackToLogin').forEach(btn => {
        btn.addEventListener('click', () => {
            showView(viewLogin);
        });
    });
}

function initForgotPassword() {
    const link = document.getElementById('forgotPasswordLink');
    if (!link) return;

    link.addEventListener('click', (e) => {
        e.preventDefault();
        const modalEl = document.getElementById('forgotPasswordModal');
        if (modalEl && window.bootstrap && window.bootstrap.Modal) {
            const modalInstance = window.bootstrap.Modal.getOrCreateInstance(modalEl);
            modalInstance.show();
        } else {
            alert('Para recuperar tu contraseña, por favor contacta al administrador en soporte@rentatools.com');
        }
    });
}

function bindEvents() {
    // 1. Formulario de Inicio de Sesión
    const loginForm = document.getElementById('loginForm');
    if (loginForm) {
        loginForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            clearFormErrors(loginForm);

            const emailInput = document.getElementById('loginEmail');
            const passInput = document.getElementById('loginPassword');
            const email = (emailInput?.value || '').trim();
            const password = passInput?.value || '';

            let hasError = false;
            let firstInvalid = null;

            if (!email) {
                setFieldError(emailInput, 'El correo electrónico es requerido');
                hasError = true;
                firstInvalid = firstInvalid || emailInput;
            } else if (!EMAIL_REGEX.test(email)) {
                setFieldError(emailInput, 'Ingresa un correo electrónico válido (ej. usuario@rentatools.com)');
                hasError = true;
                firstInvalid = firstInvalid || emailInput;
            }

            if (!password) {
                setFieldError(passInput, 'Ingresa tu contraseña');
                hasError = true;
                firstInvalid = firstInvalid || passInput;
            } else if (password.length < 8) {
                setFieldError(passInput, 'La contraseña debe tener al menos 8 caracteres');
                hasError = true;
                firstInvalid = firstInvalid || passInput;
            }

            if (hasError) {
                firstInvalid?.focus();
                return;
            }

            const btn = document.getElementById('loginSubmitBtn');
            if (btn) {
                btn.disabled = true;
                btn.innerHTML = `
                    <span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
                    <span>Ingresando...</span>
                `;
            }

            try {
                const tokenResp = await loginWithCredentials(email, password);
                handleRememberMeSave(email);
                showSuccessToast(`Bienvenido, ${tokenResp?.user?.fullName || email}`);
                setTimeout(() => {
                    redirectByRole(tokenResp?.user);
                }, 450);
            } catch {
                // ProblemDetail ya mostrado en pantalla vía rt-alert-container
            } finally {
                if (btn) {
                    btn.disabled = false;
                    btn.innerHTML = `
                        <span>Iniciar sesión</span>
                        <i class="bi bi-arrow-right"></i>
                    `;
                }
            }
        });
    }

    // 2. Registro de Cliente (POST /api/v1/auth/register/client)
    const clientForm = document.getElementById('registerClientForm');
    if (clientForm) {
        clientForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            clearFormErrors(clientForm);

            const nameInput = document.getElementById('clientFullName');
            const emailInput = document.getElementById('clientEmail');
            const passInput = document.getElementById('clientPassword');
            const phoneInput = document.getElementById('clientPhone');

            const fullName = (nameInput?.value || '').trim();
            const email = (emailInput?.value || '').trim();
            const password = passInput?.value || '';
            const phone = (phoneInput?.value || '').trim() || null;

            let hasError = false;
            let firstInvalid = null;

            if (!fullName || fullName.length < 3) {
                setFieldError(nameInput, 'Ingresa tu nombre completo (mínimo 3 caracteres)');
                hasError = true;
                firstInvalid = firstInvalid || nameInput;
            }

            if (!email) {
                setFieldError(emailInput, 'El correo electrónico es requerido');
                hasError = true;
                firstInvalid = firstInvalid || emailInput;
            } else if (!EMAIL_REGEX.test(email)) {
                setFieldError(emailInput, 'Ingresa un formato de correo válido (ej. nombre@ejemplo.com)');
                hasError = true;
                firstInvalid = firstInvalid || emailInput;
            }

            if (!password) {
                setFieldError(passInput, 'La contraseña es requerida');
                hasError = true;
                firstInvalid = firstInvalid || passInput;
            } else if (password.length < 8) {
                setFieldError(passInput, 'La contraseña debe tener al menos 8 caracteres');
                hasError = true;
                firstInvalid = firstInvalid || passInput;
            } else if (password.length > 72) {
                setFieldError(passInput, 'La contraseña no puede superar los 72 caracteres');
                hasError = true;
                firstInvalid = firstInvalid || passInput;
            }

            if (phone && !PHONE_DIGITS_REGEX.test(phone)) {
                setFieldError(phoneInput, 'Solo números permitidos (entre 7 y 15 dígitos)');
                hasError = true;
                firstInvalid = firstInvalid || phoneInput;
            }

            if (hasError) {
                firstInvalid?.focus();
                return;
            }

            const btn = document.getElementById('registerClientBtn');
            if (btn) {
                btn.disabled = true;
                btn.innerHTML = `
                    <span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
                    <span>Registrando...</span>
                `;
            }

            try {
                await apiFetch('/api/v1/auth/register/client', {
                    method: 'POST',
                    body: { email, password, fullName, phone },
                    skipAuthHeader: true,
                    skipAuthRedirect: true
                });

                const tokenResp = await loginWithCredentials(email, password);
                showSuccessToast('Cuenta de CLIENTE registrada e iniciada correctamente.');
                setTimeout(() => {
                    redirectByRole(tokenResp?.user);
                }, 500);
            } catch {
                // ProblemDetail ya mostrado en pantalla
            } finally {
                if (btn) {
                    btn.disabled = false;
                    btn.innerHTML = `
                        <i class="bi bi-person-check me-1"></i>
                        <span>Crear Cuenta de Cliente</span>
                    `;
                }
            }
        });
    }

    // 3. Registro de Proveedor (POST /api/v1/auth/register/provider)
    const providerForm = document.getElementById('registerProviderForm');
    if (providerForm) {
        providerForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            clearFormErrors(providerForm);

            const nameInput = document.getElementById('provFullName');
            const phoneInput = document.getElementById('provPhone');
            const bizInput = document.getElementById('provBusinessName');
            const taxInput = document.getElementById('provTaxId');
            const emailInput = document.getElementById('provEmail');
            const passInput = document.getElementById('provPassword');

            const fullName = (nameInput?.value || '').trim();
            const phone = (phoneInput?.value || '').trim() || null;
            const businessName = (bizInput?.value || '').trim();
            const taxId = (taxInput?.value || '').trim();
            const email = (emailInput?.value || '').trim();
            const password = passInput?.value || '';

            let hasError = false;
            let firstInvalid = null;

            if (!fullName || fullName.length < 3) {
                setFieldError(nameInput, 'Ingresa el nombre del representante (mínimo 3 caracteres)');
                hasError = true;
                firstInvalid = firstInvalid || nameInput;
            }

            if (phone && !PHONE_DIGITS_REGEX.test(phone)) {
                setFieldError(phoneInput, 'Solo números permitidos (entre 7 y 15 dígitos)');
                hasError = true;
                firstInvalid = firstInvalid || phoneInput;
            }

            if (!businessName || businessName.length < 3) {
                setFieldError(bizInput, 'Ingresa la razón social de la empresa (mínimo 3 caracteres)');
                hasError = true;
                firstInvalid = firstInvalid || bizInput;
            }

            if (!taxId || !TAX_ID_REGEX.test(taxId)) {
                setFieldError(taxInput, 'Ingresa un NIT o RUC válido (5 a 20 caracteres alfanuméricos)');
                hasError = true;
                firstInvalid = firstInvalid || taxInput;
            }

            if (!email) {
                setFieldError(emailInput, 'El correo corporativo es requerido');
                hasError = true;
                firstInvalid = firstInvalid || emailInput;
            } else if (!EMAIL_REGEX.test(email)) {
                setFieldError(emailInput, 'Ingresa un formato de correo corporativo válido');
                hasError = true;
                firstInvalid = firstInvalid || emailInput;
            }

            if (!password) {
                setFieldError(passInput, 'La contraseña es requerida');
                hasError = true;
                firstInvalid = firstInvalid || passInput;
            } else if (password.length < 8) {
                setFieldError(passInput, 'La contraseña debe tener al menos 8 caracteres');
                hasError = true;
                firstInvalid = firstInvalid || passInput;
            } else if (password.length > 72) {
                setFieldError(passInput, 'La contraseña no puede superar los 72 caracteres');
                hasError = true;
                firstInvalid = firstInvalid || passInput;
            }

            if (hasError) {
                firstInvalid?.focus();
                return;
            }

            const btn = document.getElementById('registerProviderBtn');
            if (btn) {
                btn.disabled = true;
                btn.innerHTML = `
                    <span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
                    <span>Registrando...</span>
                `;
            }

            try {
                await apiFetch('/api/v1/auth/register/provider', {
                    method: 'POST',
                    body: { email, password, fullName, phone, businessName, taxId },
                    skipAuthHeader: true,
                    skipAuthRedirect: true
                });

                const tokenResp = await loginWithCredentials(email, password);
                showSuccessToast('Perfil de PROVEEDOR registrado. Pendiente de aprobación por ADMIN.');
                setTimeout(() => {
                    redirectByRole(tokenResp?.user);
                }, 600);
            } catch {
                // ProblemDetail ya mostrado en pantalla
            } finally {
                if (btn) {
                    btn.disabled = false;
                    btn.innerHTML = `
                        <i class="bi bi-building-check me-1"></i>
                        <span>Registrar Perfil de Proveedor</span>
                    `;
                }
            }
        });
    }

    // Botones de autocompletado de cuentas semilla
    document.querySelectorAll('.demo-fill-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const email = btn.getAttribute('data-email');
            const pass = btn.getAttribute('data-pass');
            const emailInput = document.getElementById('loginEmail');
            const passInput = document.getElementById('loginPassword');

            // Asegurar que la vista de login esté activa
            const viewLogin = document.getElementById('authViewLogin');
            const viewRegisterClient = document.getElementById('authViewRegisterClient');
            const viewRegisterProvider = document.getElementById('authViewRegisterProvider');
            if (viewLogin && viewLogin.classList.contains('d-none')) {
                viewLogin.classList.remove('d-none');
                viewRegisterClient?.classList.add('d-none');
                viewRegisterProvider?.classList.add('d-none');
            }

            // Sincronizar tab de rol visual si aplica
            const clientTab = document.getElementById('roleTabClient');
            const providerTab = document.getElementById('roleTabProvider');
            if (email.includes('proveedor') && providerTab) {
                providerTab.click();
            } else if (email.includes('cliente') && clientTab) {
                clientTab.click();
            }

            if (emailInput) emailInput.value = email;
            if (passInput) passInput.value = pass;
            loginForm?.requestSubmit();
        });
    });
}
