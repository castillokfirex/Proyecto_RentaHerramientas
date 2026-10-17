import {
    apiFetch,
    renderNavbar,
    isAuthenticated,
    escapeHtml,
    formatMoney,
    formatDate,
    formatDateTime,
    showSuccessToast
} from './auth.js';

const state = {
    statusFilter: '',
    reservations: [],
    detailModal: null,
    cancelModal: null,
    highlightId: null
};

const CANCELLABLE_STATUSES = new Set(['PENDING', 'CONFIRMED', 'READY_FOR_PICKUP']);

document.addEventListener('DOMContentLoaded', () => {
    renderNavbar('client-reservations');

    const params = new URLSearchParams(window.location.search);
    state.highlightId = params.get('highlight');

    if (window.bootstrap) {
        const detailEl = document.getElementById('reservationDetailModal');
        const cancelEl = document.getElementById('cancelReservationModal');
        if (detailEl) state.detailModal = new window.bootstrap.Modal(detailEl);
        if (cancelEl) state.cancelModal = new window.bootstrap.Modal(cancelEl);
    }

    bindEvents();
    loadReservations();
});

function bindEvents() {
    const filterGroup = document.getElementById('statusFilterGroup');
    if (filterGroup) {
        filterGroup.querySelectorAll('[data-status]').forEach(btn => {
            btn.addEventListener('click', () => {
                filterGroup.querySelectorAll('[data-status]').forEach(b => {
                    b.classList.remove('btn-dark', 'active');
                    b.classList.add('btn-outline-secondary');
                });
                btn.classList.remove('btn-outline-secondary');
                btn.classList.add('btn-dark', 'active');
                state.statusFilter = btn.getAttribute('data-status') || '';
                loadReservations();
            });
        });
    }

    const refreshBtn = document.getElementById('refreshReservationsBtn');
    if (refreshBtn) {
        refreshBtn.addEventListener('click', () => loadReservations());
    }

    const cancelForm = document.getElementById('cancelReservationForm');
    if (cancelForm) {
        cancelForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            await executeCancelReservation();
        });
    }
}

async function loadReservations() {
    const unauthBanner = document.getElementById('unauthenticatedBanner');
    const loadingEl = document.getElementById('reservationsLoading');
    const listEl = document.getElementById('reservationsList');
    const countLabel = document.getElementById('reservationsCountLabel');

    if (!isAuthenticated()) {
        unauthBanner?.classList.remove('d-none');
        if (listEl) listEl.innerHTML = '';
        if (countLabel) countLabel.textContent = '';
        return;
    }

    unauthBanner?.classList.add('d-none');
    loadingEl?.classList.remove('d-none');
    if (listEl) listEl.innerHTML = '';

    try {
        const params = new URLSearchParams({ page: '0', size: '50' });
        if (state.statusFilter) {
            params.set('status', state.statusFilter);
        }

        const pageResult = await apiFetch(`/api/v1/reservations?${params.toString()}`);
        const items = pageResult?.items || [];
        state.reservations = items;

        if (countLabel) {
            countLabel.textContent = `${items.length} reserva${items.length === 1 ? '' : 's'}`;
        }

        renderReservations(items);
    } catch {
        if (listEl) {
            listEl.innerHTML = `
                <div class="col-12">
                    <div class="alert alert-danger">No se pudieron cargar las reservas. Verifica tu sesión JWT.</div>
                </div>
            `;
        }
    } finally {
        loadingEl?.classList.add('d-none');
    }
}

