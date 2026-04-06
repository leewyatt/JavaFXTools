package io.github.leewyatt.fxtools.fxmlkit.dialog;

import java.util.Collections;
import java.util.List;

/**
 * Configuration result from the i18n Resource Bundle dialog.
 */
public class I18nConfig {

    public enum Mode {
        EXISTING, CREATE_NEW
    }

    private final Mode mode;
    private final String bundleName;
    private final String bundlePath;
    private final List<String> selectedLocales;

    public I18nConfig(Mode mode, String bundleName, String bundlePath, List<String> selectedLocales) {
        this.mode = mode;
        this.bundleName = bundleName;
        this.bundlePath = bundlePath;
        this.selectedLocales = selectedLocales != null
                ? Collections.unmodifiableList(selectedLocales)
                : Collections.emptyList();
    }

    public Mode getMode() {
        return mode;
    }

    public String getBundleName() {
        return bundleName;
    }

    public String getBundlePath() {
        return bundlePath;
    }

    public List<String> getSelectedLocales() {
        return selectedLocales;
    }
}
