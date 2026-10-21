import {
    apiFetch,
    fetchAuthenticatedBlobUrl,
    renderNavbar,
    isAuthenticated,
    escapeHtml,
    formatMoney,
    formatDate,
    formatDateTime,
    showProblemAlert,
    showSuccessToast
} from './auth.js';

const DEFAULT_CHECKLIST_ITEMS = [
    { id: 1, code: 'CARCASA', label: 'Carcasa / estructura sin golpes ni fisuras', active: true },
    { id: 2, code: 'CABLE_ENCHUFE', label: 'Cable, enchufe o batería en buen estado', active: true },
    { id: 3, code: 'FUNCIONAMIENTO', label: 'Enciende y opera correctamente', active: true },
    { id: 4, code: 'ACCESORIOS', label: 'Accesorios completos', active: true },
    { id: 5, code: 'SEGURIDAD', label: 'Protecciones y dispositivos de seguridad', active: true },
    { id: 6, code: 'LIMPIEZA', label: 'Limpieza general', active: true }
];

const KNOWN_UNITS_STORAGE_KEY = 'rentatools_provider_known_units';

const state = {
    categories: [],
    tools: [],
    reservations: [],
    reservationFilter: '',
    checklistItems: DEFAULT_CHECKLIST_ITEMS,
    knownUnits: [],
    toolEditorModal: null,
    rejectModal: null
};

document.addEventListener('DOMContentLoaded', async () => {
    renderNavbar('provider-dashboard');

    if (window.bootstrap) {
        const toolEditorEl = document.getElementById('toolEditorModal');
        const rejectEl = document.getElementById('rejectReservationModal');
        if (toolEditorEl) state.toolEditorModal = new window.bootstrap.Modal(toolEditorEl);
        if (rejectEl) state.rejectModal = new window.bootstrap.Modal(rejectEl);
    }

    loadKnownUnitsFromStorage();
    bindEvents();
    await syncAllData();
});

function loadKnownUnitsFromStorage() {
    try {
        const raw = localStorage.getItem(KNOWN_UNITS_STORAGE_KEY);
        if (raw) {
            const parsed = JSON.parse(raw);
            if (Array.isArray(parsed)) {
                state.knownUnits = parsed;
            }
        }
    } catch {
        state.knownUnits = [];
    }
}

function saveKnownUnit(unitObj) {
    if (!unitObj || !unitObj.id) return;
    const idx = state.knownUnits.findIndex(u => u.id === unitObj.id);
    if (idx >= 0) {
        state.knownUnits[idx] = { ...state.knownUnits[idx], ...unitObj };
    } else {
        state.knownUnits.unshift(unitObj);
    }
    localStorage.setItem(KNOWN_UNITS_STORAGE_KEY, JSON.stringify(state.knownUnits.slice(0, 100)));
}

function bindEvents() {
    document.getElementById('refreshDashboardBtn')?.addEventListener('click', () => syncAllData());
    document.getElementById('openCreateToolBtn')?.addEventListener('click', () => openToolModal(null));

    const toolForm = document.getElementById('toolEditorForm');
    if (toolForm) {
        toolForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            await saveToolAndRates();
        });
    }

    const uploadImgForm = document.getElementById('uploadToolImageForm');
    if (uploadImgForm) {
        uploadImgForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            await handleUploadToolImage();
        });
    }

    const regUnitForm = document.getElementById('registerUnitForm');
    if (regUnitForm) {
        regUnitForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            await handleRegisterToolUnit();
        });
    }

    const changeStatusForm = document.getElementById('changeUnitStatusForm');
    if (changeStatusForm) {
        changeStatusForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            await handleChangeUnitStatus();
        });
    }

    const resFilterGroup = document.getElementById('providerResFilterGroup');
    if (resFilterGroup) {
        resFilterGroup.querySelectorAll('[data-status]').forEach(btn => {
            btn.addEventListener('click', () => {
                resFilterGroup.querySelectorAll('[data-status]').forEach(b => {
                    b.classList.remove('btn-dark', 'active');
                    b.classList.add('btn-outline-secondary');
                });
                btn.classList.remove('btn-outline-secondary');
                btn.classList.add('btn-dark', 'active');
                state.reservationFilter = btn.getAttribute('data-status') || '';
                loadProviderReservations();
            });
        });
    }

    const rejectForm = document.getElementById('rejectReservationForm');
    if (rejectForm) {
        rejectForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            await handleRejectReservation();
        });
    }

    // Eventos del Módulo de Inspecciones
    const inspResSelect = document.getElementById('inspReservationSelect');
    const inspUnitSelect = document.getElementById('inspUnitSelect');
    const inspTypeSelect = document.getElementById('inspTypeSelect');
    const simDateInput = document.getElementById('simulatedReturnDate');
    const unitAcquiredDate = document.getElementById('unitAcquiredDate');
    const today = new Date();
    const todayIso = today.toISOString().substring(0, 10);

    if (simDateInput) {
        const minPast = new Date(today);
        minPast.setDate(today.getDate() - 30);
        const maxFuture = new Date(today);
        maxFuture.setDate(today.getDate() + 90);

        simDateInput.min = minPast.toISOString().substring(0, 10);
        simDateInput.max = maxFuture.toISOString().substring(0, 10);
        simDateInput.value = todayIso;
        simDateInput.addEventListener('change', () => updateVisualLateFee());
    }

    if (unitAcquiredDate) {
        unitAcquiredDate.max = todayIso;
        unitAcquiredDate.min = '2010-01-01';
        unitAcquiredDate.value = todayIso;
    }

    if (inspResSelect) {
        inspResSelect.addEventListener('change', () => {
            populateInspectionUnitsSelect();
            updateVisualLateFee();
            loadReservationInspectionsHistory(inspResSelect.value);
        });
    }

    if (inspUnitSelect) {
        inspUnitSelect.addEventListener('change', () => updateVisualLateFee());
    }

    if (inspTypeSelect) {
        inspTypeSelect.addEventListener('change', () => {
            const isReturn = inspTypeSelect.value === 'RETURN';
            const lostOption = document.getElementById('inspConditionLostOption');
            const conditionSelect = document.getElementById('inspConditionSelect');
            if (lostOption) {
                lostOption.disabled = !isReturn;
                if (!isReturn && conditionSelect.value === 'LOST') {
                    conditionSelect.value = 'GOOD';
                }
            }
            updateVisualLateFee();
        });
    }

    document.getElementById('markAllChecklistOkBtn')?.addEventListener('click', () => {
        document.querySelectorAll('.checklist-result-select').forEach(sel => {
            sel.value = 'OK';
        });
    });

    const inspectionForm = document.getElementById('inspectionForm');
    if (inspectionForm) {
        inspectionForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            await handleRegisterInspectionAndPhotos();
        });
    }
}

