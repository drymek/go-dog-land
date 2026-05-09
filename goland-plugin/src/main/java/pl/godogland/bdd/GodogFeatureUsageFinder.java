package pl.godogland.bdd;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.cucumber.psi.GherkinStep;
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

final class GodogFeatureUsageFinder {
    private GodogFeatureUsageFinder() {
    }

    static @NotNull Collection<GherkinStep> findUsages(@NotNull AbstractStepDefinition definition, @NotNull SearchScope scope) {
        return GodogReadAction.compute(() -> findUsagesInReadAction(definition, scope));
    }

    private static @NotNull Collection<GherkinStep> findUsagesInReadAction(@NotNull AbstractStepDefinition definition, @NotNull SearchScope scope) {
        var definitionElement = definition.getElement();
        if (definitionElement == null) {
            return List.of();
        }

        List<GherkinStep> usages = new ArrayList<>();
        for (PsiFile featureFile : GodogCucumberExtension.featureFiles(definitionElement.getProject(), GlobalSearchScope.projectScope(definitionElement.getProject()))) {
            for (GherkinStep step : PsiTreeUtil.findChildrenOfType(featureFile, GherkinStep.class)) {
                if (matches(definition, featureFile, step)) {
                    usages.add(step);
                }
            }
        }

        return usages;
    }

    static @NotNull Collection<GherkinStep> findUsages(@NotNull Pattern pattern, @NotNull Project project, @NotNull SearchScope scope) {
        return GodogReadAction.compute(() -> findUsagesInReadAction(pattern, project, scope));
    }

    private static @NotNull Collection<GherkinStep> findUsagesInReadAction(@NotNull Pattern pattern, @NotNull Project project, @NotNull SearchScope scope) {
        List<GherkinStep> usages = new ArrayList<>();
        for (PsiFile featureFile : GodogCucumberExtension.featureFiles(project, GlobalSearchScope.projectScope(project))) {
            for (GherkinStep step : PsiTreeUtil.findChildrenOfType(featureFile, GherkinStep.class)) {
                if (matches(pattern, featureFile, step)) {
                    usages.add(step);
                }
            }
        }

        return usages;
    }

    private static boolean matches(AbstractStepDefinition definition, PsiFile featureFile, GherkinStep step) {
        for (String candidate : stepTexts(featureFile, step)) {
            if (candidate != null && definition.matches(candidate)) {
                return true;
            }
        }

        return false;
    }

    private static boolean matches(Pattern pattern, PsiFile featureFile, GherkinStep step) {
        for (String candidate : stepTexts(featureFile, step)) {
            if (candidate != null && pattern.matcher(candidate).matches()) {
                return true;
            }
        }

        return false;
    }

    static String stepText(PsiFile featureFile, GherkinStep step) {
        for (String candidate : stepTexts(featureFile, step)) {
            if (candidate != null) {
                return candidate;
            }
        }

        return null;
    }

    private static List<String> stepTexts(PsiFile featureFile, GherkinStep step) {
        List<String> candidates = new ArrayList<>();
        candidates.add(step.getName());
        candidates.add(step.getSubstitutedName());

        Document document = PsiDocumentManager.getInstance(featureFile.getProject()).getDocument(featureFile);
        if (document != null && !step.getTextRange().isEmpty()) {
            int line = document.getLineNumber(step.getTextRange().getStartOffset());
            candidates.add(GodogFeatureSteps.stepText(GodogFeatureSteps.lineText(document, line)));
        }

        return candidates;
    }
}
