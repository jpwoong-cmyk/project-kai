// Progressive enhancement: relocates existing settings and New chat controls.
// Existing form events, Thymeleaf bindings and scanning logic remain unchanged.
(() => {
    const header = document.querySelector('body > header');
    if (!header || document.querySelector('.kai-sidebar')) return;

    const existingSettings = header.querySelector('dl.where');
    const newChat = header.querySelector('form.new-chat');
    if (!existingSettings) return;

    const sidebar = document.createElement('aside');
    sidebar.className = 'kai-sidebar';
    sidebar.id = 'kai-sidebar';
    sidebar.setAttribute('aria-label', 'KAI workspace controls');

    const brand = document.createElement('div');
    brand.className = 'kai-sidebar-brand';
    brand.innerHTML = '<strong>Kai</strong>';
    const close = document.createElement('button');
    close.type = 'button';
    close.className = 'kai-sidebar-close';
    close.textContent = '×';
    close.title = 'Collapse sidebar';
    close.setAttribute('aria-label', 'Close workspace sidebar');
    brand.append(close);
    sidebar.append(brand);

    const caption = document.createElement('div');
    caption.className = 'kai-sidebar-caption';
    caption.textContent = 'Workspace';
    sidebar.append(caption, existingSettings);
    const links = document.createElement('nav');
    links.setAttribute('aria-label', 'Workspace navigation');
    const destinations = [
        ['↗', 'Change directory / settings', '/start'],
        ['▤', 'Past reports', '/history']
    ];
    destinations.forEach(([symbol, label, href]) => {
        const link = document.createElement('a');
        link.className = 'kai-nav-item';
        link.href = href;
        link.textContent = symbol + '  ' + label;
        links.append(link);
    });
    sidebar.append(links);
    if (newChat) sidebar.append(newChat);

    const toggle = document.createElement('button');
    toggle.className = 'kai-sidebar-toggle';
    toggle.id = 'kai-sidebar-toggle';
    toggle.type = 'button';
    toggle.textContent = '☰';
    toggle.title = 'Expand or collapse workspace sidebar';
    toggle.setAttribute('aria-controls', 'kai-sidebar');
    toggle.setAttribute('aria-label', 'Toggle workspace sidebar');
    header.prepend(toggle);
    const note = document.createElement('span');
    note.className = 'kai-top-note';
    note.textContent = 'Document analysis workspace';
    header.append(note);

    const scrim = document.createElement('button');
    scrim.type = 'button';
    scrim.className = 'kai-sidebar-scrim';
    scrim.setAttribute('aria-label', 'Close sidebar');
    document.body.prepend(scrim);
    document.body.prepend(sidebar);
    const smallScreen = window.matchMedia('(max-width: 800px)');

    let savedClosed = false;
    try { savedClosed = localStorage.getItem('kai-sidebar-collapsed') === 'true'; } catch (_) {}
    document.body.classList.toggle('kai-sidebar-collapsed', savedClosed);

    function isOpen() {
        return smallScreen.matches ? document.body.classList.contains('kai-sidebar-open')
            : !document.body.classList.contains('kai-sidebar-collapsed');
    }
    function renderState() {
        const open = isOpen();
        toggle.setAttribute('aria-expanded', String(open));
        sidebar.inert = !open;
        sidebar.setAttribute('aria-hidden', String(!open));
    }
    function setOpen(open) {
        if (smallScreen.matches) {
            document.body.classList.toggle('kai-sidebar-open', open);
        } else {
            document.body.classList.toggle('kai-sidebar-collapsed', !open);
            try { localStorage.setItem('kai-sidebar-collapsed', String(!open)); } catch (_) {}
        }
        renderState();
    }
    toggle.addEventListener('click', () => setOpen(!isOpen()));
    close.addEventListener('click', () => { setOpen(false); toggle.focus(); });
    scrim.addEventListener('click', () => { setOpen(false); toggle.focus(); });
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && smallScreen.matches && isOpen()) {
            setOpen(false); toggle.focus();
        }
    });
    smallScreen.addEventListener('change', () => {
        document.body.classList.remove('kai-sidebar-open');
        renderState();
    });
    renderState();
})();