async function syncAllData() {
    const unauthBanner = document.getElementById('providerUnauthBanner');
    if (!isAuthenticated()) {
        unauthBanner?.classList.remove('d-none');
    } else {
        unauthBanner?.classList.add('d-none');
    }

    await Promise.all([
        loadCategories(),
        loadChecklistItems(),
        loadProviderTools(),
        loadProviderReservations()
    ]);

    renderChecklistFormTable();
    renderKnownUnitsTable();
}

async function loadCategories() {
    try {
        const cats = await apiFetch('/api/v1/catalog/categories', { skipAuthHeader: true, suppressErrorAlert: true });
        if (Array.isArray(cats)) state.categories = cats;
    } catch {
        // Ignorar
    }
}

async function loadChecklistItems() {
    if (!isAuthenticated()) return;
    try {
        const items = await apiFetch('/api/v1/admin/checklist-items?activeOnly=true', { suppressErrorAlert: true });
        if (Array.isArray(items) && items.length) {
            state.checklistItems = items;
        }
    } catch {
        state.checklistItems = DEFAULT_CHECKLIST_ITEMS;
    }
}

/* =========================================================
 * 1. GESTIÓN DE HERRAMIENTAS
 * ========================================================= */

async function loadProviderTools() {
    const grid = document.getElementById('providerToolsGrid');
    const countLabel = document.getElementById('providerToolsCount');
    if (!isAuthenticated()) {
        if (grid) grid.innerHTML = '';
        return;
    }

    try {
        const pageResult = await apiFetch('/api/v1/provider/tools?page=0&size=50');
        state.tools = pageResult?.items || [];
        if (countLabel) {
            countLabel.textContent = `${state.tools.length} herramienta(s)`;
        }
        renderProviderToolsGrid();
        populateToolSelectDropdowns();
    } catch {
        if (grid) {
            grid.innerHTML = `
                <div class="col-12">
                    <div class="alert alert-warning mb-0">
                        No se pudieron cargar las herramientas del proveedor. Verifica que tu usuario tenga el rol <strong>PROVEEDOR</strong>.
                    </div>
                </div>
            `;
        }
    }
}

