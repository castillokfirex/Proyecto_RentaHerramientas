import {
    apiFetch,
    renderNavbar,
    isAuthenticated,
    hasRole,
    escapeHtml,
    formatMoney,
    showProblemAlert,
    showSuccessToast
} from './auth.js';

const state = {
    categories: [],
    selectedCategoryId: null,
    searchQuery: '',
    minDayPrice: '',
    maxDayPrice: '',
    startDate: '',
    endDate: '',
    page: 0,
    size: 9,
    tools: [],
    toolDetailsById: new Map(),
    activeModalTool: null,
    quoteModalInstance: null
};

const MAX_ADVANCE_RESERVATION_DAYS = 90; // Máximo 3 meses de anticipación
const MAX_CONTINUOUS_RENTAL_DAYS = 90;   // Máximo 3 meses de duración de alquiler continuo


/**
 * Obtiene la URL de la imagen de una herramienta.
 * Si tool.imageUrl viene definida en el DTO la usa, de lo contrario genera el slug a partir del nombre.
 * Si falla o no está disponible, cae en fallback-equipment.png.
 */
export function getToolImageUrl(tool) {
    if (!tool) return '/images/tools/fallback-equipment.png';
    if (typeof tool === 'string') {
        const slug = tool.toLowerCase()
            .normalize("NFD").replace(/[\u0300-\u036f]/g, "")
            .replace(/[^a-z0-9]+/g, "-")
            .replace(/^-+|-+$/g, "");
        return slug ? `/images/tools/${slug}.png` : '/images/tools/fallback-equipment.png';
    }
    if (tool.imageUrl && typeof tool.imageUrl === 'string' && tool.imageUrl.trim()) {
        return tool.imageUrl.trim();
    }
    if (tool.coverImageUrl && typeof tool.coverImageUrl === 'string' && tool.coverImageUrl.trim()) {
        return tool.coverImageUrl.trim();
    }
    if (Array.isArray(tool.images) && tool.images[0]?.url) {
        return tool.images[0].url.trim();
    }
    const name = tool.name || '';
    const slug = name.toLowerCase()
        .normalize("NFD").replace(/[\u0300-\u036f]/g, "") // quita tildes
        .replace(/[^a-z0-9]+/g, "-")                      // reemplaza no-alfanuméricos por guiones
        .replace(/^-+|-+$/g, "");                         // quita guiones en extremos
    return slug ? `/images/tools/${slug}.png` : '/images/tools/fallback-equipment.png';
}

if (typeof window !== 'undefined') {
    window.getToolImageUrl = getToolImageUrl;
}


function initCatalog() {
    renderNavbar('catalog');

    const quoteModalEl = document.getElementById('quoteModal');
    if (quoteModalEl && window.bootstrap) {
        state.quoteModalInstance = new window.bootstrap.Modal(quoteModalEl);
    }

    initDefaultDates();
    bindEvents();

    loadCategories();
    loadCatalogTools();
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initCatalog);
} else {
    initCatalog();
}


function initDefaultDates() {
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const plusTwo = new Date(today);
    plusTwo.setDate(today.getDate() + 2);

    const maxAdvance = new Date(today);
    maxAdvance.setDate(today.getDate() + MAX_ADVANCE_RESERVATION_DAYS);

    const maxRental = new Date(plusTwo);
    maxRental.setDate(plusTwo.getDate() + MAX_CONTINUOUS_RENTAL_DAYS);

    const todayIso = toIsoDate(today);
    const plusTwoIso = toIsoDate(plusTwo);
    const maxAdvanceIso = toIsoDate(maxAdvance);
    const maxRentalIso = toIsoDate(maxRental);

    const quoteStart = document.getElementById('quoteStartDate');
    const quoteEnd = document.getElementById('quoteEndDate');
    if (quoteStart) {
        quoteStart.min = todayIso;
        quoteStart.max = maxAdvanceIso;
        quoteStart.value = todayIso;
    }
    if (quoteEnd) {
        quoteEnd.min = todayIso;
        quoteEnd.max = maxRentalIso;
        quoteEnd.value = plusTwoIso;
    }

    // Filtros de barra lateral
    const filterStart = document.getElementById('filterStartDate');
    const filterEnd = document.getElementById('filterEndDate');
    if (filterStart) {
        filterStart.min = todayIso;
        filterStart.max = maxAdvanceIso;
    }
    if (filterEnd) {
        filterEnd.min = todayIso;
        filterEnd.max = toIsoDate(new Date(today.getTime() + 180 * 86400000));
    }
}

