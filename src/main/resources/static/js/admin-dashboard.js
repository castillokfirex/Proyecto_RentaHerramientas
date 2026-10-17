import {
    apiFetch,
    renderNavbar,
    isAuthenticated,
    hasRole,
    getCurrentUser,
    escapeHtml,
    formatMoney,
    formatDateTime,
    showSuccessToast
} from './auth.js';

const PAYMENT_METHOD_LABELS = {
    CASH: 'Efectivo en sede (CASH)',
    CARD: 'Tarjeta de crédito / débito (CARD)',
    TRANSFER: 'Transferencia bancaria (TRANSFER)',
    SIMULATED: 'Pasarela simulada en línea (SIMULATED)'
};

const state = {
    allUsers: [],
    filteredUsers: [],
    roleFilter: '',
    statusFilter: '',
    searchText: '',
    revFrom: '',
    revTo: ''
};

document.addEventListener('DOMContentLoaded', async () => {
    renderNavbar('admin-dashboard');

    if (!isAuthenticated()) {
        window.location.href = 'auth.html?expired=true&returnTo=admin-dashboard.html';
        return;
    }

    const deniedCard = document.getElementById('adminAccessDeniedCard');
    const protectedContent = document.getElementById('adminProtectedContent');

    if (!hasRole('ADMIN')) {
        deniedCard?.classList.remove('d-none');
        protectedContent?.classList.add('d-none');
        return;
    }

    deniedCard?.classList.add('d-none');
    protectedContent?.classList.remove('d-none');

    bindEvents();
    await loadAllAdminData();
});

function bindEvents() {
    document.getElementById('refreshAdminDashboardBtn')?.addEventListener('click', () => loadAllAdminData());

    const revForm = document.getElementById('revenueDateFilterForm');
    if (revForm) {
        revForm.addEventListener('submit', (e) => {
            e.preventDefault();
            state.revFrom = document.getElementById('revFromDate').value;
            state.revTo = document.getElementById('revToDate').value;
            loadRevenueMetrics();
        });
    }

    document.getElementById('clearRevFilterBtn')?.addEventListener('click', () => {
        document.getElementById('revFromDate').value = '';
        document.getElementById('revToDate').value = '';
        state.revFrom = '';
        state.revTo = '';
        loadRevenueMetrics();
    });

    document.getElementById('userRoleFilter')?.addEventListener('change', (e) => {
        state.roleFilter = e.target.value;
        loadUsersDirectory();
    });

    document.getElementById('userStatusFilter')?.addEventListener('change', (e) => {
        state.statusFilter = e.target.value;
        loadUsersDirectory();
    });

    document.getElementById('userSearchInput')?.addEventListener('input', (e) => {
        state.searchText = e.target.value.trim().toLowerCase();
        renderUsersTable();
    });
}

async function loadAllAdminData() {
    await Promise.all([
        loadRevenueMetrics(),
        loadUsersDirectory()
    ]);
}

/* =========================================================
 * 1. MÉTRICAS FINANCIERAS (GET /api/v1/admin/reports/revenue)
 * ========================================================= */

async function loadRevenueMetrics() {
    try {
        const params = new URLSearchParams();
        if (state.revFrom) params.set('from', state.revFrom);
        if (state.revTo) params.set('to', state.revTo);

        const queryStr = params.toString() ? `?${params.toString()}` : '';
        const report = await apiFetch(`/api/v1/admin/reports/revenue${queryStr}`);

        document.getElementById('kpiTotalInvoiced').textContent = formatMoney(report?.totalInvoiced || 0);
        document.getElementById('kpiTotalCollected').textContent = formatMoney(report?.totalCollected || 0);
        document.getElementById('kpiDepositsRetained').textContent = formatMoney(report?.depositsRetained || 0);
        document.getElementById('kpiDepositsRefunded').textContent = formatMoney(report?.depositsRefunded || 0);

        renderPaymentMethodBreakdown(report?.breakdownByPaymentMethod || {}, Number(report?.totalCollected || 0));
    } catch {
        // ProblemDetail mostrado por apiFetch
    }
}

