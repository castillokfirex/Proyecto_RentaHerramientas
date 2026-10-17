import {
    apiFetch,
    renderNavbar,
    isAuthenticated,
    hasRole,
    escapeHtml,
    formatMoney,
    formatDate,
    formatDateTime,
    showProblemAlert,
    showSuccessToast
} from './auth.js';

const CONCEPT_LABELS = {
    RENTAL: 'RENTAL · Alquiler',
    LATE_FEE: 'LATE_FEE · Recargo por Mora',
    DAMAGE: 'DAMAGE · Indemnización por Daño'
};

const state = {
    reservationId: null,
    billingData: null,
    activeInvoice: null,
    activeInvoiceTab: 'RENTAL'
};

document.addEventListener('DOMContentLoaded', async () => {
    renderNavbar('invoice-view');

    if (!isAuthenticated()) {
        const returnTo = encodeURIComponent('invoice-view.html' + window.location.search);
        window.location.href = `auth.html?expired=true&returnTo=${returnTo}`;
        return;
    }

    bindEvents();
    await loadQuickReservationsList();

    const params = new URLSearchParams(window.location.search);
    const resId = params.get('reservationId');
    const invId = params.get('invoiceId');

    if (resId) {
        document.getElementById('lookupTypeSelect').value = 'reservation';
        document.getElementById('lookupIdInput').value = resId;
        const quickSel = document.getElementById('quickReservationSelect');
        if (quickSel) quickSel.value = resId;
        await loadBillingByReservationId(resId);
    } else if (invId) {
        document.getElementById('lookupTypeSelect').value = 'invoice';
        document.getElementById('lookupIdInput').value = invId;
        await loadSingleInvoiceById(invId);
    } else {
        // Si no se especificó parámetro, selecciona automáticamente la primera reserva con facturación
        const quickSel = document.getElementById('quickReservationSelect');
        if (quickSel && quickSel.options.length > 1) {
            quickSel.selectedIndex = 1;
            await loadBillingByReservationId(quickSel.value);
        }
    }
});

function bindEvents() {
    document.getElementById('printInvoiceBtn')?.addEventListener('click', () => {
        window.print();
    });

    const quickSel = document.getElementById('quickReservationSelect');
    if (quickSel) {
        quickSel.addEventListener('change', async () => {
            if (quickSel.value) {
                document.getElementById('lookupTypeSelect').value = 'reservation';
                document.getElementById('lookupIdInput').value = quickSel.value;
                await loadBillingByReservationId(quickSel.value);
            }
        });
    }

    const lookupForm = document.getElementById('lookupBillingForm');
    if (lookupForm) {
        lookupForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const type = document.getElementById('lookupTypeSelect').value;
            const id = document.getElementById('lookupIdInput').value.trim();
            if (!id) return;
            if (type === 'reservation') {
                await loadBillingByReservationId(id);
            } else {
                await loadSingleInvoiceById(id);
            }
        });
    }

    document.getElementById('btnShowRentalInvoice')?.addEventListener('click', () => {
        state.activeInvoiceTab = 'RENTAL';
        updateInvoiceTabButtons();
        renderActiveInvoice(state.billingData?.rentalInvoice || null);
    });

    document.getElementById('btnShowSettlementInvoice')?.addEventListener('click', () => {
        state.activeInvoiceTab = 'SETTLEMENT';
        updateInvoiceTabButtons();
        renderActiveInvoice(state.billingData?.settlementInvoice || null);
    });

    const payForm = document.getElementById('payInvoiceForm');
    if (payForm) {
        payForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            await handleRegisterInvoicePayment();
        });
    }

    const depositForm = document.getElementById('collectDepositForm');
    if (depositForm) {
        depositForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            await handleCollectDeposit();
        });
    }

    const damageForm = document.getElementById('addDamageChargeForm');
    if (damageForm) {
        damageForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            await handleAddDamageCharge();
        });
    }

    document.getElementById('settleReservationBtn')?.addEventListener('click', async () => {
        await handleSettleReservation();
    });
}

async function loadQuickReservationsList() {
    const select = document.getElementById('quickReservationSelect');
    if (!select) return;

    try {
        const pageResult = await apiFetch('/api/v1/reservations?page=0&size=50', { suppressErrorAlert: true });
        const items = (pageResult?.items || []).filter(r =>
            ['CONFIRMED', 'READY_FOR_PICKUP', 'ACTIVE', 'RETURNED', 'CLOSED', 'CANCELLED'].includes(r.status)
        );

        select.innerHTML = `<option value="">Selecciona una reserva con facturación...</option>` +
            items.map(r => `
                <option value="${escapeHtml(r.id)}">
                    [${escapeHtml(r.code)}] ${escapeHtml(r.status)} · ${formatDate(r.startDate)} → ${formatDate(r.endDate)} (${formatMoney(r.totalAgreedAmount)})
                </option>
            `).join('');
    } catch {
        // Ignorar si no tiene reservas aún
    }
}