function renderReservations(items) {
    const listEl = document.getElementById('reservationsList');
    if (!listEl) return;

    if (!items.length) {
        listEl.innerHTML = `
            <div class="col-12">
                <div class="card rt-card p-5 text-center">
                    <i class="bi bi-calendar-x display-5 text-muted mb-2"></i>
                    <h2 class="h5 fw-bold">No tienes reservas en este estado</h2>
                    <p class="text-muted small mb-3">
                        Explora el catálogo público para cotizar herramientas y generar tu primera reserva.
                    </p>
                    <div>
                        <a href="catalog.html" class="btn btn-rt-primary btn-sm">
                            <i class="bi bi-grid-3x3-gap me-1"></i>Ir al Catálogo
                        </a>
                    </div>
                </div>
            </div>
        `;
        return;
    }

    listEl.innerHTML = items.map(res => {
        const canCancel = CANCELLABLE_STATUSES.has(res.status);
        const isConfirmed = res.status === 'CONFIRMED';
        const hasBilling = ['CONFIRMED', 'READY_FOR_PICKUP', 'ACTIVE', 'RETURNED', 'CLOSED'].includes(res.status);
        const isHighlighted = state.highlightId && state.highlightId === res.id;

        const unitsRows = (res.units || []).map(u => `
            <tr>
                <td class="fw-medium">${escapeHtml(u.toolName || 'Herramienta')}</td>
                <td><span class="badge bg-light text-dark border font-monospace">${escapeHtml(u.serialNumber || u.unitId)}</span></td>
                <td class="text-end">${formatMoney(u.dailyRate)}</td>
                <td class="text-end fw-semibold">${formatMoney(u.agreedAmount)}</td>
                <td class="text-end text-primary">${formatMoney(u.depositAmount)}</td>
            </tr>
        `).join('');

        return `
            <div class="col-12">
                <article class="card rt-card p-3 ${isHighlighted ? 'border-warning border-2' : ''}">
                    <div class="d-flex flex-wrap justify-content-between align-items-center border-bottom pb-2 mb-3 gap-2">
                        <div class="d-flex align-items-center gap-2 flex-wrap">
                            <span class="rt-code-badge" title="Código Crockford Base32">
                                <i class="bi bi-upc-scan text-warning me-1"></i>${escapeHtml(res.code)}
                            </span>
                            <span class="badge badge-status-${escapeHtml(res.status)} px-2 py-1">
                                ${escapeHtml(res.status)}
                            </span>
                            <span class="small text-muted">
                                <i class="bi bi-calendar-range me-1"></i>
                                <strong>${formatDate(res.startDate)}</strong> al <strong>${formatDate(res.endDate)}</strong>
                            </span>
                        </div>
                        <div class="small text-muted">
                            Creada: ${formatDateTime(res.createdAt)}
                        </div>
                    </div>

                    ${res.statusReason ? `
                        <div class="alert alert-secondary py-2 px-3 small mb-3">
                            <i class="bi bi-chat-left-text me-1"></i><strong>Motivo / Observación:</strong> ${escapeHtml(res.statusReason)}
                        </div>
                    ` : ''}

                    <div class="table-responsive mb-3">
                        <table class="table table-sm align-middle mb-0 small">
                            <thead class="table-light">
                                <tr>
                                    <th>Herramienta</th>
                                    <th>Unidad / Serie Física</th>
                                    <th class="text-end">Tarifa Día</th>
                                    <th class="text-end">Importe Pactado</th>
                                    <th class="text-end">Garantía</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${unitsRows}
                            </tbody>
                            <tfoot class="table-light fw-bold">
                                <tr>
                                    <td colspan="3" class="text-end">Totales de la Reserva:</td>
                                    <td class="text-end">${formatMoney(res.totalAgreedAmount)}</td>
                                    <td class="text-end text-primary">${formatMoney(res.totalDepositAmount)}</td>
                                </tr>
                            </tfoot>
                        </table>
                    </div>

                    <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 pt-1">
                        <div class="small">
                            <span class="text-muted">Total Alquiler + Garantía:</span>
                            <strong class="fs-6 ms-1 text-dark">
                                ${formatMoney(Number(res.totalAgreedAmount || 0) + Number(res.totalDepositAmount || 0))}
                            </strong>
                        </div>
                        <div class="d-flex flex-wrap gap-2">
                            <button type="button" class="btn btn-sm btn-outline-dark view-detail-btn"
                                    data-res-id="${escapeHtml(res.id)}">
                                <i class="bi bi-eye me-1"></i>Ver Detalle e Inspecciones
                            </button>

                            ${isConfirmed ? `
                                <a href="invoice-view.html?reservationId=${encodeURIComponent(res.id)}"
                                   class="btn btn-sm btn-success fw-semibold">
                                    <i class="bi bi-credit-card me-1"></i>Pagar Factura y Garantía
                                </a>
                            ` : (hasBilling ? `
                                <a href="invoice-view.html?reservationId=${encodeURIComponent(res.id)}"
                                   class="btn btn-sm btn-outline-primary">
                                    <i class="bi bi-receipt me-1"></i>Ver Facturación / Liquidación
                                </a>
                            ` : '')}

                            ${canCancel ? `
                                <button type="button" class="btn btn-sm btn-outline-danger open-cancel-btn"
                                        data-res-id="${escapeHtml(res.id)}"
                                        data-res-code="${escapeHtml(res.code)}">
                                    <i class="bi bi-x-circle me-1"></i>Cancelar Reserva
                                </button>
                            ` : ''}
                        </div>
                    </div>
                </article>
            </div>
        `;
    }).join('');

    listEl.querySelectorAll('.view-detail-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            openReservationDetail(btn.getAttribute('data-res-id'));
        });
    });

    listEl.querySelectorAll('.open-cancel-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            openCancelModal(
                btn.getAttribute('data-res-id'),
                btn.getAttribute('data-res-code')
            );
        });
    });
}