function renderPaymentMethodBreakdown(breakdownMap, totalCollected) {
    const tbody = document.getElementById('paymentMethodBreakdownBody');
    if (!tbody) return;

    const methods = ['SIMULATED', 'CARD', 'TRANSFER', 'CASH'];
    const allKeys = Array.from(new Set([...methods, ...Object.keys(breakdownMap)]));

    const positiveSum = allKeys.reduce((acc, k) => acc + Math.max(0, Number(breakdownMap[k] || 0)), 0) || Math.max(1, totalCollected);

    tbody.innerHTML = allKeys.map(method => {
        const amount = Number(breakdownMap[method] || 0);
        const pct = positiveSum > 0 ? Math.min(100, Math.max(0, Math.round((Math.max(0, amount) / positiveSum) * 100))) : 0;

        return `
            <tr>
                <td><span class="badge bg-dark font-monospace">${escapeHtml(method)}</span></td>
                <td>${escapeHtml(PAYMENT_METHOD_LABELS[method] || method)}</td>
                <td class="text-end fw-bold">${formatMoney(amount)}</td>
                <td class="text-end">
                    <div class="d-flex align-items-center justify-content-end gap-2">
                        <div class="progress flex-grow-1" style="height: 8px; max-width: 110px;">
                            <div class="progress-bar bg-warning" role="progressbar" style="width: ${pct}%"></div>
                        </div>
                        <span class="text-muted" style="min-width: 36px;">${pct}%</span>
                    </div>
                </td>
            </tr>
        `;
    }).join('');
}

/* =========================================================
 * 2. USUARIOS Y APROBACIÓN DE PROVEEDORES
 * ========================================================= */

async function loadUsersDirectory() {
    try {
        const params = new URLSearchParams({ page: '0', size: '100' });
        if (state.roleFilter) params.set('role', state.roleFilter);
        if (state.statusFilter) params.set('status', state.statusFilter);

        const pageResult = await apiFetch(`/api/v1/admin/users?${params.toString()}`);
        state.allUsers = pageResult?.items || [];

        renderPendingProvidersTray();
        renderUsersTable();
    } catch {
        // ProblemDetail mostrado por apiFetch
    }
}