async function loadBillingByReservationId(reservationId) {
    try {
        const billing = await apiFetch(`/api/v1/billing/reservations/${reservationId}`);
        state.reservationId = reservationId;
        state.billingData = billing;

        const switchBar = document.getElementById('invoiceSwitchBar');
        const settlementBtn = document.getElementById('btnShowSettlementInvoice');

        if (billing.settlementInvoice) {
            switchBar?.classList.remove('d-none');
            if (settlementBtn) settlementBtn.disabled = false;
        } else {
            switchBar?.classList.remove('d-none');
            if (settlementBtn) settlementBtn.disabled = true;
            state.activeInvoiceTab = 'RENTAL';
        }

        updateInvoiceTabButtons();
        const invToShow = state.activeInvoiceTab === 'SETTLEMENT' && billing.settlementInvoice
            ? billing.settlementInvoice
            : billing.rentalInvoice;

        renderActiveInvoice(invToShow);
        renderSecurityDepositPanel(billing.securityDeposit);
        renderProviderSettlementPanel(billing);
    } catch {
        // ProblemDetail mostrado por apiFetch
    }
}

async function loadSingleInvoiceById(invoiceId) {
    try {
        const invoice = await apiFetch(`/api/v1/billing/invoices/${invoiceId}`);
        state.reservationId = invoice.reservationId;
        state.billingData = null;
        document.getElementById('invoiceSwitchBar')?.classList.add('d-none');
        document.getElementById('securityDepositCard')?.classList.add('d-none');
        document.getElementById('providerSettlementCard')?.classList.add('d-none');
        renderActiveInvoice(invoice);
    } catch {
        // ProblemDetail mostrado por apiFetch
    }
}

function updateInvoiceTabButtons() {
    const rentalBtn = document.getElementById('btnShowRentalInvoice');
    const settlementBtn = document.getElementById('btnShowSettlementInvoice');
    if (state.activeInvoiceTab === 'SETTLEMENT') {
        rentalBtn?.classList.replace('btn-dark', 'btn-outline-dark');
        rentalBtn?.classList.remove('active');
        settlementBtn?.classList.replace('btn-outline-dark', 'btn-dark');
        settlementBtn?.classList.add('active');
    } else {
        settlementBtn?.classList.replace('btn-dark', 'btn-outline-dark');
        settlementBtn?.classList.remove('active');
        rentalBtn?.classList.replace('btn-outline-dark', 'btn-dark');
        rentalBtn?.classList.add('active');
    }
}

