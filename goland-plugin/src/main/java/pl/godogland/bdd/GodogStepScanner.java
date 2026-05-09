package pl.godogland.bdd;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GodogStepScanner {
    private static final Pattern RAW_STEP = Pattern.compile("\\bctx\\.Step\\s*\\(\\s*`([^`]+)`\\s*,");
    private static final Pattern QUOTED_STEP = Pattern.compile("\\bctx\\.Step\\s*\\(\\s*\"((?:\\\\.|[^\"\\\\])+)\"\\s*,");

    private GodogStepScanner() {
    }

    static List<AbstractStepDefinition> scan(PsiFile file) {
        List<AbstractStepDefinition> definitions = new ArrayList<>();
        for (GodogStepRegistration registration : registrations(file)) {
            definitions.add(new GodogStepDefinition(registration.element(), registration.regex()));
        }

        return definitions;
    }

    static List<GodogStepRegistration> registrations(PsiFile file) {
        List<GodogStepRegistration> registrations = new ArrayList<>();
        String text = file.getText();

        addMatches(file, text, RAW_STEP, registrations);
        addMatches(file, text, QUOTED_STEP, registrations);

        return registrations;
    }

    static GodogStepRegistration registrationAt(PsiElement element) {
        if (element == null || element.getContainingFile() == null || element.getTextRange().isEmpty()) {
            return null;
        }

        int startOffset = element.getTextRange().getStartOffset();
        int endOffset = element.getTextRange().getEndOffset();
        for (GodogStepRegistration registration : registrations(element.getContainingFile())) {
            if (registration.intersectsOffsetRange(startOffset, endOffset)) {
                return registration;
            }
        }

        return null;
    }

    private static void addMatches(PsiFile file, String text, Pattern pattern, List<GodogStepRegistration> registrations) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            PsiElement element = file.findElementAt(matcher.start());
            if (element != null) {
                registrations.add(new GodogStepRegistration(
                        element,
                        unescape(matcher.group(1)),
                        matcher.start(1),
                        matcher.end(1)
                ));
            }
        }
    }

    private static String unescape(String value) {
        return value
                .replace("\\\\", "\\")
                .replace("\\\"", "\"");
    }
}
