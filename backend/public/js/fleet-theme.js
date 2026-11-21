(() => {
    'use strict';
    const root = document.documentElement;
    const key = 'avigem-fleet-theme';
    let theme = 'light';
    try {
        if (localStorage.getItem(key) === 'dark') theme = 'dark';
    } catch {
        // El selector sigue funcionando si el navegador bloquea el almacenamiento.
    }
    root.classList.toggle('theme-dark', theme === 'dark');

    document.addEventListener('DOMContentLoaded', () => {
        const control = document.querySelector('.fleet-theme-switch');
        const buttons = document.querySelectorAll('button[data-fleet-theme]');
        const updateButtons = () => buttons.forEach(button =>
            button.setAttribute('aria-pressed', String(button.dataset.fleetTheme === theme)));

        updateButtons();
        control.hidden = false;
        const settingsControl = document.querySelector('.fleet-settings-theme');
        if (settingsControl) settingsControl.hidden = false;
        buttons.forEach(button => button.addEventListener('click', () => {
            const next = button.dataset.fleetTheme;
            if (next === theme) return;
            const applyTheme = () => {
                theme = next;
                root.classList.toggle('theme-dark', theme === 'dark');
                updateButtons();
                try { localStorage.setItem(key, theme); } catch { /* Preferencia solo para esta página. */ }
                document.dispatchEvent(new CustomEvent('fleet-theme-change', { detail: theme }));
            };
            if (document.startViewTransition && !matchMedia('(prefers-reduced-motion: reduce)').matches) {
                document.startViewTransition(applyTheme);
            } else {
                applyTheme();
            }
        }));
    });

    window.fleetMapStyle = (select, { light, dark }) => {
        const styles = { light, dark };
        select.value = theme;
        let current = theme;
        return {
            initial: styles[current],
            bind(map, onChange = () => {}) {
                const update = next => {
                    select.value = next;
                    if (next === current) return;
                    current = next;
                    onChange();
                    map.setStyle(styles[current], { diff: false });
                };
                select.disabled = false;
                select.addEventListener('change', () => update(select.value));
                document.addEventListener('fleet-theme-change', event => update(event.detail));
            },
        };
    };
})();