async function openReservationDetail(reservationId) {
    const bodyEl = document.getElementById('reservationDetailModalBody');
    if (!bodyEl) return;

    bodyEl.innerHTML = `
        <div class="text-center py-4">
            <div class="spinner-border text-warning" role="status"></div>
            <p class="small text-muted mt-2 mb-0">Consultando detalle e inspecciones...</p>
        </div>
    `;
    state.detailModal?.show();

    try {
        const [res, inspections] = await Promise.all([
            apiFetch(`/api/v1/reservations/${reservationId}`),
            apiFetch(`/api/v1/reservations/${reservationId}/inspections`, { suppressErrorAlert: true }).catch(() => [])
        ]);

        const unitsHtml = (res.units || []).map(u => `
            <li class="list-group-item d-flex justify-content-between align-items-center small">
                <div>
                    <strong>${escapeHtml(u.toolName)}</strong>
                    <span class="badge bg-secondary font-monospace ms-2">${escapeHtml(u.serialNumber)}</span>
                    <div class="text-muted" style="font-size: 0.78rem;">ID Unidad Reserva: ${escapeHtml(u.id)}</div>
                </div>
                <div class="text-end">
                    <div>Pactado: <strong>${formatMoney(u.agreedAmount)}</strong> (${formatMoney(u.dailyRate)}/día)</div>
                    <div class="text-primary">Garantía: ${formatMoney(u.depositAmount)}</div>
                </div>
            </li>
        `).join('');

        const inspectionsHtml = (inspections && inspections.length)
            ? inspections.map(insp => `
                <div class="card bg-light border-0 p-2 mb-2 small">
                    <div class="d-flex justify-content-between align-items-center">
                        <span>
                            <span class="badge ${insp.type === 'DELIVERY' ? 'bg-primary' : 'bg-dark'}">${escapeHtml(insp.type)}</span>
                            <strong class="ms-1">Condición: ${escapeHtml(insp.overallCondition)}</strong>
                        </span>
                        <span class="text-muted">${formatDateTime(insp.performedAt)}</span>
                    </div>
                    ${insp.observations ? `<div class="mt-1 text-secondary">${escapeHtml(insp.observations)}</div>` : ''}
                </div>
            `).join('')
            : `<p class="text-muted small mb-0">Aún no se han registrado actas de inspección para esta reserva.</p>`;

        bodyEl.innerHTML = `
            <div class="d-flex justify-content-between align-items-center mb-3">
                <div>
                    <span class="rt-code-badge">${escapeHtml(res.code)}</span>
                    <span class="badge badge-status-${escapeHtml(res.status)} ms-2">${escapeHtml(res.status)}</span>
                </div>
                <div class="small text-muted">ID: <span class="font-monospace">${escapeHtml(res.id)}</span></div>
            </div>

            <div class="row g-2 mb-3 small">
                <div class="col-sm-4">
                    <div class="p-2 bg-light rounded">
                        <div class="text-muted">Periodo</div>
                        <strong>${formatDate(res.startDate)} → ${formatDate(res.endDate)}</strong>
                    </div>
                </div>
                <div class="col-sm-4">
                    <div class="p-2 bg-light rounded">
                        <div class="text-muted">Alquiler Pactado</div>
                        <strong>${formatMoney(res.totalAgreedAmount)}</strong>
                    </div>
                </div>
                <div class="col-sm-4">
                    <div class="p-2 bg-light rounded">
                        <div class="text-muted">Garantía Total</div>
                        <strong class="text-primary">${formatMoney(res.totalDepositAmount)}</strong>
                    </div>
                </div>
            </div>

            <h6 class="fw-bold small text-uppercase text-muted mb-2">Unidades Físicas Asignadas</h6>
            <ul class="list-group mb-3">${unitsHtml}</ul>

            <h6 class="fw-bold small text-uppercase text-muted mb-2">Actas de Inspección (Entrega / Devolución)</h6>
            ${inspectionsHtml}
        `;
    } catch {
        bodyEl.innerHTML = `<div class="alert alert-danger mb-0">No se pudo cargar el detalle de la reserva.</div>`;
    }
}

function openCancelModal(reservationId, code) {
    document.getElementById('cancelReservationId').value = reservationId;
    document.getElementById('cancelReservationCode').textContent = code || reservationId;
    document.getElementById('cancelReasonInput').value = '';
    state.cancelModal?.show();
}

async function executeCancelReservation() {
    const reservationId = document.getElementById('cancelReservationId').value;
    const reason = document.getElementById('cancelReasonInput').value.trim();
    const submitBtn = document.getElementById('confirmCancelSubmitBtn');

    if (!reservationId) return;
    if (submitBtn) submitBtn.disabled = true;

    try {
        await apiFetch(`/api/v1/reservations/${reservationId}/cancel`, {
            method: 'POST',
            body: reason ? { reason } : {}
        });
        state.cancelModal?.hide();
        showSuccessToast('Reserva cancelada correctamente.');
        await loadReservations();
    } catch {
        // ProblemDetail ya mostrado en pantalla
    } finally {
        if (submitBtn) submitBtn.disabled = false;
    }
}