function renderActiveInvoice(inv) {
    state.activeInvoice = inv;
    const emptyEl = document.getElementById('invoiceEmptyState');
    const contentEl = document.getElementById('invoiceContentContainer');
    const submitPayBtn = document.getElementById('submitPayInvoiceBtn');

    if (!inv) {
        emptyEl?.classList.remove('d-none');
        contentEl?.classList.add('d-none');
        if (submitPayBtn) submitPayBtn.disabled = true;
        return;
    }

    emptyEl?.classList.add('d-none');
    contentEl?.classList.remove('d-none');

    document.getElementById('invReservationId').textContent = inv.reservationId || '—';
    document.getElementById('invTypeBadge').textContent = inv.type || 'RENTAL';

    const statusBadge = document.getElementById('invStatusBadge');
    statusBadge.textContent = inv.paid ? `${inv.status} · PAGADA` : inv.status;
    statusBadge.className = `badge ${inv.paid ? 'bg-success' : (inv.status === 'VOIDED' ? 'bg-danger' : 'bg-primary')}`;

    document.getElementById('invSequenceNumber').textContent = inv.number != null
        ? `N° ${String(inv.number).padStart(8, '0')}`
        : 'BORRADOR (Sin número)';
    document.getElementById('invIssuedAt').textContent = inv.issuedAt
        ? `Emitida: ${formatDateTime(inv.issuedAt)}`
        : `Creada: ${formatDateTime(inv.createdAt)}`;
    document.getElementById('invUuidLabel').textContent = `Factura ID: ${inv.id}`;

    // Líneas de factura (RENTAL, LATE_FEE, DAMAGE)
    const linesBody = document.getElementById('invoiceLinesTableBody');
    const lines = inv.lines || [];
    if (!lines.length) {
        linesBody.innerHTML = `
            <tr>
                <td colspan="6" class="text-center text-muted py-3">Sin conceptos registrados en esta factura.</td>
            </tr>
        `;
    } else {
        linesBody.innerHTML = lines.map(line => `
            <tr>
                <td>
                    <span class="badge badge-concept-${escapeHtml(line.concept)}">
                        ${escapeHtml(CONCEPT_LABELS[line.concept] || line.concept)}
                    </span>
                </td>
                <td class="fw-medium">${escapeHtml(line.description)}</td>
                <td class="font-monospace text-muted" style="font-size: 0.75rem;">${escapeHtml(line.reservationUnitId)}</td>
                <td class="text-center">${Number(line.quantity || 1)}</td>
                <td class="text-end">${formatMoney(line.unitPrice)}</td>
                <td class="text-end fw-bold">${formatMoney(line.lineTotal)}</td>
            </tr>
        `).join('');
    }

    // Totales e IVA/IGV
    const taxPct = Math.round(Number(inv.taxRate || 0.18) * 100);
    document.getElementById('invTaxLabel').textContent = `IVA / IGV (${taxPct}%):`;
    document.getElementById('invSubtotal').textContent = formatMoney(inv.subtotal);
    document.getElementById('invTax').textContent = formatMoney(inv.tax);
    document.getElementById('invTotal').textContent = formatMoney(inv.total);
    document.getElementById('invNetPaid').textContent = formatMoney(inv.netPaid);
    document.getElementById('invRetainedDeposit').textContent = formatMoney(inv.retainedDeposit);

    const balanceNum = Number(inv.balance || 0);
    const balanceEl = document.getElementById('invBalance');
    balanceEl.textContent = formatMoney(balanceNum);
    balanceEl.className = balanceNum > 0 ? 'text-danger fw-bold' : 'text-success fw-bold';

    // Historial de pagos
    const paymentsBody = document.getElementById('invoicePaymentsTableBody');
    const payments = inv.payments || [];
    if (!payments.length) {
        paymentsBody.innerHTML = `
            <tr>
                <td colspan="5" class="text-center text-muted py-2">No se registran pagos o reembolsos aún.</td>
            </tr>
        `;
    } else {
        paymentsBody.innerHTML = payments.map(p => `
            <tr>
                <td>${formatDateTime(p.paidAt)}</td>
                <td>
                    <span class="badge ${p.kind === 'REFUND' ? 'bg-warning text-dark' : 'bg-success'}">
                        ${escapeHtml(p.kind)}
                    </span>
                </td>
                <td><span class="badge bg-dark font-monospace">${escapeHtml(p.method)}</span></td>
                <td class="font-monospace">${escapeHtml(p.reference || '—')}</td>
                <td class="text-end fw-bold ${p.kind === 'REFUND' ? 'text-danger' : 'text-success'}">
                    ${p.kind === 'REFUND' ? '-' : ''}${formatMoney(p.amount)}
                </td>
            </tr>
        `).join('');
    }

    // Formulario de pago simulado
    const payAmountInput = document.getElementById('payAmountInput');
    if (payAmountInput) {
        payAmountInput.value = balanceNum > 0 ? balanceNum.toFixed(2) : '';
    }
    if (submitPayBtn) {
        submitPayBtn.disabled = !(inv.status === 'ISSUED' && balanceNum > 0);
    }
}

function renderSecurityDepositPanel(dep) {
    const card = document.getElementById('securityDepositCard');
    const badge = document.getElementById('depositStatusBadge');
    const body = document.getElementById('depositSummaryBody');
    const amountInput = document.getElementById('depositAmountInput');
    const btn = document.getElementById('collectDepositBtn');

    if (!dep) {
        card?.classList.add('d-none');
        return;
    }

    card?.classList.remove('d-none');
    const pendingToCollect = Math.max(0, Number(dep.requiredAmount || 0) - Number(dep.totalCollected || 0));

    if (dep.settled) {
        badge.className = 'badge bg-dark';
        badge.textContent = 'LIQUIDADA';
    } else if (dep.fullyCollected) {
        badge.className = 'badge bg-success';
        badge.textContent = 'CUBIERTA 100%';
    } else {
        badge.className = 'badge bg-warning text-dark';
        badge.textContent = 'PENDIENTE COBRO';
    }

    body.innerHTML = `
        <div class="d-flex justify-content-between py-1 border-bottom">
            <span>Garantía Requerida:</span>
            <strong>${formatMoney(dep.requiredAmount)}</strong>
        </div>
        <div class="d-flex justify-content-between py-1 border-bottom">
            <span>Total Recaudado:</span>
            <strong class="text-success">${formatMoney(dep.totalCollected)}</strong>
        </div>
        <div class="d-flex justify-content-between py-1 border-bottom">
            <span>Retenido por Mora/Daños:</span>
            <strong class="text-danger">${formatMoney(dep.totalRetained)}</strong>
        </div>
        <div class="d-flex justify-content-between py-1 border-bottom">
            <span>Reembolsado al Cliente:</span>
            <strong class="text-primary">${formatMoney(dep.totalRefunded)}</strong>
        </div>
        <div class="d-flex justify-content-between pt-2">
            <span>Saldo en Custodia:</span>
            <strong>${formatMoney(dep.balance)}</strong>
        </div>
    `;

    if (amountInput) {
        amountInput.value = pendingToCollect > 0 ? pendingToCollect.toFixed(2) : '';
    }
    if (btn) {
        btn.disabled = dep.settled || pendingToCollect <= 0;
    }
}