function syncQuoteEndDateBounds() {
    const quoteStart = document.getElementById('quoteStartDate');
    const quoteEnd = document.getElementById('quoteEndDate');
    if (!quoteStart || !quoteEnd || !quoteStart.value) return;

    quoteEnd.min = quoteStart.value;

    const startObj = new Date(quoteStart.value + 'T00:00:00');
    if (!isNaN(startObj.getTime())) {
        const maxEnd = new Date(startObj);
        maxEnd.setDate(startObj.getDate() + MAX_CONTINUOUS_RENTAL_DAYS);
        quoteEnd.max = toIsoDate(maxEnd);

        if (quoteEnd.value && quoteEnd.value < quoteStart.value) {
            quoteEnd.value = quoteStart.value;
        }
        if (quoteEnd.value && quoteEnd.value > quoteEnd.max) {
            quoteEnd.value = quoteEnd.max;
        }
    }
}

function toIsoDate(d) {
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

function bindEvents() {
    const searchForm = document.getElementById('catalogSearchForm');
    if (searchForm) {
        searchForm.addEventListener('submit', (e) => {
            e.preventDefault();
            state.searchQuery = document.getElementById('searchQueryInput').value.trim();
            state.page = 0;
            loadCatalogTools();
        });
    }

    const extraFiltersForm = document.getElementById('extraFiltersForm');
    if (extraFiltersForm) {
        extraFiltersForm.addEventListener('submit', (e) => {
            e.preventDefault();
            state.minDayPrice = document.getElementById('filterMinPrice').value.trim();
            state.maxDayPrice = document.getElementById('filterMaxPrice').value.trim();
            state.startDate = document.getElementById('filterStartDate').value;
            state.endDate = document.getElementById('filterEndDate').value;
            state.page = 0;
            loadCatalogTools();
        });
    }

    const resetFiltersBtn = document.getElementById('resetFiltersBtn');
    if (resetFiltersBtn) {
        resetFiltersBtn.addEventListener('click', () => {
            document.getElementById('searchQueryInput').value = '';
            document.getElementById('filterMinPrice').value = '';
            document.getElementById('filterMaxPrice').value = '';
            document.getElementById('filterStartDate').value = '';
            document.getElementById('filterEndDate').value = '';
            state.searchQuery = '';
            state.selectedCategoryId = null;
            state.minDayPrice = '';
            state.maxDayPrice = '';
            state.startDate = '';
            state.endDate = '';
            state.page = 0;
            renderCategoryTree();
            loadCatalogTools();
        });
    }

    const clearCategoryBtn = document.getElementById('clearCategoryBtn');
    if (clearCategoryBtn) {
        clearCategoryBtn.addEventListener('click', () => {
            state.selectedCategoryId = null;
            state.page = 0;
            renderCategoryTree();
            loadCatalogTools();
        });
    }

    const refreshCatalogBtn = document.getElementById('refreshCatalogBtn');
    if (refreshCatalogBtn) {
        refreshCatalogBtn.addEventListener('click', () => loadCatalogTools());
    }

    document.querySelectorAll('.quick-cat-filter').forEach(btn => {
        btn.addEventListener('click', () => {
            const kw = btn.getAttribute('data-keyword') || '';
            const match = state.categories.find(c => c.name.toLowerCase().includes(kw.toLowerCase()));
            if (match) {
                state.selectedCategoryId = match.id;
            } else {
                state.searchQuery = kw;
                const searchInput = document.getElementById('searchQueryInput');
                if (searchInput) searchInput.value = kw;
            }
            state.page = 0;
            renderCategoryTree();
            loadCatalogTools();
            const toolsSection = document.getElementById('toolsSection');
            if (toolsSection) {
                toolsSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
        });
    });

    const quoteStartDate = document.getElementById('quoteStartDate');
    const quoteEndDate = document.getElementById('quoteEndDate');
    const quoteQuantity = document.getElementById('quoteQuantity');
    const recalcQuoteBtn = document.getElementById('recalcQuoteBtn');

    if (quoteStartDate) {
        ['input', 'change'].forEach(evt => {
            quoteStartDate.addEventListener(evt, () => {
                syncQuoteEndDateBounds();
                triggerRealtimeQuote();
            });
        });
    }

    if (quoteEndDate) {
        ['input', 'change'].forEach(evt => {
            quoteEndDate.addEventListener(evt, () => triggerRealtimeQuote());
        });
    }

    if (quoteQuantity) {
        ['input', 'change'].forEach(evt => {
            quoteQuantity.addEventListener(evt, () => triggerRealtimeQuote());
        });
    }

    if (recalcQuoteBtn) {
        recalcQuoteBtn.addEventListener('click', () => triggerRealtimeQuote());
    }

    const filterStartDate = document.getElementById('filterStartDate');
    const filterEndDate = document.getElementById('filterEndDate');
    if (filterStartDate && filterEndDate) {
        filterStartDate.addEventListener('change', () => {
            if (filterStartDate.value) {
                filterEndDate.min = filterStartDate.value;
                const fStart = new Date(filterStartDate.value + 'T00:00:00');
                if (!isNaN(fStart.getTime())) {
                    const fMax = new Date(fStart);
                    fMax.setDate(fStart.getDate() + 90);
                    filterEndDate.max = toIsoDate(fMax);
                }
            }
        });
    }

    const confirmReservationBtn = document.getElementById('confirmReservationBtn');
    if (confirmReservationBtn) {
        confirmReservationBtn.addEventListener('click', () => submitDirectReservation());
    }

    const loginToReserveBtn = document.getElementById('loginToReserveBtn');
    if (loginToReserveBtn) {
        loginToReserveBtn.addEventListener('click', () => {
            // Cerrar el modal de cotización limpiamente para que no choquen los backdrops
            if (state.quoteModalInstance) {
                state.quoteModalInstance.hide();
            }
            setTimeout(() => {
                const quickAuthEl = document.getElementById('rtQuickAuthModal');
                if (quickAuthEl && window.bootstrap) {
                    const quickAuthModal = window.bootstrap.Modal.getOrCreateInstance(quickAuthEl);
                    quickAuthModal.show();
                } else {
                    window.location.href = 'auth.html';
                }
            }, 300);
        });
    }
}

async function loadCategories() {
    try {
        const categories = await apiFetch('/api/v1/catalog/categories', { skipAuthHeader: true });
        state.categories = Array.isArray(categories) ? categories : [];
        renderCategoryTree();
    } catch {
        const container = document.getElementById('categoryTreeContainer');
        if (container) {
            container.innerHTML = `<div class="text-danger small py-2">No se pudieron cargar las categorías.</div>`;
        }
    }
}

function renderCategoryTree() {
    const container = document.getElementById('categoryTreeContainer');
    const badge = document.getElementById('activeCategoryBadge');
    if (!container) return;

    if (!state.categories.length) {
        container.innerHTML = `<div class="text-muted small py-2">Sin categorías registradas.</div>`;
        return;
    }

    const roots = state.categories.filter(c => !c.parentId);
    const childrenByParent = new Map();
    state.categories.forEach(c => {
        if (c.parentId) {
            if (!childrenByParent.has(c.parentId)) {
                childrenByParent.set(c.parentId, []);
            }
            childrenByParent.get(c.parentId).push(c);
        }
    });

    let html = `
        <button type="button" class="list-group-item list-group-item-action d-flex justify-content-between align-items-center ${!state.selectedCategoryId ? 'active' : ''}"
                data-cat-id="">
            <span><i class="bi bi-collection me-2"></i>Todas las categorías</span>
        </button>
    `;

    roots.forEach(root => {
        const isSelected = state.selectedCategoryId === root.id;
        html += `
            <button type="button" class="list-group-item list-group-item-action d-flex justify-content-between align-items-center ${isSelected ? 'active' : ''}"
                    data-cat-id="${escapeHtml(root.id)}">
                <span><i class="bi bi-folder2-open me-2"></i>${escapeHtml(root.name)}</span>
            </button>
        `;
        const children = childrenByParent.get(root.id) || [];
        children.forEach(child => {
            const isChildSelected = state.selectedCategoryId === child.id;
            html += `
                <button type="button" class="list-group-item list-group-item-action rt-category-child d-flex justify-content-between align-items-center ${isChildSelected ? 'active' : ''}"
                        data-cat-id="${escapeHtml(child.id)}">
                    <span><i class="bi bi-arrow-return-right me-2"></i>${escapeHtml(child.name)}</span>
                </button>
            `;
        });
    });

    container.innerHTML = html;

    container.querySelectorAll('[data-cat-id]').forEach(btn => {
        btn.addEventListener('click', () => {
            const catId = btn.getAttribute('data-cat-id') || null;
            state.selectedCategoryId = catId;
            state.page = 0;
            renderCategoryTree();
            const toolsSection = document.getElementById('toolsSection') || document.getElementById('toolsGrid');
            if (toolsSection) {
                toolsSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
            loadCatalogTools();
        });
    });

    if (badge) {
        if (state.selectedCategoryId) {
            const found = state.categories.find(c => c.id === state.selectedCategoryId);
            badge.textContent = `Categoría: ${found ? found.name : state.selectedCategoryId}`;
            badge.classList.remove('d-none');
        } else {
            badge.classList.add('d-none');
        }
    }
}

function renderSkeletonGrid(gridEl) {
    if (!gridEl) return;
    let skeletonHtml = '';
    for (let i = 0; i < 6; i++) {
        skeletonHtml += `
            <div class="col">
                <div class="card rt-card h-100 p-0 overflow-hidden">
                    <div class="rt-skeleton" style="height: 195px; width: 100%;"></div>
                    <div class="card-body p-3">
                        <div class="rt-skeleton mb-2" style="height: 14px; width: 35%;"></div>
                        <div class="rt-skeleton mb-2" style="height: 20px; width: 80%;"></div>
                        <div class="rt-skeleton mb-3" style="height: 14px; width: 55%;"></div>
                        <div class="row g-1 mb-3">
                            <div class="col-4"><div class="rt-skeleton" style="height: 44px;"></div></div>
                            <div class="col-4"><div class="rt-skeleton" style="height: 44px;"></div></div>
                            <div class="col-4"><div class="rt-skeleton" style="height: 44px;"></div></div>
                        </div>
                        <div class="rt-skeleton" style="height: 34px; width: 100%;"></div>
                    </div>
                </div>
            </div>
        `;
    }
    gridEl.innerHTML = skeletonHtml;
}

async function loadCatalogTools() {
    const loadingEl = document.getElementById('catalogLoading');
    const gridEl = document.getElementById('toolsGrid');
    const totalCountEl = document.getElementById('catalogTotalCount');

    if (loadingEl) loadingEl.classList.remove('d-none');
    if (gridEl && !gridEl.children.length) {
        renderSkeletonGrid(gridEl);
    }

    try {
        const params = new URLSearchParams();
        if (state.searchQuery) params.set('q', state.searchQuery);
        if (state.selectedCategoryId) params.set('categoryId', state.selectedCategoryId);
        if (state.minDayPrice) params.set('minDayPrice', state.minDayPrice);
        if (state.maxDayPrice) params.set('maxDayPrice', state.maxDayPrice);
        if (state.startDate && state.endDate) {
            params.set('startDate', state.startDate);
            params.set('endDate', state.endDate);
        }
        params.set('page', String(state.page));
        params.set('size', String(state.size));

        const pageResult = await apiFetch(`/api/v1/catalog/tools?${params.toString()}`, { skipAuthHeader: true });
        const items = pageResult?.items || [];
        state.tools = items;

        // Cargar detalles de cada herramienta en paralelo para obtener tarifas DAY / WEEK / MONTH completas
        await Promise.all(items.map(async (summary) => {
            if (!state.toolDetailsById.has(summary.id)) {
                try {
                    const detail = await apiFetch(`/api/v1/catalog/tools/${summary.id}`, {
                        skipAuthHeader: true,
                        suppressErrorAlert: true
                    });
                    if (detail) {
                        state.toolDetailsById.set(summary.id, detail);
                    }
                } catch {
                    // Si falla el detalle, se utiliza el resumen
                }
            }
        }));

        if (totalCountEl) {
            const total = pageResult?.totalElements ?? items.length;
            totalCountEl.textContent = `${total} herramienta${total === 1 ? '' : 's'} disponible${total === 1 ? '' : 's'}`;
        }

        renderToolsGrid(items);
        renderPagination(pageResult);
    } catch {
        if (gridEl) {
            gridEl.innerHTML = `
                <div class="col-12">
                    <div class="alert alert-danger">No se pudo cargar el catálogo de herramientas.</div>
                </div>
            `;
        }
    } finally {
        if (loadingEl) loadingEl.classList.add('d-none');
    }
}

function renderToolsGrid(items) {
    const gridEl = document.getElementById('toolsGrid');
    if (!gridEl) return;

    if (!items.length) {
        gridEl.innerHTML = `
            <div class="col-12">
                <div class="card rt-card p-5 text-center">
                    <i class="bi bi-search display-5 text-muted mb-2"></i>
                    <h3 class="h5 fw-bold">No se encontraron herramientas</h3>
                    <p class="text-muted small mb-0">
                        Prueba ajustando los términos de búsqueda o limpiando los filtros de categoría y precio.
                    </p>
                </div>
            </div>
        `;
        return;
    }

    const categoryMap = new Map(state.categories.map(c => [c.id, c.name]));

    gridEl.innerHTML = items.map(tool => {
        const detail = state.toolDetailsById.get(tool.id);
        const rates = detail?.rates || {};
        const dayRate = rates.DAY ?? tool.dayRate;
        const weekRate = rates.WEEK ?? null;
        const monthRate = rates.MONTH ?? null;

        const coverUrl = tool.coverImageUrl || (detail?.images?.[0]?.url) || null;
        const catBadges = (tool.categoryIds || [])
            .map(cid => categoryMap.get(cid))
            .filter(Boolean)
            .map(name => `<span class="badge bg-light text-secondary border">${escapeHtml(name)}</span>`)
            .join(' ');

        const availCount = tool.availableUnitsCount ?? detail?.availableUnitsCount ?? 0;
        const toolImgUrl = getToolImageUrl(detail ? { ...detail, ...tool } : tool);

        return `
            <div class="col">
                <article class="card rt-card group h-100 d-flex flex-column">
                    <div class="relative w-full h-48 bg-slate-950 flex items-center justify-center overflow-hidden border-b border-slate-800 p-2">
                        <img src="${toolImgUrl}" alt="${escapeHtml(tool.name)}" class="max-h-full max-w-full object-contain group-hover:scale-105 transition-transform duration-300" loading="eager" onerror="this.onerror=null; this.src='/images/tools/fallback-equipment.png';" />
                        <span class="position-absolute top-0 end-0 m-2 rt-avail-badge ${availCount > 0 ? 'bg-success text-white' : 'bg-secondary text-white'}">
                            ${availCount > 0 ? '<span class="rt-dot-pulse"></span>' : '<i class="bi bi-x-circle me-1"></i>'}
                            ${availCount} disp.
                        </span>
                    </div>

                    <div class="card-body d-flex flex-column flex-grow-1">
                        <div class="d-flex flex-wrap gap-1 mb-2">
                            ${catBadges}
                        </div>
                        <h3 class="h6 fw-bold mb-1">${escapeHtml(tool.name)}</h3>
                        <p class="text-muted small mb-2">
                            <i class="bi bi-tag me-1"></i>
                            <strong>${escapeHtml(tool.brand || 'Genérico')}</strong>
                            ${tool.model ? ` · Modelo <span class="font-monospace">${escapeHtml(tool.model)}</span>` : ''}
                        </p>
                        ${detail?.description ? `
                            <p class="small text-secondary mb-3 text-truncate" title="${escapeHtml(detail.description)}">
                                ${escapeHtml(detail.description)}
                            </p>
                        ` : ''}

                        <!-- Tarifas Día / Semana / Mes -->
                        <div class="row g-1 mb-3 mt-auto">
                            <div class="col-4">
                                <div class="rt-rate-box">
                                    <span class="rt-rate-label">Día</span>
                                    <span class="rt-rate-val">${dayRate != null ? formatMoney(dayRate) : '—'}</span>
                                </div>
                            </div>
                            <div class="col-4">
                                <div class="rt-rate-box">
                                    <span class="rt-rate-label">Semana</span>
                                    <span class="rt-rate-val">${weekRate != null ? formatMoney(weekRate) : '—'}</span>
                                </div>
                            </div>
                            <div class="col-4">
                                <div class="rt-rate-box">
                                    <span class="rt-rate-label">Mes</span>
                                    <span class="rt-rate-val">${monthRate != null ? formatMoney(monthRate) : '—'}</span>
                                </div>
                            </div>
                        </div>

                        <div class="d-flex justify-content-between align-items-center small text-muted border-top pt-2 mb-3">
                            <span><i class="bi bi-shield-check text-warning me-1"></i>Garantía:</span>
                            <strong class="text-dark">${formatMoney(tool.depositAmount)}</strong>
                        </div>

                        <button type="button" class="btn btn-rt-primary w-100 btn-sm open-quote-btn"
                                data-tool-id="${escapeHtml(tool.id)}">
                            <i class="bi bi-calculator me-1"></i>Cotizar y Reservar
                        </button>
                    </div>
                </article>
            </div>
        `;
    }).join('');

    gridEl.querySelectorAll('.open-quote-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const toolId = btn.getAttribute('data-tool-id');
            openQuoteModal(toolId);
        });
    });
}

