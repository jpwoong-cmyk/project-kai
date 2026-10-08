// KAI workspace controls and live scan presentation.
// The original chat forms and /progress polling are deliberately unchanged.
(() => {
    'use strict';
    const header = document.querySelector('body > header');
    if (!header || document.querySelector('.kai-sidebar')) return;
    const original = header.querySelector('dl.where');
    if (!original) return;

    const make = (tag, className, value) => {
        const el = document.createElement(tag);
        if (className) el.className = className;
        if (value !== undefined) el.textContent = value;
        return el;
    };
    const qs = (sel, root = document) => root.querySelector(sel);
    const request = async (url, init = {}) => {
        const response = await fetch(url, { credentials: 'same-origin', ...init });
        let result;
        try { result = await response.json(); } catch (_) { throw new Error('KAI did not return a valid response.'); }
        if (!response.ok || result.ok === false || result.error) throw new Error(result.problem || result.error || `Request failed (${response.status}).`);
        return result;
    };
    const jsonPost = (url, value) => request(url, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(value)
    });
    const working = Boolean(qs('#log'));
    const newChat = qs('form.new-chat', header);
    const previouslyUnfinished = newChat?.querySelector('button')?.dataset.warn;
    const dt = Array.from(original.querySelectorAll(':scope > dt'));
    const ddFor = label => dt.find(x => x.textContent.trim().toLowerCase() === label)?.nextElementSibling;
    const folderRow = ddFor('scanning');
    const historyRow = ddFor('history');
    const modelRow = ddFor('ai model');
    if (!folderRow || !historyRow || !modelRow) return;
    let scanning = Array.from(folderRow.querySelectorAll('.chip code')).map(x => x.textContent.trim()).filter(Boolean);
    let historyFolder = qs('.chip code', historyRow)?.textContent.trim() || '';
    let selectedModel = qs('.chip code', modelRow)?.textContent.trim() || '';

    const sidebar = make('aside', 'kai-sidebar');
    sidebar.id = 'kai-sidebar';
    sidebar.setAttribute('aria-label', 'KAI workspace controls');
    const brand = make('div', 'kai-sidebar-brand');
    brand.append(make('strong', '', 'Kai'));
    const close = make('button', 'kai-sidebar-close', '×');
    close.type = 'button'; close.title = 'Collapse sidebar'; close.setAttribute('aria-label', 'Close workspace sidebar');
    brand.append(close);
    sidebar.append(brand, make('div', 'kai-sidebar-caption', 'Workspace'));
    const controls = make('div', 'kai-settings');
    const status = make('p', 'kai-settings-status');
    status.setAttribute('role', 'status'); status.setAttribute('aria-live', 'polite');
    const scanBlock = make('section', 'kai-settings-section');
    const historyBlock = make('section', 'kai-settings-section');
    const modelBlock = make('section', 'kai-settings-section');
    scanBlock.append(make('h2', '', 'Folders to scan'));
    historyBlock.append(make('h2', '', 'History folder'));
    modelBlock.append(make('h2', '', 'AI model'));
    controls.append(scanBlock, historyBlock, modelBlock, status);
    sidebar.append(controls);

    const nav = make('nav', 'kai-sidebar-nav');
    nav.setAttribute('aria-label', 'Workspace navigation');
    const pastReports = make('a', 'kai-nav-item', '▤  Past reports');
    pastReports.href = '/history';
    nav.append(pastReports);
    sidebar.append(nav);
    if (newChat) sidebar.append(newChat);

    const toggle = make('button', 'kai-sidebar-toggle', '☰');
    toggle.type = 'button'; toggle.id = 'kai-sidebar-toggle';
    toggle.setAttribute('aria-controls', 'kai-sidebar');
    toggle.setAttribute('aria-label', 'Expand or collapse workspace sidebar');
    header.prepend(toggle);
    const note = make('span', 'kai-top-note', 'Knowledge Analysis Intelligence');
    header.append(note);
    const scrim = make('button', 'kai-sidebar-scrim');
    scrim.type = 'button'; scrim.setAttribute('aria-label', 'Close sidebar');
    document.body.prepend(scrim);
    document.body.prepend(sidebar);
    original.remove(); // No duplicate history link, settings link, or model chip.

    const smallScreen = window.matchMedia('(max-width: 800px)');
    let savedClosed = false;
    try { savedClosed = localStorage.getItem('kai-sidebar-collapsed') === 'true'; } catch (_) { /* private browsing */ }
    document.body.classList.toggle('kai-sidebar-collapsed', savedClosed);
    const isOpen = () => smallScreen.matches ? document.body.classList.contains('kai-sidebar-open')
        : !document.body.classList.contains('kai-sidebar-collapsed');
    const showState = () => {
        const open = isOpen();
        toggle.setAttribute('aria-expanded', String(open));
        sidebar.inert = !open;
        sidebar.setAttribute('aria-hidden', String(!open));
    };
    const setOpen = open => {
        if (smallScreen.matches) document.body.classList.toggle('kai-sidebar-open', open);
        else {
            document.body.classList.toggle('kai-sidebar-collapsed', !open);
            try { localStorage.setItem('kai-sidebar-collapsed', String(!open)); } catch (_) { /* private browsing */ }
        }
        showState();
    };
    toggle.addEventListener('click', () => setOpen(!isOpen()));
    close.addEventListener('click', () => { setOpen(false); toggle.focus(); });
    scrim.addEventListener('click', () => { setOpen(false); toggle.focus(); });
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && smallScreen.matches && isOpen()) { setOpen(false); toggle.focus(); }
    });
    smallScreen.addEventListener('change', () => {
        document.body.classList.remove('kai-sidebar-open'); showState();
    });
    showState();

    let busy = false;
    const message = (text, bad = false) => {
        status.textContent = text; status.classList.toggle('bad', bad);
    };
    function guardChange() {
        if (working || busy) { message(working ? 'Wait until this scan finishes.' : 'Please wait for the current update.', true); return false; }
        if (previouslyUnfinished && !confirm('Changing workspace settings can make older unfinalized proposals stale. Continue without resetting the chat?')) return false;
        return true;
    }
    async function pickPath() {
        const result = await request('/start/browse', { method: 'POST' });
        return result.path || ''; // Cancel leaves the original unchanged.
    }
    async function saveFolders(nextScan, nextBackup) {
        const result = await jsonPost('/workspace/folders', { scan: nextScan, backup: nextBackup });
        scanning = nextScan.slice(); historyFolder = nextBackup;
        message('Saved. Your next scan will use the selected folders.');
        renderFolders();
        // New source-file options and the empty-chat recent reports depend on the settings.
        window.location.reload();
        return result;
    }
    function button(text, action, aria) {
        const b = make('button', 'kai-inline-action', text); b.type = 'button';
        if (aria) b.setAttribute('aria-label', aria);
        b.disabled = working;
        b.addEventListener('click', async () => {
            if (!guardChange()) return;
            busy = true; b.disabled = true; b.textContent = 'Opening…'; message('');
            try { await action(); }
            catch (e) { message(e.message, true); }
            finally { busy = false; b.disabled = working; b.textContent = text; }
        });
        return b;
    }
    function pathField(path, label) {
        const input = make('input', 'kai-path-input');
        input.type = 'text'; input.readOnly = true; input.value = path;
        input.title = path; input.setAttribute('aria-label', label);
        return input;
    }
    function renderFolders() {
        scanBlock.replaceChildren(make('h2', '', 'Folders to scan'));
        scanning.forEach((path, index) => {
            const row = make('div', 'kai-folder-row');
            row.append(pathField(path, 'Scan folder ' + (index + 1)));
            row.append(button('Browse', async () => {
                const chosen = await pickPath();
                if (chosen && chosen !== path) {
                    const next = scanning.slice(); next[index] = chosen;
                    await saveFolders(next, historyFolder);
                }
            }, 'Browse for scan folder ' + (index + 1)));
            const remove = button('×', async () => {
                if (scanning.length < 2) { message('Keep at least one scanning folder.', true); return; }
                if (!confirm('Stop scanning this folder? This does not delete files.')) return;
                const next = scanning.filter((_, n) => n !== index);
                await saveFolders(next, historyFolder);
            }, 'Remove scan folder ' + (index + 1));
            remove.classList.add('kai-remove-folder');
            remove.disabled = working || scanning.length < 2;
            row.append(remove);
            scanBlock.append(row);
        });
        scanBlock.append(button('+ Add folder', async () => {
            const chosen = await pickPath();
            if (chosen && !scanning.includes(chosen)) await saveFolders([...scanning, chosen], historyFolder);
        }, 'Add another scan folder'));
        historyBlock.replaceChildren(make('h2', '', 'History folder'));
        const history = make('div', 'kai-folder-row');
        history.append(pathField(historyFolder, 'History folder'));
        history.append(button('Browse', async () => {
            const chosen = await pickPath();
            if (chosen && chosen !== historyFolder) await saveFolders(scanning, chosen);
        }, 'Browse for history folder'));
        historyBlock.append(history);
    }
    renderFolders();

    const modelSelect = make('select', 'kai-model-select');
    modelSelect.setAttribute('aria-label', 'Choose AI model');
    modelSelect.disabled = true;
    const modelLabel = make('p', 'kai-model-caption', 'Current: ' + selectedModel);
    modelBlock.append(modelSelect, modelLabel);
    const option = (name, value) => {
        const el = make('option', '', name); el.value = value; return el;
    };
    async function loadModels() {
        modelSelect.replaceChildren(option('Loading models…', selectedModel));
        try {
            const result = await request('/workspace/models');
            const listed = result.models || [];
            modelSelect.replaceChildren();
            if (!listed.length) {
                modelSelect.append(option(selectedModel + ' (model list unavailable)', selectedModel));
                modelSelect.disabled = true;
                modelLabel.textContent = 'This provider does not publish a model list.';
                return;
            }
            if (selectedModel && !listed.includes(selectedModel)) modelSelect.append(option(selectedModel + ' (current)', selectedModel));
            listed.forEach(m => modelSelect.append(option(m, m)));
            modelSelect.value = selectedModel;
            modelSelect.disabled = working;
            modelLabel.textContent = listed.length + ' available models';
        } catch (e) {
            modelSelect.replaceChildren(option(selectedModel || 'Unavailable', selectedModel));
            modelSelect.disabled = true; modelLabel.textContent = e.message;
        }
    }
    modelSelect.addEventListener('change', async () => {
        const nextModel = modelSelect.value;
        if (nextModel === selectedModel) return;
        if (!guardChange()) { modelSelect.value = selectedModel; return; }
        busy = true; modelSelect.disabled = true; message('Checking ' + nextModel + '…');
        try {
            await jsonPost('/workspace/model', { model: nextModel });
            selectedModel = nextModel;
            message('AI model switched to ' + selectedModel + '.');
            modelLabel.textContent = 'Current model: ' + selectedModel;
        } catch (e) { modelSelect.value = selectedModel; message(e.message, true); }
        finally { busy = false; modelSelect.disabled = working; }
    });
    loadModels();

    // Live scan status board. Keep #log and its children untouched: the built-in
    // progress poll uses log.children.length to obtain only unseen events.
    const log = qs('#log');
    if (log) {
        const shell = make('div', 'kai-scan-board');
        const intro = make('p', 'kai-scan-intro', 'Files appear in these panels as the scanner decides whether they need updating.');
        const columns = make('div', 'kai-scan-columns');
        function panel(title, cls) {
            const article = make('section', 'kai-scan-panel ' + cls);
            const heading = make('div', 'kai-scan-heading');
            const label = make('h3', '', title);
            const count = make('span', 'kai-scan-count', '0');
            heading.append(label, count);
            const empty = make('p', 'kai-scan-empty', 'Waiting for scan results…');
            const list = make('ul', 'kai-scan-list'); list.setAttribute('aria-label', title);
            article.append(heading, empty, list);
            columns.append(article);
            return { list, count, empty };
        }
        const unchanged = panel('No change needed', 'kai-scan-clean');
        const affected = panel('Needs updating', 'kai-scan-affected');
        shell.append(intro, columns);
        // Skipped files are visible, not silently filtered out by the repository.
        const excluded = make('section', 'kai-scan-panel kai-scan-skipped');
        const excludedHeading = make('div', 'kai-scan-heading');
        const excludedCount = make('span', 'kai-scan-count', '0');
        excludedHeading.append(make('h3', '', 'Skipped or unsupported'), excludedCount);
        const excludedEmpty = make('p', 'kai-scan-empty', 'No skipped files so far.');
        const excludedList = make('ul', 'kai-scan-list');
        excluded.append(excludedHeading, excludedEmpty, excludedList);
        shell.append(excluded);
        const issues = make('p', 'kai-scan-issues'); issues.setAttribute('role', 'status');
        shell.append(issues);
        const details = make('details', 'kai-scan-raw');
        const summary = make('summary', '', 'View detailed scan activity');
        log.parentNode.insertBefore(shell, log);
        log.replaceWith(details);
        details.append(summary, log);
        const seen = new Map();
        let failed = 0;
        const excludedSeen = new Set();
        function updatePanel(target) {
            target.count.textContent = String(target.list.children.length);
            target.empty.hidden = target.list.children.length > 0;
        }
        function record(file, kind) {
            if (seen.get(file) === kind) return;
            if (seen.has(file)) {
                const old = qs('li[data-path="' + CSS.escape(file) + '"]', shell);
                old?.remove();
            }
            seen.set(file, kind);
            const target = kind === 'affected' ? affected : unchanged;
            const item = make('li', '', file.replace(/^.*[\\/]/, ''));
            item.title = file; item.dataset.path = file;
            target.list.append(item);
            updatePanel(unchanged); updatePanel(affected);
        }
        function digest(line) {
            // Scanner messages are timestamped by Progress.add(). Text is display-only.
            const skip = line.match(/Skipped file:\s+(.+?)\s+\((\d+) bytes\):\s+(.+)$/);
            if (skip) {
                if (!excludedSeen.has(skip[1])) {
                    excludedSeen.add(skip[1]);
                    const item = make('li', '', skip[1].replace(/^.*[\\/]/, '') +
                        ' · ' + (Number(skip[2]) / 1000000).toFixed(2) + ' MB · ' + skip[3]);
                    item.title = skip[1];
                    excludedList.append(item);
                    excludedEmpty.hidden = true;
                    excludedCount.textContent = String(excludedSeen.size);
                }
                return;
            }
            const no = line.match(/Scanner:\s+(.+?)\s+needs no change$/);
            if (no) { record(no[1], 'clean'); return; }
            const yes = line.match(/Scanner:\s+(.+?)\s+must change(?:\s+\(update by hand\))?$/);
            if (yes) { record(yes[1], 'affected'); return; }
            if (/Scanner:\s+could not check\s+/.test(line)) {
                failed += 1;
                issues.textContent = failed + ' file' + (failed === 1 ? '' : 's') + ' could not be checked. See detailed activity.';
            }
        }
        const observer = new MutationObserver(records => {
            records.forEach(record => record.addedNodes.forEach(node => {
                if (node.nodeType === Node.ELEMENT_NODE && node.parentNode === log) digest(node.textContent || '');
            }));
        });
        Array.from(log.children).forEach(node => digest(node.textContent || ''));
        observer.observe(log, { childList: true });
    }
})();
