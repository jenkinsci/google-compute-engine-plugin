Behaviour.specify('textarea[name="_.startupScript"]', 'startup-script-exit-reporter-toggle', 0, function (el) {
    var container = el.closest('.repeated-chunk') || el.closest('form');
    var exitReporter = container.querySelector('textarea[name="_.startupScriptExitReporter"]');
    if (!exitReporter) return;
    var entryDiv = exitReporter.closest('.jenkins-form-item');
    if (!entryDiv) return;

    var windowsCheckbox = container.querySelector('input[name="_.windowsConfiguration"]');

    var linuxDefault = 'curl -s -X PUT -H "Metadata-Flavor: Google" \\\n'
        + '  "http://metadata.google.internal/computeMetadata/v1/instance/guest-attributes/startup-script/status" \\\n'
        + '  -d "$1"';
    var windowsDefault = 'Invoke-RestMethod -Method PUT -Body "$($args[0])" `\n'
        + '  -Headers @{\'Metadata-Flavor\'=\'Google\'} `\n'
        + '  -Uri "http://metadata.google.internal/computeMetadata/v1/instance/guest-attributes/startup-script/status"';

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
