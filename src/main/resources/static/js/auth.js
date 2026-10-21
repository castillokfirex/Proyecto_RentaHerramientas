/**
 * RentaTools — Helper compartido de Autenticación JWT, Consumo de API (fetch),
 * redirección automática ante HTTP 401 a auth.html y manejo de errores RFC 9457 (ProblemDetail).
 */

const TOKEN_KEYS = ['accessToken', 'jwt', 'token', 'rentatools_access_token'];
const REFRESH_KEYS = ['refreshToken', 'rentatools_refresh_token'];
const USER_KEYS = ['user', 'currentUser', 'rentatools_user'];

export class ProblemDetailError extends Error {
    constructor(problem, status) {
        const detailMsg = problem?.detail || problem?.message || problem?.title || `Error HTTP ${status}`;
        super(detailMsg);
        this.name = 'ProblemDetailError';
        this.status = problem?.status || status;
        this.title = problem?.title || 'Error en la solicitud';
        this.detail = detailMsg;
        this.code = problem?.code || 'API_ERROR';
        this.type = problem?.type || 'about:blank';
        this.instance = problem?.instance || '';
        this.errors = problem?.errors || null;
        this.raw = problem;
    }
}

export function getAccessToken() {
    for (const key of TOKEN_KEYS) {
        const val = localStorage.getItem(key);
        if (val && val.trim() !== '' && val !== 'null' && val !== 'undefined') {
            return val.trim();
        }
    }
    return null;
}

export function getRefreshToken() {
    for (const key of REFRESH_KEYS) {
        const val = localStorage.getItem(key);
        if (val && val.trim() !== '' && val !== 'null' && val !== 'undefined') {
            return val.trim();
        }
    }
    return null;
}

export function parseJwtPayload(token) {
    if (!token) return null;
    try {
        const parts = token.split('.');
        if (parts.length !== 3) return null;
        const base64Url = parts[1];
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        const jsonPayload = decodeURIComponent(
            atob(base64)
                .split('')
                .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
                .join('')
        );
        return JSON.parse(jsonPayload);
    } catch {
        return null;
    }
}

export function getCurrentUser() {
    for (const key of USER_KEYS) {
        const raw = localStorage.getItem(key);
        if (raw) {
            try {
                const parsed = JSON.parse(raw);
                if (parsed && (parsed.id || parsed.email || parsed.roles)) {
                    return normalizeUser(parsed);
                }
            } catch {
                // ignore malformed JSON
            }
        }
    }
    const token = getAccessToken();
    const claims = parseJwtPayload(token);
    if (!claims) return null;
    return normalizeUser({
        id: claims.sub,
        email: claims.email || claims.sub,
        fullName: claims.fullName || claims.name || claims.email || 'Usuario Autenticado',
        roles: Array.isArray(claims.roles) ? claims.roles : []
    });
}

function normalizeUser(u) {
    const roles = Array.isArray(u.roles)
        ? u.roles.map(r => String(r).replace(/^ROLE_/, '').toUpperCase())
        : [];
    return {
        id: u.id || u.userId || u.sub || null,
        email: u.email || '',
        fullName: u.fullName || u.name || u.email || 'Usuario',
        phone: u.phone || null,
        status: u.status || 'ACTIVE',
        roles,
        providerProfile: u.providerProfile || null
    };
}

export function isAuthenticated() {
    const token = getAccessToken();
    if (!token) return false;
    const claims = parseJwtPayload(token);
    if (claims && claims.exp) {
        const nowSec = Math.floor(Date.now() / 1000);
        if (claims.exp < nowSec) {
            return false;
        }
    }
    return true;
}

export function hasRole(role) {
    if (!isAuthenticated()) return false;
    const user = getCurrentUser();
    if (!user || !Array.isArray(user.roles)) return false;
    const normalized = String(role).replace(/^ROLE_/, '').toUpperCase();
    return user.roles.includes(normalized);
}

