/**
 * RentaTools — Helper compartido de Autenticación JWT, Consumo de API (fetch)
 * y manejo de errores RFC 9457 (ProblemDetail).
 * Expuesto como módulo ES y en window.RentaToolsAuth para compatibilidad global.
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
        skipAuthHeader: true
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
    window.location.href = 'catalog.html';
}

/**
 * Wrapper sobre fetch() que adjunta Authorization: Bearer <JWT>
 * y traduce errores RFC 9457 ProblemDetail a ProblemDetailError mostrando alerta visual.
 */
export async function apiFetch(url, options = {}) {
    const {
        skipAuthHeader = false,
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
 * Renderiza la barra de navegación superior responsiva según el rol autenticado
 * e incluye un modal rápido de inicio de sesión / configuración de token JWT.
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

    navHost.innerHTML = `
        <nav class="navbar navbar-expand-lg navbar-dark rt-navbar sticky-top">
            <div class="container">
                <a class="navbar-brand d-flex align-items-center gap-2 fw-bold" href="catalog.html">
                    <span class="rt-brand-badge"><i class="bi bi-tools"></i> RT</span>
                    <span>RentaTools</span>
                </a>
                <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#rtNavbarContent"
                        aria-controls="rtNavbarContent" aria-expanded="false" aria-label="Alternar navegación">
                    <span class="navbar-toggler-icon"></span>
                </button>
                <div class="collapse navbar-collapse" id="rtNavbarContent">
                    <ul class="navbar-nav me-auto mb-2 mb-lg-0">
                        <li class="nav-item">
                            <a class="nav-link ${activePage === 'catalog' ? 'active fw-semibold text-warning' : ''}" href="catalog.html">
                                <i class="bi bi-grid-3x3-gap me-1"></i>Catálogo
                            </a>
                        </li>
                        ${(!authenticated || isClient || isAdmin) ? `
                        <li class="nav-item">
                            <a class="nav-link ${activePage === 'client-reservations' ? 'active fw-semibold text-warning' : ''}" href="client-reservations.html">
                                <i class="bi bi-calendar2-check me-1"></i>Mis Reservas
                            </a>
                        </li>
                        ` : ''}
                        ${(!authenticated || isProvider || isAdmin) ? `
                        <li class="nav-item">
                            <a class="nav-link ${activePage === 'provider-dashboard' ? 'active fw-semibold text-warning' : ''}" href="provider-dashboard.html">
                                <i class="bi bi-briefcase me-1"></i>Panel Proveedor
                            </a>
                        </li>
                        ` : ''}
                        ${authenticated ? `
                        <li class="nav-item">
                            <a class="nav-link ${activePage === 'invoice-view' ? 'active fw-semibold text-warning' : ''}" href="invoice-view.html">
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
                            <button type="button" class="btn btn-sm btn-outline-warning" data-bs-toggle="modal" data-bs-target="#rtQuickAuthModal">
                                <i class="bi bi-key me-1"></i>Cambiar Sesión
                            </button>
                            <button type="button" class="btn btn-sm btn-danger" id="rtLogoutBtn">
                                <i class="bi bi-box-arrow-right me-1"></i>Cerrar Sesión
                            </button>
                        ` : `
                            <a href="login.html" class="btn btn-sm btn-outline-light d-none d-sm-inline-block">
                                <i class="bi bi-box-arrow-in-right me-1"></i>Ir a Login
                            </a>
                            <button type="button" class="btn btn-sm btn-rt-primary" data-bs-toggle="modal" data-bs-target="#rtQuickAuthModal">
                                <i class="bi bi-person-lock me-1"></i>Iniciar Sesión / JWT
                            </button>
                        `}
                    </div>
                </div>
            </div>
        </nav>

        <!-- Modal de Inicio de Sesión / Token JWT Rápido -->
        <div class="modal fade" id="rtQuickAuthModal" tabindex="-1" aria-labelledby="rtQuickAuthModalLabel" aria-hidden="true">
            <div class="modal-dialog modal-dialog-centered">
                <div class="modal-content">
                    <div class="modal-header bg-dark text-white">
                        <h5 class="modal-title" id="rtQuickAuthModalLabel">
                            <i class="bi bi-shield-lock text-warning me-2"></i>Autenticación RentaTools
                        </h5>
                        <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Cerrar"></button>
                    </div>
                    <div class="modal-body">
                        <ul class="nav nav-pills nav-fill mb-3" id="rtAuthTabs" role="tablist">
                            <li class="nav-item" role="presentation">
                                <button class="nav-link active" id="tab-credentials-btn" data-bs-toggle="pill"
                                        data-bs-target="#tab-credentials" type="button" role="tab">
                                    Email y Contraseña
                                </button>
                            </li>
                            <li class="nav-item" role="presentation">
                                <button class="nav-link" id="tab-jwt-btn" data-bs-toggle="pill"
                                        data-bs-target="#tab-jwt" type="button" role="tab">
                                    Pegar Token JWT
                                </button>
                            </li>
                        </ul>

                        <div class="tab-content">
                            <div class="tab-pane fade show active" id="tab-credentials" role="tabpanel">
                                <form id="rtQuickLoginForm">
                                    <div class="mb-3">
                                        <label for="rtAuthEmail" class="form-label small fw-semibold">Correo electrónico</label>
                                        <input type="email" class="form-control" id="rtAuthEmail" required placeholder="usuario@rentatools.pe">
                                    </div>
                                    <div class="mb-3">
                                        <label for="rtAuthPassword" class="form-label small fw-semibold">Contraseña</label>
                                        <input type="password" class="form-control" id="rtAuthPassword" required placeholder="••••••••">
                                    </div>
                                    <div class="d-grid">
                                        <button type="submit" class="btn btn-rt-primary">
                                            <i class="bi bi-box-arrow-in-right me-1"></i>Iniciar Sesión
                                        </button>
                                    </div>
                                </form>
                            </div>

                            <div class="tab-pane fade" id="tab-jwt" role="tabpanel">
                                <form id="rtQuickJwtForm">
                                    <div class="mb-3">
                                        <label for="rtJwtInput" class="form-label small fw-semibold">Access Token (Bearer JWT)</label>
                                        <textarea class="form-control font-monospace small" id="rtJwtInput" rows="4"
                                                  placeholder="eyJhbGciOiJIUzI1NiJ9...">${escapeHtml(getAccessToken() || '')}</textarea>
                                        <div class="form-text">
                                            Los roles y el ID de usuario se extraen automáticamente del payload del JWT.
                                        </div>
                                    </div>
                                    <div class="d-grid">
                                        <button type="submit" class="btn btn-dark">
                                            <i class="bi bi-check2-circle me-1"></i>Guardar Token en localStorage
                                        </button>
                                    </div>
                                </form>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    `;

    const logoutBtn = document.getElementById('rtLogoutBtn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', () => logout());
    }

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

    const jwtForm = document.getElementById('rtQuickJwtForm');
    if (jwtForm) {
        jwtForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const rawToken = document.getElementById('rtJwtInput').value.trim().replace(/^Bearer\s+/i, '');
            if (!rawToken) {
                clearSession();
                window.location.reload();
                return;
            }
            saveSession({ accessToken: rawToken });
            try {
                const me = await apiFetch('/api/v1/users/me', { suppressErrorAlert: true });
                if (me) saveSession({ accessToken: rawToken, user: me });
            } catch {
                // Si falla /me, se conserva la información extraída de los claims del JWT
            }
            window.location.reload();
        });
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
