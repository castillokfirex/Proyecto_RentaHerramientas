import {
    apiFetch,
    renderNavbar,
    loginWithCredentials,
    redirectByRole,
    showSuccessToast
} from './auth.js';

document.addEventListener('DOMContentLoaded', () => {
    renderNavbar('auth');

    const params = new URLSearchParams(window.location.search);
    if (params.get('expired') === 'true') {
        document.getElementById('expiredSessionBanner')?.classList.remove('d-none');
    }

    bindEvents();
});

function bindEvents() {
    // 1. Login Form
    const loginForm = document.getElementById('loginForm');
    if (loginForm) {
        loginForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const email = document.getElementById('loginEmail').value.trim();
            const password = document.getElementById('loginPassword').value;
            const btn = document.getElementById('loginSubmitBtn');

            if (btn) btn.disabled = true;
            try {
                const tokenResp = await loginWithCredentials(email, password);
                showSuccessToast(`Bienvenido, ${tokenResp?.user?.fullName || email}`);
                setTimeout(() => {
                    redirectByRole(tokenResp?.user);
                }, 450);
            } catch {
                // ProblemDetail ya mostrado en pantalla
            } finally {
                if (btn) btn.disabled = false;
            }
        });
    }

    // 2. Registro de Cliente (POST /api/v1/auth/register/client)
    const clientForm = document.getElementById('registerClientForm');
    if (clientForm) {
        clientForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const fullName = document.getElementById('clientFullName').value.trim();
            const email = document.getElementById('clientEmail').value.trim();
            const password = document.getElementById('clientPassword').value;
            const phone = document.getElementById('clientPhone').value.trim() || null;
            const btn = document.getElementById('registerClientBtn');

            if (btn) btn.disabled = true;
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
                if (btn) btn.disabled = false;
            }
        });
    }

    // 3. Registro de Proveedor (POST /api/v1/auth/register/provider)
    const providerForm = document.getElementById('registerProviderForm');
    if (providerForm) {
        providerForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const fullName = document.getElementById('provFullName').value.trim();
            const phone = document.getElementById('provPhone').value.trim() || null;
            const businessName = document.getElementById('provBusinessName').value.trim();
            const taxId = document.getElementById('provTaxId').value.trim();
            const email = document.getElementById('provEmail').value.trim();
            const password = document.getElementById('provPassword').value;
            const btn = document.getElementById('registerProviderBtn');

            if (btn) btn.disabled = true;
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
                if (btn) btn.disabled = false;
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
            if (emailInput) emailInput.value = email;
            if (passInput) passInput.value = pass;
            loginForm?.requestSubmit();
        });
    });
}