function renderProviderSettlementPanel(billing) {
    const card = document.getElementById('providerSettlementCard');
    const canSettle = hasRole('PROVEEDOR') || hasRole('ADMIN');
    if (!canSettle || !billing?.settlementInvoice) {
        card?.classList.add('d-none');
        return;
    }

    card?.classList.remove('d-none');
    const firstUnitId = billing.rentalInvoice?.lines?.[0]?.reservationUnitId || '';
    const unitInput = document.getElementById('damageUnitIdInput');
    if (unitInput && !unitInput.value && firstUnitId) {
        unitInput.value = firstUnitId;
    }
}

async function handleRegisterInvoicePayment() {
    const inv = state.activeInvoice;
    if (!inv) return;

    const amount = Number(document.getElementById('payAmountInput').value || 0);
    const method = document.getElementById('payMethodSelect').value || 'SIMULATED';
    const reference = document.getElementById('payReferenceInput').value.trim() || 'SIM-PAY';

    if (amount <= 0) {
        showProblemAlert('Ingresa un monto mayor a cero para procesar el pago.', 'warning');
        return;
    }

    try {
        const updatedInvoice = await apiFetch(`/api/v1/billing/invoices/${inv.id}/payments`, {
            method: 'POST',
            body: { amount, method, reference }
        });

        showSuccessToast(`Pago simulado (${method}) por ${formatMoney(amount)} procesado correctamente.`);
        if (state.reservationId) {
            await loadBillingByReservationId(state.reservationId);
        } else {
            renderActiveInvoice(updatedInvoice);
        }
    } catch {
        // ProblemDetail mostrado
    }
}

async function handleCollectDeposit() {
    if (!state.reservationId) return;
    const amount = Number(document.getElementById('depositAmountInput').value || 0);
    if (amount <= 0) return;

    try {
        await apiFetch(`/api/v1/billing/reservations/${state.reservationId}/deposit/collections`, {
            method: 'POST',
            body: {
                amount,
                method: 'SIMULATED',
                reference: 'SIM-DEPOSIT-ONLINE'
            }
        });

        showSuccessToast(`Garantía cobrada (${formatMoney(amount)}) exitosamente.`);
        await loadBillingByReservationId(state.reservationId);
    } catch {
        // ProblemDetail mostrado
    }
}

async function handleAddDamageCharge() {
    const settlementInv = state.billingData?.settlementInvoice;
    if (!settlementInv) return;

    const reservationUnitId = document.getElementById('damageUnitIdInput').value.trim();
    const description = document.getElementById('damageDescriptionInput').value.trim();
    const amount = Number(document.getElementById('damageAmountInput').value || 0);

    try {
        await apiFetch(`/api/v1/billing/invoices/${settlementInv.id}/damage-charges`, {
            method: 'POST',
            body: { reservationUnitId, description, amount }
        });
        document.getElementById('damageDescriptionInput').value = '';
        document.getElementById('damageAmountInput').value = '';
        showSuccessToast('Cargo por daño agregado al borrador de liquidación (SETTLEMENT).');
        state.activeInvoiceTab = 'SETTLEMENT';
        await loadBillingByReservationId(state.reservationId);
    } catch {
        // ProblemDetail mostrado
    }
}

async function handleSettleReservation() {
    if (!state.reservationId) return;
    try {
        await apiFetch(`/api/v1/billing/reservations/${state.reservationId}/settle`, {
            method: 'POST',
            body: {
                refundMethod: 'SIMULATED',
                refundReference: 'SIM-SETTLE-REFUND'
            }
        });
        showSuccessToast('Liquidación final completada. Se aplicó retención y devolución de garantía.');
        state.activeInvoiceTab = 'SETTLEMENT';
        await loadBillingByReservationId(state.reservationId);
    } catch {
        // ProblemDetail mostrado
    }
}