export function getLandingPageForUser(user = getCurrentUser()) {
    const roles = (user?.roles || []).map(r => String(r).replace(/^ROLE_/, '').toUpperCase());
    if (roles.includes('ADMIN')) {
        return 'admin-dashboard.html';
    }
    if (roles.includes('PROVEEDOR')) {
        return 'provider-dashboard.html';
    }
    return 'catalog.html';
}

export function redirectByRole(user = getCurrentUser()) {
    window.location.href = getLandingPageForUser(user);
}

export function saveSession({ accessToken, refreshToken, user }) {
    if (accessToken) {
        TOKEN_KEYS.forEach(k => localStorage.setItem(k, accessToken));
    }
    if (refreshToken) {
        REFRESH_KEYS.forEach(k => localStorage.setItem(k, refreshToken));
    }
    if (user) {
        const normalized = normalizeUser(user);
        USER_KEYS.forEach(k => localStorage.setItem(k, JSON.stringify(normalized)));
    } else if (accessToken) {
        const claims = parseJwtPayload(accessToken);
        if (claims) {
            const derived = normalizeUser({
                id: claims.sub,
                email: claims.email,
                fullName: claims.email,
                roles: claims.roles || []
            });
            USER_KEYS.forEach(k => localStorage.setItem(k, JSON.stringify(derived)));
        }
    }
}

export function clearSession() {
    [...TOKEN_KEYS, ...REFRESH_KEYS, ...USER_KEYS].forEach(k => localStorage.removeItem(k));
}

export async function loginWithCredentials(email, password) {
    const data = await apiFetch('/api/v1/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
        skipAuthHeader: true,
        skipAuthRedirect: true
    });
    saveSession({
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
        user: data.user
    });
    return data;
}

