package pl.godogland.bdd;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;

import java.util.LinkedHashMap;
import java.util.Map;

final class GodogBddEnvironment {
    static final String SETTINGS_KEY = "pl.godogland.bdd.environment";

    private GodogBddEnvironment() {
    }

    static String raw(Project project) {
        return PropertiesComponent.getInstance(project).getValue(SETTINGS_KEY, "");
    }

    static void save(Project project, String value) {
        PropertiesComponent.getInstance(project).setValue(SETTINGS_KEY, value == null ? "" : value);
    }

    static Map<String, String> userEnvironment(Project project) {
        return parse(raw(project));
    }

    static Map<String, String> parse(String text) {
        Map<String, String> environment = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return environment;
        }

        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).trim();
            }

            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }

            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (!key.isEmpty()) {
                environment.put(key, unquote(value));
            }
        }

        return environment;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }

        return value;
    }
}
