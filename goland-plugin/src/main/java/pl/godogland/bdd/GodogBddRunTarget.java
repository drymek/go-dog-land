package pl.godogland.bdd;

final class GodogBddRunTarget {
    enum Type {
        FEATURE,
        SCENARIO
    }

    private final Type type;
    private final String name;

    private GodogBddRunTarget(Type type, String name) {
        this.type = type;
        this.name = name;
    }

    static GodogBddRunTarget feature(String name) {
        return new GodogBddRunTarget(Type.FEATURE, name);
    }

    static GodogBddRunTarget scenario(String name) {
        return new GodogBddRunTarget(Type.SCENARIO, name);
    }

    Type type() {
        return type;
    }

    String name() {
        return name;
    }

    String testName() {
        return name.trim().replaceAll("\\s+", "_");
    }

    String title() {
        if (type == Type.FEATURE) {
            return "Godog feature: " + name;
        }

        return "Godog scenario: " + name;
    }

    String debugTitle() {
        if (type == Type.FEATURE) {
            return "Debug Godog feature: " + name;
        }

        return "Debug Godog scenario: " + name;
    }

    String tooltip() {
        if (type == Type.FEATURE) {
            return "Run Godog feature";
        }

        return "Run Godog scenario";
    }

    String debugTooltip() {
        if (type == Type.FEATURE) {
            return "Debug Godog feature";
        }

        return "Debug Godog scenario";
    }

    String accessibleName() {
        return tooltip();
    }
}