export async function logout() {
    const refreshToken = getRefreshToken();
    const accessToken = getAccessToken();
    if (accessToken && refreshToken) {
        try {
            await fetch('/api/v1/auth/logout', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${accessToken}`
                },
                body: JSON.stringify({ refreshToken })
            });
        } catch {
            // Ignorar error de red al cerrar sesión local
        }
    }
    clearSession();
    window.location.href = 'auth.html';
}

/**
 * Wrapper sobre fetch() que adjunta Authorization: Bearer <JWT>,
 * redirige automáticamente a auth.html ante HTTP 401
 * y traduce errores RFC 9457 ProblemDetail a ProblemDetailError mostrando alerta visual.
 */
export async function apiFetch(url, options = {}) {
    const {
        skipAuthHeader = false,
        skipAuthRedirect = false,
        suppressErrorAlert = false,
        headers: customHeaders = {},
        ...restOptions
    } = options;

    const headers = new Headers(customHeaders);
    const token = getAccessToken();

    if (!skipAuthHeader && token) {
        headers.set('Authorization', `Bearer ${token}`);
    }

    if (!headers.has('Accept')) {
        headers.set('Accept', 'application/json, application/problem+json');
    }

    const isFormData = restOptions.body instanceof FormData;
    if (!isFormData && restOptions.body && typeof restOptions.body === 'object' && !(restOptions.body instanceof Blob)) {
        restOptions.body = JSON.stringify(restOptions.body);
    }
    if (!isFormData && restOptions.body && !headers.has('Content-Type')) {
        headers.set('Content-Type', 'application/json');
    }

    const response = await fetch(url, {
        ...restOptions,
        headers
    });

    if (response.status === 204) {
        return null;
    }

    const contentType = response.headers.get('content-type') || '';
    const isJson = contentType.includes('application/json') || contentType.includes('application/problem+json');

    if (!response.ok) {
        let problemPayload = null;
        if (isJson) {
            try {
                problemPayload = await response.json();
            } catch {
                problemPayload = null;
            }
        } else {
            const text = await response.text().catch(() => '');
            problemPayload = {
                title: response.statusText || 'Error HTTP',
                status: response.status,
                detail: text || `La solicitud falló con estado HTTP ${response.status}`,
                code: `HTTP_${response.status}`
            };
        }

        const err = new ProblemDetailError(problemPayload, response.status);

        // Redirección automática a auth.html cuando la API responde 401 Unauthorized
        if (response.status === 401 && !skipAuthRedirect) {
            clearSession();
            const isAlreadyOnAuthPage = window.location.pathname.endsWith('/auth.html') || window.location.pathname.endsWith('auth.html');
            if (!isAlreadyOnAuthPage) {
                const returnTo = encodeURIComponent(window.location.pathname.split('/').pop() + window.location.search);
                window.location.href = `auth.html?expired=true&returnTo=${returnTo}`;
                throw err;
            }
        }

        if (!suppressErrorAlert) {
            showProblemAlert(err);
        }
        throw err;
    }

    if (isJson) {
        return await response.json();
    }
    return await response.blob();
}

/**
 * Descarga un recurso binario autenticado (ej. fotos de inspección) y devuelve un ObjectURL.
 */
export async function fetchAuthenticatedBlobUrl(url) {
    const token = getAccessToken();
    const headers = {};
    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }
    const resp = await fetch(url, { headers });
    if (resp.status === 401) {
        clearSession();
        window.location.href = 'auth.html';
        throw new Error('Sesión expirada (401)');
    }
    if (!resp.ok) {
        throw new Error(`No se pudo cargar recurso (${resp.status})`);
    }
    const blob = await resp.blob();
    return URL.createObjectURL(blob);
}

/**
 * Muestra una alerta flotante clara con el detalle RFC 9457 (ProblemDetail).
 */
export function showProblemAlert(errorOrMessage, type = 'danger') {
    let container = document.getElementById('rt-alert-container');
    if (!container) {
        container = document.createElement('div');
        container.id = 'rt-alert-container';
        document.body.appendChild(container);
    }

    const alertEl = document.createElement('div');
    const isProblem = errorOrMessage instanceof ProblemDetailError || (errorOrMessage && errorOrMessage.detail && errorOrMessage.title);

    if (isProblem) {
        const title = escapeHtml(errorOrMessage.title || 'Error');
        const detail = escapeHtml(errorOrMessage.detail || errorOrMessage.message || 'Ocurrió un error inesperado.');
        const code = escapeHtml(errorOrMessage.code || 'ERROR');
        const status = errorOrMessage.status || '';

        let fieldErrorsHtml = '';
        if (errorOrMessage.errors && typeof errorOrMessage.errors === 'object') {
            const items = Object.entries(errorOrMessage.errors)
                .map(([field, msg]) => `<li><strong>${escapeHtml(field)}:</strong> ${escapeHtml(String(msg))}</li>`)
                .join('');
            if (items) {
                fieldErrorsHtml = `<ul class="mb-0 mt-2 small ps-3">${items}</ul>`;
            }
        }

        alertEl.className = `alert alert-${type} alert-dismissible fade show rt-problem-alert mb-2`;
        alertEl.setAttribute('role', 'alert');
        alertEl.innerHTML = `
            <div class="d-flex align-items-start justify-content-between">
                <div>
                    <div class="d-flex align-items-center gap-2 mb-1">
                        <i class="bi bi-exclamation-triangle-fill"></i>
                        <strong>${title}</strong>
                        <span class="badge bg-dark font-monospace">${code}${status ? ' · ' + status : ''}</span>
                    </div>
                    <div class="small">${detail}</div>
                    ${fieldErrorsHtml}
                </div>
                <button type="button" class="btn-close ms-2" data-bs-dismiss="alert" aria-label="Cerrar"></button>
            </div>
        `;
    } else {
        const msg = typeof errorOrMessage === 'string'
            ? errorOrMessage
            : (errorOrMessage?.message || 'Operación completada');
        const icon = type === 'success' ? 'bi-check-circle-fill' : 'bi-info-circle-fill';
        alertEl.className = `alert alert-${type} alert-dismissible fade show shadow-sm mb-2`;
        alertEl.setAttribute('role', 'alert');
        alertEl.innerHTML = `
            <div class="d-flex align-items-center gap-2">
                <i class="bi ${icon}"></i>
                <span>${escapeHtml(msg)}</span>
                <button type="button" class="btn-close ms-auto" data-bs-dismiss="alert" aria-label="Cerrar"></button>
            </div>
        `;
    }

    container.appendChild(alertEl);
    setTimeout(() => {
        if (alertEl && alertEl.parentNode) {
            alertEl.classList.remove('show');
            setTimeout(() => alertEl.remove(), 250);
        }
    }, 6500);
}

export function showSuccessToast(message) {
    showProblemAlert(message, 'success');
}

export function escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

export function formatMoney(amount, currency = 'PEN') {
    const num = Number(amount || 0);
    return new Intl.NumberFormat('es-PE', {
        style: 'currency',
        currency,
        minimumFractionDigits: 2
    }).format(num);
}

export function formatDate(dateStr) {
    if (!dateStr) return '—';
    return String(dateStr).substring(0, 10);
}

export function formatDateTime(instantStr) {
    if (!instantStr) return '—';
    try {
        return new Date(instantStr).toLocaleString('es-PE', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });
    } catch {
        return instantStr;
    }
}

/**
 * Renderiza la barra de navegación superior responsiva según el rol autenticado.
 */
export function renderNavbar(activePage = '') {
    const navHost = document.getElementById('rt-navbar-host');
    if (!navHost) return;

    const authenticated = isAuthenticated();
    const user = getCurrentUser();
    const isClient = hasRole('CLIENTE');
    const isProvider = hasRole('PROVEEDOR');
    const isAdmin = hasRole('ADMIN');

    const rolesBadges = user?.roles?.length
        ? user.roles.map(r => `<span class="badge bg-warning text-dark ms-1">${escapeHtml(r)}</span>`).join('')
        : '';

    // Garantizar favicon en la pestaña
    if (!document.querySelector("link[rel*='icon']")) {
        const link = document.createElement('link');
        link.rel = 'icon';
        link.type = 'image/png';
        link.href = 'images/logo.png';
        document.head.appendChild(link);
    }

    navHost.innerHTML = `
        <nav class="navbar navbar-expand-lg navbar-dark rt-navbar d-print-none">
            <div class="container">
                <a class="navbar-brand d-flex align-items-center gap-2" href="catalog.html">
                    <span class="rt-brand-badge-hex">
                        <i class="bi bi-tools text-warning fs-5"></i>
                    </span>
                    <div class="d-flex flex-column">
                        <span class="fw-bold fs-5 tracking-tight text-white lh-1">Renta<span class="text-warning">Tools</span></span>
                        <span class="rt-brand-sub">RENTA DE HERRAMIENTAS</span>
                    </div>
                </a>
                <button class="navbar-toggler border-0" type="button" data-bs-toggle="collapse" data-bs-target="#rtNavbarContent"
                        aria-controls="rtNavbarContent" aria-expanded="false" aria-label="Alternar navegación">
                    <span class="navbar-toggler-icon"></span>
                </button>
                <div class="collapse navbar-collapse" id="rtNavbarContent">
                    <ul class="navbar-nav mx-auto mb-2 mb-lg-0 align-items-lg-center gap-1 gap-lg-3">
                        <li class="nav-item">
                            <a class="nav-link rt-nav-link ${activePage === 'catalog' ? 'active' : ''}" href="catalog.html">
                                <i class="bi bi-house-door me-1"></i>Inicio
                            </a>
                        </li>
                        <li class="nav-item">
                            <a class="nav-link rt-nav-link" href="catalog.html#toolsSection">
                                <i class="bi bi-tools me-1"></i>Catálogo
                            </a>
                        </li>
                        ${(authenticated && (isClient || isAdmin)) ? `
                        <li class="nav-item">
                            <a class="nav-link rt-nav-link ${activePage === 'client-reservations' ? 'active' : ''}" href="client-reservations.html">
                                <i class="bi bi-calendar2-check me-1"></i>Mis Reservas
                            </a>
                        </li>
                        ` : ''}
                        ${(authenticated && (isProvider || isAdmin)) ? `
                        <li class="nav-item">
                            <a class="nav-link rt-nav-link ${activePage === 'provider-dashboard' ? 'active' : ''}" href="provider-dashboard.html">
                                <i class="bi bi-briefcase me-1"></i>Panel Proveedor
                            </a>
                        </li>
                        ` : ''}
                        ${(authenticated && isAdmin) ? `
                        <li class="nav-item">
                            <a class="nav-link rt-nav-link ${activePage === 'admin-dashboard' ? 'active' : ''}" href="admin-dashboard.html">
                                <i class="bi bi-shield-check me-1"></i>Administración
                            </a>
                        </li>
                        ` : ''}
                        ${authenticated ? `
                        <li class="nav-item">
                            <a class="nav-link rt-nav-link ${activePage === 'invoice-view' ? 'active' : ''}" href="invoice-view.html">
                                <i class="bi bi-receipt me-1"></i>Facturación
                            </a>
                        </li>
                        ` : ''}
                    </ul>

                    <div class="d-flex align-items-center gap-2 flex-wrap">
                        ${authenticated ? `
                            <div class="text-light small me-2 d-flex align-items-center">
                                <i class="bi bi-person-check-fill text-warning me-1"></i>
                                <span class="fw-medium">${escapeHtml(user?.fullName || user?.email || 'Usuario')}</span>
                                ${rolesBadges}
                            </div>
                            <button type="button" class="btn btn-sm btn-outline-danger rounded-3" id="rtLogoutBtn">
                                <i class="bi bi-box-arrow-right me-1"></i>Salir
                            </button>
                        ` : `
                            <a href="auth.html" class="btn rt-btn-nav-primary px-3 py-2 fw-semibold d-inline-flex align-items-center gap-2">
                                <i class="bi bi-person-fill"></i>
                                <span>Iniciar Sesión / Registrarse</span>
                            </a>
                        `}
                    </div>
                </div>
            </div>
        </nav>
    `;

    const logoutBtn = document.getElementById('rtLogoutBtn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', () => logout());
    }

    // Modal de Login Rápido montado directamente en document.body para evitar conflictos de stacking context
    let quickModalEl = document.getElementById('rtQuickAuthModal');
    if (!quickModalEl) {
        const modalContainer = document.createElement('div');
        modalContainer.innerHTML = `
        <div class="modal fade d-print-none" id="rtQuickAuthModal" tabindex="-1" aria-labelledby="rtQuickAuthModalLabel" aria-hidden="true" style="z-index: 1060;">
            <div class="modal-dialog modal-dialog-centered">
                <div class="modal-content border-0 shadow-lg" style="border-radius: var(--rt-radius-lg, 1rem); overflow: hidden;">
                    <div class="modal-header bg-dark text-white">
                        <h5 class="modal-title fw-bold" id="rtQuickAuthModalLabel">
                            <i class="bi bi-shield-lock text-warning me-2"></i>Iniciar Sesión
                        </h5>
                        <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Cerrar"></button>
                    </div>
                    <div class="modal-body p-4">
                        <div class="bg-light p-3 rounded-3 mb-3 border">
                            <div class="small fw-bold text-muted mb-2">
                                <i class="bi bi-lightning-charge-fill text-warning me-1"></i>Acceso rápido de prueba (1 clic):
                            </div>
                            <div class="d-flex flex-wrap gap-2">
                                <button type="button" class="btn btn-sm btn-outline-dark demo-fill-client-quick">
                                    <i class="bi bi-person me-1 text-warning"></i>Demo CLIENTE
                                </button>
                                <button type="button" class="btn btn-sm btn-outline-dark demo-fill-provider-quick">
                                    <i class="bi bi-briefcase me-1 text-warning"></i>Demo PROVEEDOR
                                </button>
                            </div>
                        </div>

                        <form id="rtQuickLoginForm">
                            <div class="mb-3">
                                <label for="rtAuthEmail" class="form-label small fw-semibold">Correo Electrónico</label>
                                <input type="email" class="form-control" id="rtAuthEmail" required placeholder="correo@rentatools.com">
                            </div>
                            <div class="mb-3">
                                <label for="rtAuthPassword" class="form-label small fw-semibold">Contraseña</label>
                                <input type="password" class="form-control" id="rtAuthPassword" required placeholder="••••••••">
                            </div>
                            <div class="d-grid gap-2">
                                <button type="submit" id="rtQuickSubmitBtn" class="btn btn-rt-primary py-2">
                                    <i class="bi bi-box-arrow-in-right me-1"></i>Ingresar y Continuar
                                </button>
                                <a href="auth.html" class="btn btn-outline-secondary btn-sm text-center">
                                    ¿No tienes cuenta? Regístrate aquí (auth.html)
                                </a>
                            </div>
                        </form>
                    </div>
                </div>
            </div>
        </div>
        `;
        document.body.appendChild(modalContainer.firstElementChild);
    }

    // Botones de autocompletado demo para pruebas rápidas
    document.querySelectorAll('.demo-fill-client-quick').forEach(btn => {
        btn.addEventListener('click', () => {
            const emailInput = document.getElementById('rtAuthEmail');
            const passInput = document.getElementById('rtAuthPassword');
            if (emailInput) emailInput.value = 'cliente.dev@rentatools.com';
            if (passInput) passInput.value = 'Password123!';
        });
    });

    document.querySelectorAll('.demo-fill-provider-quick').forEach(btn => {
        btn.addEventListener('click', () => {
            const emailInput = document.getElementById('rtAuthEmail');
            const passInput = document.getElementById('rtAuthPassword');
            if (emailInput) emailInput.value = 'proveedor1.dev@rentatools.com';
            if (passInput) passInput.value = 'Password123!';
        });
    });

    const loginForm = document.getElementById('rtQuickLoginForm');
    if (loginForm) {
        loginForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const email = document.getElementById('rtAuthEmail').value.trim();
            const password = document.getElementById('rtAuthPassword').value;
            try {
                await loginWithCredentials(email, password);
                showSuccessToast('Sesión iniciada correctamente');
                window.location.reload();
            } catch {
                // El error ProblemDetail ya es mostrado por apiFetch
            }
        });
    }

    // Botón flotante para volver arriba sin saltos ni espacios en blanco
    let backToTop = document.getElementById('rtBackToTopBtn');
    if (!backToTop) {
        backToTop = document.createElement('button');
        backToTop.id = 'rtBackToTopBtn';
        backToTop.type = 'button';
        backToTop.className = 'btn btn-rt-primary rt-back-to-top shadow d-none';
        backToTop.title = 'Volver arriba';
        backToTop.setAttribute('aria-label', 'Volver al inicio de la página');
        backToTop.innerHTML = '<i class="bi bi-chevron-up text-dark fw-bold"></i>';
        document.body.appendChild(backToTop);

        backToTop.addEventListener('click', () => {
            window.scrollTo({ top: 0, behavior: 'smooth' });
        });

        window.addEventListener('scroll', () => {
            if (window.scrollY > 280) {
                backToTop.classList.remove('d-none');
            } else {
                backToTop.classList.add('d-none');
            }
        }, { passive: true });
    }
}

const RentaToolsAuth = {
    ProblemDetailError,
    getAccessToken,
    getRefreshToken,
    parseJwtPayload,
    getCurrentUser,
    isAuthenticated,
    hasRole,
    getLandingPageForUser,
    redirectByRole,
    saveSession,
    clearSession,
    loginWithCredentials,
    logout,
    apiFetch,
    fetchAuthenticatedBlobUrl,
    showProblemAlert,
    showSuccessToast,
    escapeHtml,
    formatMoney,
    formatDate,
    formatDateTime,
    renderNavbar
};

if (typeof window !== 'undefined') {
    window.RentaToolsAuth = RentaToolsAuth;
    window.auth = RentaToolsAuth;
}

export default RentaToolsAuth;