function renderProviderToolsGrid() {
    const grid = document.getElementById('providerToolsGrid');
    if (!grid) return;

    if (!state.tools.length) {
        grid.innerHTML = `
            <div class="col-12">
                <div class="card rt-card p-5 text-center">
                    <i class="bi bi-tools display-5 text-muted mb-2"></i>
                    <h3 class="h6 fw-bold">Aún no has registrado herramientas</h3>
                    <p class="small text-muted mb-3">
                        Crea una herramienta en estado DRAFT, asígnale tarifa diaria, registra al menos 1 unidad física y publícala.
                    </p>
                    <div>
                        <button type="button" class="btn btn-rt-primary btn-sm" id="emptyCreateToolBtn">
                            <i class="bi bi-plus-lg me-1"></i>Crear Primera Herramienta
                        </button>
                    </div>
                </div>
            </div>
        `;
        document.getElementById('emptyCreateToolBtn')?.addEventListener('click', () => openToolModal(null));
        return;
    }

    grid.innerHTML = state.tools.map(tool => {
        const rates = tool.rates || {};
        const coverImg = tool.images?.[0]?.url || null;
        const canPublish = tool.status !== 'ACTIVE';
        const canDeactivate = tool.status === 'ACTIVE';

        return `
            <div class="col">
                <article class="card rt-card h-100 d-flex flex-column">
                    <div class="rt-tool-img-wrapper">
                        ${coverImg
                            ? `<img src="${escapeHtml(coverImg)}" alt="${escapeHtml(tool.name)}"
                                    onerror="this.outerHTML='<i class=\\'bi bi-tools rt-tool-placeholder\\'></i>'">`
                            : `<i class="bi bi-tools rt-tool-placeholder"></i>`
                        }
                        <span class="position-absolute top-0 start-0 m-2 badge badge-tool-${escapeHtml(tool.status)}">
                            ${escapeHtml(tool.status)}
                        </span>
                        <span class="position-absolute top-0 end-0 m-2 badge bg-dark">
                            ${tool.availableUnitsCount ?? 0} unid. disp.
                        </span>
                    </div>
                    <div class="card-body d-flex flex-column flex-grow-1">
                        <h3 class="h6 fw-bold mb-1">${escapeHtml(tool.name)}</h3>
                        <p class="small text-muted mb-2">
                            ${escapeHtml(tool.brand || 'Sin marca')} ${tool.model ? `· ${escapeHtml(tool.model)}` : ''}
                        </p>

                        <div class="row g-1 mb-2 mt-auto">
                            <div class="col-4">
                                <div class="rt-rate-box">
                                    <span class="rt-rate-label">Día</span>
                                    <span class="rt-rate-val">${rates.DAY != null ? formatMoney(rates.DAY) : '—'}</span>
                                </div>
                            </div>
                            <div class="col-4">
                                <div class="rt-rate-box">
                                    <span class="rt-rate-label">Semana</span>
                                    <span class="rt-rate-val">${rates.WEEK != null ? formatMoney(rates.WEEK) : '—'}</span>
                                </div>
                            </div>
                            <div class="col-4">
                                <div class="rt-rate-box">
                                    <span class="rt-rate-label">Mes</span>
                                    <span class="rt-rate-val">${rates.MONTH != null ? formatMoney(rates.MONTH) : '—'}</span>
                                </div>
                            </div>
                        </div>

                        <div class="d-flex justify-content-between small text-muted border-top pt-2 mb-3">
                            <span>Garantía: <strong class="text-dark">${formatMoney(tool.depositAmount)}</strong></span>
                            <span>Fotos: <strong>${(tool.images || []).length}/10</strong></span>
                        </div>

                        <div class="d-flex flex-wrap gap-1">
                            <button type="button" class="btn btn-sm btn-outline-dark flex-grow-1 edit-tool-btn"
                                    data-tool-id="${escapeHtml(tool.id)}">
                                <i class="bi bi-pencil-square me-1"></i>Editar / Fotos
                            </button>
                            ${canPublish ? `
                                <button type="button" class="btn btn-sm btn-success publish-tool-btn"
                                        data-tool-id="${escapeHtml(tool.id)}" title="Publicar en catálogo (ACTIVE)">
                                    <i class="bi bi-rocket-takeoff me-1"></i>Publicar
                                </button>
                            ` : ''}
                            ${canDeactivate ? `
                                <button type="button" class="btn btn-sm btn-outline-danger deactivate-tool-btn"
                                        data-tool-id="${escapeHtml(tool.id)}" title="Desactivar herramienta">
                                    <i class="bi bi-pause-circle me-1"></i>Desactivar
                                </button>
                            ` : ''}
                        </div>
                    </div>
                </article>
            </div>
        `;
    }).join('');

    grid.querySelectorAll('.edit-tool-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const tool = state.tools.find(t => t.id === btn.getAttribute('data-tool-id'));
            if (tool) openToolModal(tool);
        });
    });

    grid.querySelectorAll('.publish-tool-btn').forEach(btn => {
        btn.addEventListener('click', async () => {
            const toolId = btn.getAttribute('data-tool-id');
            try {
                await apiFetch(`/api/v1/provider/tools/${toolId}/publish`, { method: 'POST' });
                showSuccessToast('Herramienta publicada en el catálogo público (ACTIVE).');
                await loadProviderTools();
            } catch {
                // ProblemDetail ya mostrado
            }
        });
    });

    grid.querySelectorAll('.deactivate-tool-btn').forEach(btn => {
        btn.addEventListener('click', async () => {
            const toolId = btn.getAttribute('data-tool-id');
            try {
                await apiFetch(`/api/v1/provider/tools/${toolId}/deactivate`, { method: 'POST' });
                showSuccessToast('Herramienta desactivada (INACTIVE).');
                await loadProviderTools();
            } catch {
                // ProblemDetail ya mostrado
            }
        });
    });
}

function openToolModal(tool) {
    document.getElementById('editToolId').value = tool?.id || '';
    document.getElementById('toolNameInput').value = tool?.name || '';
    document.getElementById('toolBrandInput').value = tool?.brand || '';
    document.getElementById('toolModelInput').value = tool?.model || '';
    document.getElementById('toolDescriptionInput').value = tool?.description || '';
    document.getElementById('toolDepositInput').value = tool?.depositAmount ?? '100.00';

    const rates = tool?.rates || {};
    document.getElementById('rateDayInput').value = rates.DAY ?? '';
    document.getElementById('rateWeekInput').value = rates.WEEK ?? '';
    document.getElementById('rateMonthInput').value = rates.MONTH ?? '';

    const selectedCats = new Set(tool?.categoryIds || []);
    const catContainer = document.getElementById('toolCategoriesCheckboxes');
    if (catContainer) {
        if (!state.categories.length) {
            catContainer.innerHTML = `<span class="text-muted small">Sin categorías disponibles</span>`;
        } else {
            catContainer.innerHTML = state.categories.map(cat => `
                <div class="form-check form-check-inline mb-0">
                    <input class="form-check-input tool-cat-checkbox" type="checkbox"
                           id="cat_chk_${escapeHtml(cat.id)}" value="${escapeHtml(cat.id)}"
                           ${selectedCats.has(cat.id) ? 'checked' : ''}>
                    <label class="form-check-label small" for="cat_chk_${escapeHtml(cat.id)}">
                        ${escapeHtml(cat.name)}
                    </label>
                </div>
            `).join('');
        }
    }

    const imgSection = document.getElementById('toolImagesSection');
    if (tool && tool.id) {
        imgSection?.classList.remove('d-none');
        renderToolModalImages(tool);
    } else {
        imgSection?.classList.add('d-none');
    }

    state.toolEditorModal?.show();
}