function renderPagination(pageResult) {
    const ul = document.getElementById('catalogPagination');
    if (!ul) return;
    const totalPages = pageResult?.totalPages || 0;
    const current = pageResult?.page || 0;

    if (totalPages <= 1) {
        ul.innerHTML = '';
        return;
    }

    let html = '';
    for (let i = 0; i < totalPages; i++) {
        html += `
            <li class="page-item ${i === current ? 'active' : ''}">
                <button type="button" class="page-link" data-page="${i}">${i + 1}</button>
            </li>
        `;
    }
    ul.innerHTML = html;

    ul.querySelectorAll('[data-page]').forEach(btn => {
        btn.addEventListener('click', () => {
            state.page = Number(btn.getAttribute('data-page'));
            const toolsSection = document.getElementById('toolsSection') || document.getElementById('toolsGrid');
            if (toolsSection) {
                toolsSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
            loadCatalogTools();
        });
    });
}

function openQuoteModal(toolId) {
    const summary = state.tools.find(t => t.id === toolId);
    const detail = state.toolDetailsById.get(toolId);
    if (!summary && !detail) return;

    const tool = { ...summary, ...detail };
    state.activeModalTool = tool;

    document.getElementById('quoteToolId').value = tool.id;
    const maxUnits = Math.max(1, Number(tool.availableUnitsCount || 1));
    const qtyInput = document.getElementById('quoteQuantity');
    qtyInput.max = String(maxUnits);
    qtyInput.value = '1';

    if (state.startDate && state.endDate) {
        document.getElementById('quoteStartDate').value = state.startDate;
        document.getElementById('quoteEndDate').value = state.endDate;
    }

    const rates = tool.rates || {};
    const dayRate = rates.DAY ?? tool.dayRate;
    const weekRate = rates.WEEK ?? null;
    const monthRate = rates.MONTH ?? null;

    const summaryContainer = document.getElementById('quoteToolSummary');
    const modalImgUrl = getToolImageUrl(tool);
    summaryContainer.innerHTML = `
        <div class="relative w-full h-36 bg-slate-950 flex items-center justify-center overflow-hidden rounded-3 mb-3 border border-slate-800 p-2">
            <img src="${modalImgUrl}" alt="${escapeHtml(tool.name)}" class="max-h-full max-w-full object-contain" onerror="this.onerror=null; this.src='/images/tools/fallback-equipment.png';" />
        </div>
        <h4 class="h6 fw-bold mb-1">${escapeHtml(tool.name)}</h4>
        <p class="small text-muted mb-2">
            ${escapeHtml(tool.brand || 'Genérico')} ${tool.model ? `· ${escapeHtml(tool.model)}` : ''}
        </p>
        <p class="small text-secondary mb-3">${escapeHtml(tool.description || 'Herramienta verificada para alquiler.')}</p>
        <ul class="list-group list-group-flush small mb-3">
            <li class="list-group-item d-flex justify-content-between px-0">
                <span>Tarifa Día:</span>
                <strong>${dayRate != null ? formatMoney(dayRate) : '—'}</strong>
            </li>
            <li class="list-group-item d-flex justify-content-between px-0">
                <span>Tarifa Semana (7d):</span>
                <strong>${weekRate != null ? formatMoney(weekRate) : 'No aplica'}</strong>
            </li>
            <li class="list-group-item d-flex justify-content-between px-0">
                <span>Tarifa Mes (30d):</span>
                <strong>${monthRate != null ? formatMoney(monthRate) : 'No aplica'}</strong>
            </li>
            <li class="list-group-item d-flex justify-content-between px-0">
                <span>Garantía por unidad:</span>
                <strong class="text-primary">${formatMoney(tool.depositAmount)}</strong>
            </li>
            <li class="list-group-item d-flex justify-content-between px-0">
                <span>Unidades disponibles:</span>
                <span class="badge bg-success">${tool.availableUnitsCount ?? 1}</span>
            </li>
        </ul>
    `;

    updateModalAuthButtons();
    syncQuoteEndDateBounds();
    if (state.quoteModalInstance) {
        state.quoteModalInstance.show();
    }
    triggerRealtimeQuote();
}

function updateModalAuthButtons() {
    const isClient = hasRole('CLIENTE');
    const authNotice = document.getElementById('quoteAuthNotice');
    const loginBtn = document.getElementById('loginToReserveBtn');
    const confirmBtn = document.getElementById('confirmReservationBtn');

    if (isClient) {
        authNotice?.classList.add('d-none');
        loginBtn?.classList.add('d-none');
        if (confirmBtn) confirmBtn.disabled = false;
    } else {
        authNotice?.classList.remove('d-none');
        loginBtn?.classList.remove('d-none');
        if (confirmBtn) confirmBtn.disabled = true;
    }
}

async function triggerRealtimeQuote() {
    const tool = state.activeModalTool;
    if (!tool) return;

    const startDate = document.getElementById('quoteStartDate').value;
    const endDate = document.getElementById('quoteEndDate').value;
    const quantity = Math.max(1, Number(document.getElementById('quoteQuantity').value || 1));
    const breakdownBody = document.getElementById('quoteBreakdownBody');
    const badge = document.getElementById('quoteSourceBadge');

    if (!startDate || !endDate) {
        return;
    }

    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const todayIso = toIsoDate(today);

    if (startDate < todayIso) {
        badge.className = 'badge bg-danger';
        badge.textContent = 'Fecha pasada';
        breakdownBody.innerHTML = `<div class="text-danger small"><i class="bi bi-exclamation-triangle me-1"></i>La fecha de inicio no puede ser anterior al día de hoy.</div>`;
        return;
    }

    if (endDate < startDate) {
        badge.className = 'badge bg-danger';
        badge.textContent = 'Fechas inválidas';
        breakdownBody.innerHTML = `<div class="text-danger small"><i class="bi bi-exclamation-triangle me-1"></i>La fecha de fin debe ser igual o posterior a la fecha de inicio.</div>`;
        return;
    }

    const startMs = new Date(startDate + 'T00:00:00').getTime();
    const endMs = new Date(endDate + 'T00:00:00').getTime();
    const days = Math.floor((endMs - startMs) / 86400000) + 1;

    if (days > MAX_CONTINUOUS_RENTAL_DAYS) {
        badge.className = 'badge bg-danger';
        badge.textContent = 'Límite excedido';
        breakdownBody.innerHTML = `<div class="text-danger small"><i class="bi bi-exclamation-triangle me-1"></i>El período máximo de alquiler continuo es de ${MAX_CONTINUOUS_RENTAL_DAYS} días (3 meses).</div>`;
        return;
    }

    // Si está autenticado como CLIENTE, invoca POST /api/v1/reservations/quote en tiempo real
    if (isAuthenticated() && hasRole('CLIENTE')) {
        badge.className = 'badge bg-info text-dark';
        badge.textContent = 'Calculando...';
        try {
            const quote = await apiFetch('/api/v1/reservations/quote', {
                method: 'POST',
                body: {
                    startDate,
                    endDate,
                    items: [{ toolId: tool.id, quantity }]
                }
            });
            badge.className = 'badge bg-success';
            badge.textContent = 'Cotización Oficial API';
            renderQuoteBreakdown(quote, false);
            return;
        } catch (err) {
            badge.className = 'badge bg-danger';
            badge.textContent = err.code || 'Error';
            breakdownBody.innerHTML = `<div class="text-danger">${escapeHtml(err.detail || err.message)}</div>`;
            return;
        }
    }

    // Cálculo estimado local para visitantes públicos aún no autenticados como CLIENTE
    const rates = tool.rates || {};
    const dailyRate = Number(rates.DAY ?? tool.dayRate ?? 0);
    const depositUnit = Number(tool.depositAmount ?? 0);
    const unitAgreed = computeEstimatedUnitAmount(days, dailyRate, rates.WEEK, rates.MONTH);


    badge.className = 'badge bg-warning text-dark';
    badge.textContent = 'Estimación Preliminar';
    renderQuoteBreakdown({
        startDate,
        endDate,
        days,
        lines: [{
            toolId: tool.id,
            quantity,
            dailyRate,
            unitAgreedAmount: unitAgreed,
            unitDepositAmount: depositUnit,
            lineAgreedAmount: unitAgreed * quantity,
            lineDepositAmount: depositUnit * quantity
        }],
        totalAgreedAmount: unitAgreed * quantity,
        totalDepositAmount: depositUnit * quantity
    }, true);
}

function computeEstimatedUnitAmount(days, dayRate, weekRate, monthRate) {
    let remaining = days;
    let total = 0;
    if (monthRate && remaining >= 30) {
        const months = Math.floor(remaining / 30);
        total += months * Number(monthRate);
        remaining = remaining % 30;
    }
    if (weekRate && remaining >= 7) {
        const weeks = Math.floor(remaining / 7);
        total += weeks * Number(weekRate);
        remaining = remaining % 7;
    }
    total += remaining * dayRate;
    return total;
}

function renderQuoteBreakdown(quote, isEstimate) {
    const breakdownBody = document.getElementById('quoteBreakdownBody');
    const line = quote.lines?.[0] || {};
    const totalToPayNow = Number(quote.totalAgreedAmount || 0) + Number(quote.totalDepositAmount || 0);

    breakdownBody.innerHTML = `
        <div class="d-flex justify-content-between py-1 border-bottom">
            <span>Periodo (${escapeHtml(quote.startDate)} al ${escapeHtml(quote.endDate)}):</span>
            <strong>${quote.days} día${quote.days === 1 ? '' : 's'}</strong>
        </div>
        <div class="d-flex justify-content-between py-1 border-bottom">
            <span>Cantidad solicitada:</span>
            <strong>${line.quantity || 1} unidad(es)</strong>
        </div>
        <div class="d-flex justify-content-between py-1 border-bottom">
            <span>Tarifa diaria base congelada:</span>
            <span>${formatMoney(line.dailyRate)} / día</span>
        </div>
        <div class="d-flex justify-content-between py-1 border-bottom">
            <span>Subtotal Alquiler Pactado:</span>
            <strong class="text-dark">${formatMoney(quote.totalAgreedAmount)}</strong>
        </div>
        <div class="d-flex justify-content-between py-1 border-bottom">
            <span>Depósito de Garantía Reembolsable:</span>
            <strong class="text-primary">${formatMoney(quote.totalDepositAmount)}</strong>
        </div>
        <div class="d-flex justify-content-between pt-2 mt-1 fs-6">
            <span class="fw-bold">Total Estimado (Alquiler + Garantía):</span>
            <strong class="text-success">${formatMoney(totalToPayNow)}</strong>
        </div>
        ${isEstimate ? `<div class="text-muted mt-2" style="font-size: 0.78rem;">* Inicia sesión como CLIENTE para cotizar directamente contra el motor de precios del servidor.</div>` : ''}
    `;
}

async function submitDirectReservation() {
    if (!hasRole('CLIENTE')) {
        showProblemAlert('Debes iniciar sesión con rol CLIENTE para crear una reserva.', 'warning');
        return;
    }

    const tool = state.activeModalTool;
    if (!tool) return;

    const startDate = document.getElementById('quoteStartDate').value;
    const endDate = document.getElementById('quoteEndDate').value;
    const quantity = Math.max(1, Number(document.getElementById('quoteQuantity').value || 1));
    const btn = document.getElementById('confirmReservationBtn');

    if (btn) btn.disabled = true;
    try {
        const reservation = await apiFetch('/api/v1/reservations', {
            method: 'POST',
            body: {
                startDate,
                endDate,
                items: [{ toolId: tool.id, quantity }]
            }
        });

        if (state.quoteModalInstance) {
            state.quoteModalInstance.hide();
        }

        showSuccessToast(`¡Reserva creada con éxito! Código: ${reservation.code}`);
        setTimeout(() => {
            window.location.href = `client-reservations.html?highlight=${encodeURIComponent(reservation.id)}`;
        }, 1200);
    } catch {
        // ProblemDetail ya mostrado en pantalla
    } finally {
        if (btn) btn.disabled = false;
    }
}
