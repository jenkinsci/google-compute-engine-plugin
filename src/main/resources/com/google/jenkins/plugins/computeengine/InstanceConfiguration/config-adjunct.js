// UX helper for the startup-script exit reporter field:
//  1. Hides the exit reporter field until a startup script is entered.
//  2. Auto-fills the platform-appropriate default (curl / Invoke-RestMethod)
//     the first time a startup script is typed in.
//  3. Swaps between Linux and Windows defaults when the Windows checkbox toggles,
//     but only if the current value is still a known default (user edits are preserved).
// Default strings are passed from Java via data-* attributes on a hidden div in
// config.jelly, so they are not duplicated here.
Behaviour.specify('textarea[name="_.startupScript"]', 'startup-script-exit-reporter-toggle', 0, function (el) {
    var container = el.closest('.repeated-chunk') || el.closest('form');
    var exitReporter = container.querySelector('textarea[name="_.startupScriptExitReporter"]');
    if (!exitReporter) return;
    var entryDiv = exitReporter.closest('.jenkins-form-item');
    if (!entryDiv) return;

    var windowsCheckbox = container.querySelector('input[name="_.windowsConfiguration"]');

    var defaults = container.querySelector('.exit-reporter-defaults');
    if (!defaults) return;
    var linuxDefault = defaults.dataset.linuxDefault;
    var windowsDefault = defaults.dataset.windowsDefault;

    function isDefault(value) {
        return value === linuxDefault || value === windowsDefault;
    }

    function platformDefault() {
        return windowsCheckbox && windowsCheckbox.checked ? windowsDefault : linuxDefault;
    }

    function toggle(autoFill) {
        var hasScript = el.value.trim().length > 0;
        entryDiv.style.display = hasScript ? '' : 'none';
        if (autoFill && hasScript && exitReporter.value.trim().length === 0) {
            exitReporter.value = platformDefault();
        }
    }

    function swapDefault() {
        if (isDefault(exitReporter.value)) {
            exitReporter.value = platformDefault();
        }
    }

    toggle(false);
    el.addEventListener('input', function () { toggle(true); });
    if (windowsCheckbox) {
        windowsCheckbox.addEventListener('change', swapDefault);
    }
});