function renderToolModalImages(tool) {
    const gallery = document.getElementById('toolImagesGallery');
    if (!gallery) return;

    const images = tool.images || [];
    if (!images.length) {
        gallery.innerHTML = `<div class="col-12 small text-muted">Aún no se han subido imágenes para esta herramienta.</div>`;
        return;
    }

    gallery.innerHTML = images.map((img, idx) => `
        <div class="col">
            <div class="card h-100 border p-1 text-center">
                <img src="${escapeHtml(img.url)}" alt="Imagen ${idx + 1}" class="rounded mb-1"
                     style="height: 85px; object-fit: cover;">
                <div class="d-flex justify-content-between align-items-center px-1">
                    <span class="badge ${img.cover || idx === 0 ? 'bg-warning text-dark' : 'bg-light text-secondary'}">
                        ${img.cover || idx === 0 ? 'Portada' : '#' + (idx + 1)}
                    </span>
                    <button type="button" class="btn btn-link btn-sm text-danger p-0 delete-tool-img-btn"
                            data-tool-id="${escapeHtml(tool.id)}" data-img-id="${escapeHtml(img.id)}"
                            title="Eliminar imagen">
                        <i class="bi bi-trash"></i>
                    </button>
                </div>
            </div>
        </div>
    `).join('');

    gallery.querySelectorAll('.delete-tool-img-btn').forEach(btn => {
        btn.addEventListener('click', async () => {
            const toolId = btn.getAttribute('data-tool-id');
            const imgId = btn.getAttribute('data-img-id');
            try {
                await apiFetch(`/api/v1/provider/tools/${toolId}/images/${imgId}`, { method: 'DELETE' });
                showSuccessToast('Imagen eliminada.');
                await loadProviderTools();
                const updated = state.tools.find(t => t.id === toolId);
                if (updated) renderToolModalImages(updated);
            } catch {
                // ProblemDetail mostrado
            }
        });
    });
}

async function saveToolAndRates() {
    const editId = document.getElementById('editToolId').value.trim();
    const name = document.getElementById('toolNameInput').value.trim();
    const brand = document.getElementById('toolBrandInput').value.trim() || null;
    const model = document.getElementById('toolModelInput').value.trim() || null;
    const description = document.getElementById('toolDescriptionInput').value.trim() || null;
    const depositAmount = Number(document.getElementById('toolDepositInput').value || 0);

    const categoryIds = Array.from(document.querySelectorAll('.tool-cat-checkbox:checked'))
        .map(chk => chk.value);

    const rates = [];
    const dayVal = document.getElementById('rateDayInput').value;
    const weekVal = document.getElementById('rateWeekInput').value;
    const monthVal = document.getElementById('rateMonthInput').value;

    if (dayVal && Number(dayVal) > 0) rates.push({ period: 'DAY', price: Number(dayVal) });
    if (weekVal && Number(weekVal) > 0) rates.push({ period: 'WEEK', price: Number(weekVal) });
    if (monthVal && Number(monthVal) > 0) rates.push({ period: 'MONTH', price: Number(monthVal) });

    try {
        let savedTool;
        if (!editId) {
            savedTool = await apiFetch('/api/v1/provider/tools', {
                method: 'POST',
                body: { name, brand, model, description, depositAmount, categoryIds, rates }
            });
            showSuccessToast('Herramienta creada. Ahora puedes adjuntar imágenes o registrar unidades.');
        } else {
            await apiFetch(`/api/v1/provider/tools/${editId}`, {
                method: 'PUT',
                body: { name, brand, model, description, depositAmount, categoryIds }
            });
            savedTool = await apiFetch(`/api/v1/provider/tools/${editId}/rates`, {
                method: 'PUT',
                body: { rates }
            });
            showSuccessToast('Herramienta y tarifas actualizadas.');
        }

        await loadProviderTools();
        if (savedTool) {
            document.getElementById('editToolId').value = savedTool.id;
            document.getElementById('toolImagesSection')?.classList.remove('d-none');
            renderToolModalImages(savedTool);
        }
    } catch {
        // ProblemDetail mostrado
    }
}

async function handleUploadToolImage() {
    const toolId = document.getElementById('editToolId').value.trim();
    const fileInput = document.getElementById('toolImageFileInput');
    if (!toolId || !fileInput?.files?.length) return;

    const formData = new FormData();
    formData.append('file', fileInput.files[0]);

    try {
        await apiFetch(`/api/v1/provider/tools/${toolId}/images`, {
            method: 'POST',
            body: formData
        });
        fileInput.value = '';
        showSuccessToast('Imagen subida correctamente.');
        await loadProviderTools();
        const updated = state.tools.find(t => t.id === toolId);
        if (updated) renderToolModalImages(updated);
    } catch {
        // ProblemDetail mostrado
    }
}

/* =========================================================
 * 2. GESTIÓN DE UNIDADES FÍSICAS
 * ========================================================= */

function populateToolSelectDropdowns() {
    const unitToolSelect = document.getElementById('unitToolSelect');
    const statusToolSelect = document.getElementById('statusToolSelect');

    const optionsHtml = `<option value="">Selecciona una herramienta...</option>` +
        state.tools.map(t => `<option value="${escapeHtml(t.id)}">${escapeHtml(t.name)} (${escapeHtml(t.brand || 'Genérico')})</option>`).join('');

    if (unitToolSelect) unitToolSelect.innerHTML = optionsHtml;
    if (statusToolSelect) statusToolSelect.innerHTML = optionsHtml;
}

async function handleRegisterToolUnit() {
    const toolId = document.getElementById('unitToolSelect').value;
    const serialNumber = document.getElementById('unitSerialInput').value.trim();
    const acquiredOn = document.getElementById('unitAcquiredDate').value || null;
    const notes = document.getElementById('unitNotesInput').value.trim() || null;

    if (!toolId || !serialNumber) return;

    try {
        const unit = await apiFetch(`/api/v1/provider/tools/${toolId}/units`, {
            method: 'POST',
            body: { serialNumber, acquiredOn, notes }
        });

        const tool = state.tools.find(t => t.id === toolId);
        saveKnownUnit({
            id: unit.id,
            toolId: unit.toolId,
            toolName: tool?.name || 'Herramienta',
            serialNumber: unit.serialNumber,
            status: unit.status
        });

        document.getElementById('unitSerialInput').value = '';
        document.getElementById('unitNotesInput').value = '';
        showSuccessToast(`Unidad ${unit.serialNumber} registrada en estado ${unit.status}.`);
        renderKnownUnitsTable();
        await loadProviderTools();
    } catch {
        // ProblemDetail mostrado
    }
}

