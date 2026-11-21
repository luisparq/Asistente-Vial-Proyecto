(() => {
    'use strict';

    document.querySelectorAll('main .table-wrap').forEach((container, index) => {
        const table = container.querySelector(':scope > table');
        if (!table) return;

        const viewport = document.createElement('div');
        viewport.className = 'table-scroll';
        const hint = document.createElement('p');
        hint.className = 'table-scroll-hint';
        hint.id = `avigem-table-hint-${index}`;
        hint.textContent = 'Desplaza la tabla hacia los lados para ver más columnas.';
        hint.hidden = true;
        table.before(hint, viewport);
        viewport.append(table);

        const heading = container.querySelector('h2, h3') ?? container.closest('.panel')?.querySelector('h2, h3');
        viewport.setAttribute('aria-label', table.caption?.textContent.trim() || heading?.textContent.trim() || 'Tabla de datos');
        viewport.setAttribute('aria-describedby', hint.id);
        const update = () => {
            const scrollable = viewport.clientWidth > 0 && viewport.scrollWidth > viewport.clientWidth;
            hint.hidden = !scrollable;
            if (scrollable) {
                viewport.tabIndex = 0;
                viewport.setAttribute('role', 'region');
            } else {
                viewport.removeAttribute('tabindex');
                viewport.removeAttribute('role');
            }
        };
        const observer = new ResizeObserver(update);
        observer.observe(viewport);
        observer.observe(table);
        update();
    });
})();
