package pl.godogland.bdd;

import com.intellij.openapi.editor.Document;

final class GodogFeatureSteps {
    private static final String[] STEP_KEYWORDS = {"Given", "When", "Then", "And", "But"};

    private GodogFeatureSteps() {
    }

    static String lineText(Document document, int line) {
        return document.getText().substring(document.getLineStartOffset(line), document.getLineEndOffset(line));
    }

    static boolean isStepLine(String line) {
        return stepText(line) != null;
    }

    static String stepText(String line) {
        String trimmed = line.trim();
        for (String keyword : STEP_KEYWORDS) {
            if (trimmed.equals(keyword)) {
                return "";
            }

            String prefix = keyword + " ";
            if (trimmed.startsWith(prefix)) {
                return trimmed.substring(prefix.length()).trim();
            }
        }

        return null;
    }
}