async function handleChangeUnitStatus() {
    const toolId = document.getElementById('statusToolSelect').value;
    const unitId = document.getElementById('statusUnitIdInput').value.trim();
    const status = document.getElementById('statusTargetSelect').value;
    const notes = document.getElementById('statusNotesInput').value.trim() || null;

    if (!toolId || !unitId || !status) return;

    try {
        const updated = await apiFetch(`/api/v1/provider/tools/${toolId}/units/${unitId}/status`, {
            method: 'PATCH',
            body: { status, notes }
        });

        const tool = state.tools.find(t => t.id === toolId);
        saveKnownUnit({
            id: updated.id,
            toolId: updated.toolId,
            toolName: tool?.name || 'Herramienta',
            serialNumber: updated.serialNumber,
            status: updated.status
        });

        showSuccessToast(`Estado de la unidad ${updated.serialNumber} cambiado a ${updated.status}.`);
        renderKnownUnitsTable();
        await loadProviderTools();
    } catch {
        // ProblemDetail mostrado
    }
}

function renderKnownUnitsTable() {
    const tbody = document.getElementById('knownUnitsTableBody');
    if (!tbody) return;

    if (!state.knownUnits.length) {
        tbody.innerHTML = `
            <tr>
                <td colspan="5" class="text-center text-muted py-3">
                    No hay unidades recientes en caché. Registra una unidad a la izquierda o recibe una reserva para listarla aquí.
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = state.knownUnits.map(u => `
        <tr>
            <td class="fw-medium">${escapeHtml(u.toolName || 'Herramienta')}</td>
            <td><span class="badge bg-light text-dark border font-monospace">${escapeHtml(u.serialNumber)}</span></td>
            <td><span class="badge badge-unit-${escapeHtml(u.status || 'AVAILABLE')}">${escapeHtml(u.status || 'AVAILABLE')}</span></td>
            <td class="font-monospace text-muted" style="font-size: 0.76rem;">${escapeHtml(u.id)}</td>
            <td class="text-end">
                <button type="button" class="btn btn-sm btn-outline-dark py-0 select-unit-btn"
                        data-tool-id="${escapeHtml(u.toolId)}"
                        data-unit-id="${escapeHtml(u.id)}"
                        data-status="${escapeHtml(u.status || 'AVAILABLE')}">
                    Seleccionar
                </button>
            </td>
        </tr>
    `).join('');

    tbody.querySelectorAll('.select-unit-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const toolSelect = document.getElementById('statusToolSelect');
            const unitInput = document.getElementById('statusUnitIdInput');
            const targetSelect = document.getElementById('statusTargetSelect');
            if (toolSelect) toolSelect.value = btn.getAttribute('data-tool-id');
            if (unitInput) unitInput.value = btn.getAttribute('data-unit-id');
            const curr = btn.getAttribute('data-status');
            if (targetSelect) {
                targetSelect.value = curr === 'AVAILABLE' ? 'MAINTENANCE' : 'AVAILABLE';
            }
        });
    });
}

/* =========================================================
 * 3. BANDEJA DE SOLICITUDES (RESERVAS RECIBIDAS)
 * ========================================================= */

async function loadProviderReservations() {
    const listEl = document.getElementById('providerReservationsList');
    const countEl = document.getElementById('providerReservationsCount');
    const pendingBadge = document.getElementById('pendingRequestsBadge');

    if (!isAuthenticated()) {
        if (listEl) listEl.innerHTML = '';
        return;
    }

    try {
        const params = new URLSearchParams({ page: '0', size: '50' });
        if (state.reservationFilter) {
            params.set('status', state.reservationFilter);
        }

        const pageResult = await apiFetch(`/api/v1/reservations?${params.toString()}`);
        const items = pageResult?.items || [];
        state.reservations = items;

        // Extraer unidades de las reservas para poblar la tabla de unidades conocidas
        items.forEach(res => {
            (res.units || []).forEach(u => {
                saveKnownUnit({
                    id: u.unitId,
                    toolId: u.toolId,
                    toolName: u.toolName,
                    serialNumber: u.serialNumber,
                    status: 'AVAILABLE'
                });
            });
        });
        renderKnownUnitsTable();

        const pendingCount = items.filter(r => r.status === 'PENDING').length;
        if (pendingBadge) pendingBadge.textContent = String(pendingCount);
        if (countEl) countEl.textContent = `${items.length} solicitud(es)`;

        renderProviderReservations(items);
        populateInspectionReservationsSelect(items);
    } catch {
        if (listEl) {
            listEl.innerHTML = `
                <div class="col-12">
                    <div class="alert alert-warning mb-0">No se pudieron cargar las solicitudes de reserva.</div>
                </div>
            `;
        }
    }
}

function renderProviderReservations(items) {
    const listEl = document.getElementById('providerReservationsList');
    if (!listEl) return;

    if (!items.length) {
        listEl.innerHTML = `
            <div class="col-12">
                <div class="card rt-card p-4 text-center text-muted small">
                    No hay reservas recibidas para el filtro seleccionado.
                </div>
            </div>
        `;
        return;
    }

    listEl.innerHTML = items.map(res => {
        const isPending = res.status === 'PENDING';
        const canInspect = ['READY_FOR_PICKUP', 'ACTIVE'].includes(res.status);

        const unitsList = (res.units || []).map(u => `
            <span class="badge bg-light text-dark border me-1">
                ${escapeHtml(u.toolName)} · <span class="font-monospace">${escapeHtml(u.serialNumber)}</span>
                (${formatMoney(u.dailyRate)}/día)
            </span>
        `).join('');

        return `
            <div class="col-12">
                <article class="card rt-card p-3">
                    <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-2">
                        <div class="d-flex align-items-center gap-2 flex-wrap">
                            <span class="rt-code-badge">${escapeHtml(res.code)}</span>
                            <span class="badge badge-status-${escapeHtml(res.status)}">${escapeHtml(res.status)}</span>
                            <span class="small text-muted">
                                <i class="bi bi-calendar3 me-1"></i>
                                <strong>${formatDate(res.startDate)}</strong> al <strong>${formatDate(res.endDate)}</strong>
                            </span>
                        </div>
                        <div class="small">
                            Alquiler: <strong>${formatMoney(res.totalAgreedAmount)}</strong> ·
                            Garantía: <strong class="text-primary">${formatMoney(res.totalDepositAmount)}</strong>
                        </div>
                    </div>

                    <div class="mb-2">${unitsList}</div>

                    <div class="d-flex flex-wrap justify-content-between align-items-center border-top pt-2 mt-1 gap-2">
                        <span class="small text-muted font-monospace">Cliente ID: ${escapeHtml(res.clientId)}</span>
                        <div class="d-flex gap-2">
                            ${isPending ? `
                                <button type="button" class="btn btn-sm btn-success confirm-res-btn"
                                        data-res-id="${escapeHtml(res.id)}">
                                    <i class="bi bi-check2-circle me-1"></i>Confirmar Solicitud
                                </button>
                                <button type="button" class="btn btn-sm btn-outline-danger reject-res-btn"
                                        data-res-id="${escapeHtml(res.id)}" data-res-code="${escapeHtml(res.code)}">
                                    <i class="bi bi-x-circle me-1"></i>Rechazar
                                </button>
                            ` : ''}
                            ${canInspect ? `
                                <button type="button" class="btn btn-sm btn-rt-primary go-inspect-btn"
                                        data-res-id="${escapeHtml(res.id)}"
                                        data-res-status="${escapeHtml(res.status)}">
                                    <i class="bi bi-clipboard2-check me-1"></i>Ir a Inspección (${res.status === 'READY_FOR_PICKUP' ? 'DELIVERY' : 'RETURN'})
                                </button>
                            ` : ''}
                            <a href="invoice-view.html?reservationId=${encodeURIComponent(res.id)}"
                               class="btn btn-sm btn-outline-secondary">
                                <i class="bi bi-receipt me-1"></i>Facturación / Liquidación
                            </a>
                        </div>
                    </div>
                </article>
            </div>
        `;
    }).join('');

    listEl.querySelectorAll('.confirm-res-btn').forEach(btn => {
        btn.addEventListener('click', async () => {
            const resId = btn.getAttribute('data-res-id');
            try {
                await apiFetch(`/api/v1/reservations/${resId}/confirm`, { method: 'POST' });
                showSuccessToast('Reserva confirmada (CONFIRMED). Se emitió la factura RENTAL y apertura de garantía.');
                await loadProviderReservations();
            } catch {
                // ProblemDetail mostrado
            }
        });
    });

    listEl.querySelectorAll('.reject-res-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.getElementById('rejectReservationId').value = btn.getAttribute('data-res-id');
            document.getElementById('rejectReservationCode').textContent = btn.getAttribute('data-res-code');
            document.getElementById('rejectReasonInput').value = '';
            state.rejectModal?.show();
        });
    });

    listEl.querySelectorAll('.go-inspect-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const resId = btn.getAttribute('data-res-id');
            const status = btn.getAttribute('data-res-status');
            const tabBtn = document.getElementById('tab-inspections-btn');
            if (tabBtn && window.bootstrap) {
                new window.bootstrap.Tab(tabBtn).show();
            }
            const resSelect = document.getElementById('inspReservationSelect');
            const typeSelect = document.getElementById('inspTypeSelect');
            if (resSelect) {
                resSelect.value = resId;
                populateInspectionUnitsSelect();
                loadReservationInspectionsHistory(resId);
            }
            if (typeSelect) {
                typeSelect.value = status === 'READY_FOR_PICKUP' ? 'DELIVERY' : 'RETURN';
                typeSelect.dispatchEvent(new Event('change'));
            }
        });
    });
}

async function handleRejectReservation() {
    const resId = document.getElementById('rejectReservationId').value;
    const reason = document.getElementById('rejectReasonInput').value.trim();
    if (!resId) return;

    try {
        await apiFetch(`/api/v1/reservations/${resId}/reject`, {
            method: 'POST',
            body: reason ? { reason } : {}
        });
        state.rejectModal?.hide();
        showSuccessToast('Reserva rechazada (REJECTED).');
        await loadProviderReservations();
    } catch {
        // ProblemDetail mostrado
    }
}

/* =========================================================
 * 4. MÓDULO DE INSPECCIONES (DELIVERY / RETURN + MORA + FOTOS)
 * ========================================================= */

function renderChecklistFormTable() {
    const tbody = document.getElementById('inspectionChecklistBody');
    if (!tbody) return;

    tbody.innerHTML = state.checklistItems.map(item => `
        <tr class="rt-checklist-row" data-item-id="${item.id}">
            <td>
                <div class="fw-semibold">${escapeHtml(item.label)}</div>
                <span class="text-muted font-monospace" style="font-size: 0.72rem;">${escapeHtml(item.code)}</span>
            </td>
            <td>
                <select class="form-select form-select-sm checklist-result-select" data-item-id="${item.id}">
                    <option value="OK" selected>OK (Conforme)</option>
                    <option value="DAMAGED">DAMAGED (Dañado)</option>
                    <option value="MISSING">MISSING (Faltante)</option>
                    <option value="NOT_APPLICABLE">NOT_APPLICABLE (N/A)</option>
                </select>
            </td>
            <td>
                <input type="text" class="form-control form-control-sm checklist-note-input"
                       data-item-id="${item.id}" maxlength="300" placeholder="Nota opcional...">
            </td>
        </tr>
    `).join('');
}

function populateInspectionReservationsSelect(reservations) {
    const select = document.getElementById('inspReservationSelect');
    if (!select) return;

    const currentVal = select.value;
    const inspectable = reservations.filter(r =>
        ['READY_FOR_PICKUP', 'ACTIVE', 'RETURNED', 'CLOSED', 'CONFIRMED'].includes(r.status)
    );

    select.innerHTML = `<option value="">Selecciona una reserva...</option>` +
        inspectable.map(r => `
            <option value="${escapeHtml(r.id)}">
                [${escapeHtml(r.code)}] ${escapeHtml(r.status)} · ${formatDate(r.startDate)} → ${formatDate(r.endDate)}
            </option>
        `).join('');

    if (currentVal && inspectable.some(r => r.id === currentVal)) {
        select.value = currentVal;
    }
    populateInspectionUnitsSelect();
}

function populateInspectionUnitsSelect() {
    const resId = document.getElementById('inspReservationSelect')?.value;
    const unitSelect = document.getElementById('inspUnitSelect');
    if (!unitSelect) return;

    const res = state.reservations.find(r => r.id === resId);
    if (!res || !res.units?.length) {
        unitSelect.innerHTML = `<option value="">Primero selecciona una reserva...</option>`;
        return;
    }

    unitSelect.innerHTML = res.units.map(u => `
        <option value="${escapeHtml(u.id)}" data-daily-rate="${u.dailyRate}">
            ${escapeHtml(u.toolName)} — Serie: ${escapeHtml(u.serialNumber)} (Tarifa: ${formatMoney(u.dailyRate)}/día)
        </option>
    `).join('');
}

function updateVisualLateFee() {
    const box = document.getElementById('lateFeeCalculatorBox');
    const textEl = document.getElementById('lateFeeCalculationText');
    const type = document.getElementById('inspTypeSelect')?.value;
    const resId = document.getElementById('inspReservationSelect')?.value;
    const unitSelect = document.getElementById('inspUnitSelect');
    const simDate = document.getElementById('simulatedReturnDate')?.value;

    if (!box || !textEl) return;

    if (type !== 'RETURN') {
        box.classList.add('d-none');
        return;
    }

    box.classList.remove('d-none');
    const res = state.reservations.find(r => r.id === resId);
    if (!res) {
        textEl.innerHTML = `<span class="text-muted">Selecciona una reserva para calcular visualmente los días de mora y recargo aplicable.</span>`;
        return;
    }

    const selectedOpt = unitSelect?.selectedOptions?.[0];
    const dailyRate = Number(selectedOpt?.getAttribute('data-daily-rate') || res.units?.[0]?.dailyRate || 0);
    const endMs = new Date(res.endDate + 'T00:00:00').getTime();
    const retMs = simDate ? new Date(simDate + 'T00:00:00').getTime() : Date.now();
    const lateDays = Math.max(0, Math.floor((retMs - endMs) / 86400000));
    const surchargeMultiplier = 1.5;
    const estimatedLateFee = lateDays * dailyRate * surchargeMultiplier;

    textEl.innerHTML = `
        <div class="d-flex flex-wrap justify-content-between align-items-center pt-1">
            <span>
                Fecha límite pactada: <strong>${formatDate(res.endDate)}</strong> ·
                Días de atraso: <span class="badge ${lateDays > 0 ? 'bg-danger' : 'bg-success'}">${lateDays} día(s)</span>
            </span>
            <span>
                Tarifa diaria: <strong>${formatMoney(dailyRate)}</strong> × 1.5 =
                <strong class="${lateDays > 0 ? 'text-danger' : 'text-success'}">
                    Mora estimada: ${formatMoney(estimatedLateFee)}
                </strong>
            </span>
        </div>
    `;
}

async function handleRegisterInspectionAndPhotos() {
    const reservationId = document.getElementById('inspReservationSelect').value;
    const reservationUnitId = document.getElementById('inspUnitSelect').value;
    const type = document.getElementById('inspTypeSelect').value;
    const overallCondition = document.getElementById('inspConditionSelect').value;
    const observations = document.getElementById('inspObservations').value.trim() || null;
    const photosInput = document.getElementById('inspPhotosInput');
    const submitBtn = document.getElementById('submitInspectionBtn');

    if (!reservationId || !reservationUnitId) {
        showProblemAlert('Selecciona una reserva y una unidad antes de registrar la inspección.', 'warning');
        return;
    }

    const results = state.checklistItems.map(item => {
        const sel = document.querySelector(`.checklist-result-select[data-item-id="${item.id}"]`);
        const noteIn = document.querySelector(`.checklist-note-input[data-item-id="${item.id}"]`);
        return {
            checklistItemId: item.id,
            result: sel ? sel.value : 'OK',
            note: noteIn?.value?.trim() || null
        };
    });

    if (submitBtn) submitBtn.disabled = true;

    try {
        const createdInspection = await apiFetch(
            `/api/v1/reservations/${reservationId}/units/${reservationUnitId}/inspections`,
            {
                method: 'POST',
                body: {
                    type,
                    overallCondition,
                    observations,
                    results
                }
            }
        );

        const files = photosInput?.files ? Array.from(photosInput.files) : [];
        let uploadedCount = 0;
        for (const file of files) {
            const fd = new FormData();
            fd.append('file', file);
            await apiFetch(`/api/v1/inspections/${createdInspection.id}/photos`, {
                method: 'POST',
                body: fd
            });
            uploadedCount++;
        }

        if (photosInput) photosInput.value = '';
        document.getElementById('inspObservations').value = '';

        showSuccessToast(
            `Acta de ${type} registrada correctamente${uploadedCount > 0 ? ` con ${uploadedCount} foto(s) de evidencia` : ''}.`
        );

        await Promise.all([
            loadProviderReservations(),
            loadProviderTools(),
            loadReservationInspectionsHistory(reservationId)
        ]);
    } catch {
        // ProblemDetail ya mostrado
    } finally {
        if (submitBtn) submitBtn.disabled = false;
    }
}

async function loadReservationInspectionsHistory(reservationId) {
    const container = document.getElementById('reservationInspectionsHistory');
    if (!container) return;

    if (!reservationId) {
        container.innerHTML = `<p class="text-muted mb-0">Selecciona una reserva a la izquierda para consultar sus actas y fotos de evidencia.</p>`;
        return;
    }

    container.innerHTML = `<div class="text-muted">Cargando historial de inspecciones...</div>`;

    try {
        const inspections = await apiFetch(`/api/v1/reservations/${reservationId}/inspections`);
        if (!inspections || !inspections.length) {
            container.innerHTML = `<p class="text-muted mb-0">Aún no existen actas registradas para esta reserva.</p>`;
            return;
        }

        container.innerHTML = inspections.map(insp => {
            const resultsBadges = (insp.results || []).map(r => `
                <span class="badge ${r.result === 'OK' ? 'bg-light text-success border' : 'bg-danger-subtle text-danger border'} me-1 mb-1">
                    ${escapeHtml(r.checklistItemCode)}: ${escapeHtml(r.result)}
                </span>
            `).join('');

            const photosHtml = (insp.photos || []).map(p => `
                <button type="button" class="btn btn-sm btn-outline-secondary me-1 mb-1 preview-insp-photo-btn"
                        data-insp-id="${escapeHtml(insp.id)}" data-photo-id="${escapeHtml(p.id)}">
                    <i class="bi bi-image me-1"></i>Ver Foto (${Math.round((p.sizeBytes || 0) / 1024)} KB)
                </button>
            `).join('');

            return `
                <div class="card bg-light border p-3 mb-2">
                    <div class="d-flex justify-content-between align-items-center mb-1">
                        <div>
                            <span class="badge ${insp.type === 'DELIVERY' ? 'bg-primary' : 'bg-dark'}">${escapeHtml(insp.type)}</span>
                            <span class="badge bg-warning text-dark ms-1">${escapeHtml(insp.overallCondition)}</span>
                        </div>
                        <span class="text-muted" style="font-size: 0.78rem;">${formatDateTime(insp.performedAt)}</span>
                    </div>
                    <div class="text-muted font-monospace mb-2" style="font-size: 0.75rem;">
                        Unidad Reserva: ${escapeHtml(insp.reservationUnitId)}
                    </div>
                    ${insp.observations ? `<p class="mb-2 text-secondary">${escapeHtml(insp.observations)}</p>` : ''}
                    <div class="mb-2">${resultsBadges}</div>
                    <div class="mb-2">${photosHtml}</div>
                    <form class="d-flex gap-1 mt-1 attach-extra-photo-form" data-insp-id="${escapeHtml(insp.id)}">
                        <input type="file" class="form-control form-control-sm" accept="image/jpeg,image/png,image/webp" required>
                        <button type="submit" class="btn btn-sm btn-dark text-nowrap">
                            <i class="bi bi-camera me-1"></i>+ Foto
                        </button>
                    </form>
                    <div class="mt-2" id="photo-preview-${escapeHtml(insp.id)}"></div>
                </div>
            `;
        }).join('');

        container.querySelectorAll('.preview-insp-photo-btn').forEach(btn => {
            btn.addEventListener('click', async () => {
                const inspId = btn.getAttribute('data-insp-id');
                const photoId = btn.getAttribute('data-photo-id');
                const previewBox = document.getElementById(`photo-preview-${inspId}`);
                try {
                    const blobUrl = await fetchAuthenticatedBlobUrl(`/api/v1/inspections/${inspId}/photos/${photoId}`);
                    if (previewBox) {
                        previewBox.innerHTML = `
                            <img src="${blobUrl}" alt="Evidencia de inspección" class="img-fluid rounded border" style="max-height: 220px;">
                        `;
                    }
                } catch (err) {
                    showProblemAlert(err.message || 'No se pudo cargar la foto de evidencia.');
                }
            });
        });

        container.querySelectorAll('.attach-extra-photo-form').forEach(form => {
            form.addEventListener('submit', async (e) => {
                e.preventDefault();
                const inspId = form.getAttribute('data-insp-id');
                const fileInput = form.querySelector('input[type="file"]');
                if (!inspId || !fileInput?.files?.length) return;
                const fd = new FormData();
                fd.append('file', fileInput.files[0]);
                try {
                    await apiFetch(`/api/v1/inspections/${inspId}/photos`, {
                        method: 'POST',
                        body: fd
                    });
                    showSuccessToast('Fotografía de evidencia añadida al acta.');
                    await loadReservationInspectionsHistory(reservationId);
                } catch {
                    // ProblemDetail mostrado
                }
            });
        });
    } catch {
        container.innerHTML = `<div class="text-danger">No se pudo cargar el historial de inspecciones.</div>`;
    }
}