function renderPendingProvidersTray() {
    const tbody = document.getElementById('pendingProvidersTableBody');
    const countBadge = document.getElementById('pendingProvidersCountBadge');
    if (!tbody) return;

    const pendingProviders = state.allUsers.filter(u =>
        (u.roles || []).includes('PROVEEDOR') && u.providerProfile && !u.providerProfile.approved
    );

    if (countBadge) {
        countBadge.textContent = String(pendingProviders.length);
    }

    if (!pendingProviders.length) {
        tbody.innerHTML = `
            <tr>
                <td colspan="6" class="text-center text-muted py-3">
                    <i class="bi bi-check2-all text-success me-1"></i>
                    No hay solicitudes de proveedores pendientes de aprobación en este momento.
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = pendingProviders.map(u => {
        const p = u.providerProfile || {};
        return `
            <tr>
                <td class="fw-bold">${escapeHtml(p.businessName || '—')}</td>
                <td><span class="badge bg-light text-dark border font-monospace">${escapeHtml(p.taxId || '—')}</span></td>
                <td>${escapeHtml(u.fullName)}</td>
                <td>
                    <div>${escapeHtml(u.email)}</div>
                    <div class="text-muted" style="font-size: 0.75rem;">${escapeHtml(u.phone || '')}</div>
                </td>
                <td><span class="badge bg-warning text-dark">PENDIENTE APROBACIÓN</span></td>
                <td class="text-end">
                    <button type="button" class="btn btn-sm btn-success approve-provider-btn"
                            data-user-id="${escapeHtml(u.id)}">
                        <i class="bi bi-check-circle me-1"></i>Aprobar Proveedor
                    </button>
                </td>
            </tr>
        `;
    }).join('');

    tbody.querySelectorAll('.approve-provider-btn').forEach(btn => {
        btn.addEventListener('click', async () => {
            const userId = btn.getAttribute('data-user-id');
            btn.disabled = true;
            try {
                await apiFetch(`/api/v1/admin/providers/${userId}/approve`, { method: 'POST' });
                showSuccessToast('Proveedor aprobado exitosamente.');
                await loadUsersDirectory();
            } catch {
                btn.disabled = false;
            }
        });
    });
}

function renderUsersTable() {
    const tbody = document.getElementById('usersTableBody');
    const totalBadge = document.getElementById('totalUsersBadge');
    if (!tbody) return;

    const currentAdminId = getCurrentUser()?.id;

    const filtered = state.allUsers.filter(u => {
        if (!state.searchText) return true;
        const haystack = `${u.fullName || ''} ${u.email || ''} ${u.providerProfile?.businessName || ''} ${u.providerProfile?.taxId || ''}`.toLowerCase();
        return haystack.includes(state.searchText);
    });

    if (totalBadge) {
        totalBadge.textContent = String(filtered.length);
    }

    if (!filtered.length) {
        tbody.innerHTML = `
            <tr>
                <td colspan="7" class="text-center text-muted py-4">
                    No se encontraron usuarios para los criterios seleccionados.
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = filtered.map(u => {
        const isSuspended = u.status === 'SUSPENDED';
        const isSelf = currentAdminId && u.id === currentAdminId;
        const targetStatus = isSuspended ? 'ACTIVE' : 'SUSPENDED';

        const roleBadges = (u.roles || []).map(r => {
            const cls = r === 'ADMIN' ? 'bg-danger' : (r === 'PROVEEDOR' ? 'bg-primary' : 'bg-secondary');
            return `<span class="badge ${cls} me-1">${escapeHtml(r)}</span>`;
        }).join('');

        let providerInfo = '<span class="text-muted">—</span>';
        if (u.providerProfile) {
            const appr = u.providerProfile.approved;
            providerInfo = `
                <div>
                    <strong>${escapeHtml(u.providerProfile.businessName)}</strong>
                    <span class="font-monospace text-muted">(${escapeHtml(u.providerProfile.taxId)})</span>
                </div>
                <span class="badge ${appr ? 'bg-success-subtle text-success border' : 'bg-warning text-dark'}">
                    ${appr ? 'Homologado' : 'Pendiente'}
                </span>
                ${!appr ? `
                    <button type="button" class="btn btn-link btn-sm p-0 ms-1 text-success fw-semibold inline-approve-btn"
                            data-user-id="${escapeHtml(u.id)}">
                        Aprobar
                    </button>
                ` : ''}
            `;
        }

        return `
            <tr>
                <td class="fw-medium">${escapeHtml(u.fullName)}</td>
                <td>
                    <div>${escapeHtml(u.email)}</div>
                    <div class="text-muted font-monospace" style="font-size: 0.72rem;">${escapeHtml(u.id)}</div>
                </td>
                <td>${roleBadges}</td>
                <td>${providerInfo}</td>
                <td>
                    <span class="badge ${isSuspended ? 'bg-danger' : 'bg-success'}">
                        ${escapeHtml(u.status)}
                    </span>
                </td>
                <td class="text-muted">${formatDateTime(u.createdAt)}</td>
                <td class="text-end">
                    <button type="button"
                            class="btn btn-sm ${isSuspended ? 'btn-outline-success' : 'btn-outline-danger'} toggle-status-btn"
                            data-user-id="${escapeHtml(u.id)}"
                            data-target-status="${targetStatus}"
                            ${isSelf ? 'disabled title="No puedes suspender tu propia cuenta"' : ''}>
                        <i class="bi ${isSuspended ? 'bi-person-check' : 'bi-person-slash'} me-1"></i>
                        ${isSuspended ? 'Activar (ACTIVE)' : 'Suspender (SUSPENDED)'}
                    </button>
                </td>
            </tr>
        `;
    }).join('');

    tbody.querySelectorAll('.toggle-status-btn').forEach(btn => {
        btn.addEventListener('click', async () => {
            const userId = btn.getAttribute('data-user-id');
            const targetStatus = btn.getAttribute('data-target-status');
            btn.disabled = true;
            try {
                await apiFetch(`/api/v1/admin/users/${userId}/status`, {
                    method: 'PATCH',
                    body: { status: targetStatus }
                });
                showSuccessToast(`Estado del usuario actualizado a ${targetStatus}.`);
                await loadUsersDirectory();
            } catch {
                btn.disabled = false;
            }
        });
    });

    tbody.querySelectorAll('.inline-approve-btn').forEach(btn => {
        btn.addEventListener('click', async () => {
            const userId = btn.getAttribute('data-user-id');
            btn.disabled = true;
            try {
                await apiFetch(`/api/v1/admin/providers/${userId}/approve`, { method: 'POST' });
                showSuccessToast('Proveedor aprobado exitosamente.');
                await loadUsersDirectory();
            } catch {
                btn.disabled = false;
            }
        });
    });
}
