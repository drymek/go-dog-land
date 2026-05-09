package pl.godogland.bdd;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class GodogStepMatcher {
    private GodogStepMatcher() {
    }

    static PsiElement findDefinition(PsiFile featureFile, String stepText) {
        List<PsiElement> definitions = findDefinitions(featureFile, stepText);
        return definitions.isEmpty() ? null : definitions.get(0);
    }

    static List<PsiElement> findDefinitions(PsiFile featureFile, String stepText) {
        List<PsiElement> definitions = new ArrayList<>();
        Collection<PsiFile> goFiles = GodogCucumberExtension.goFiles(featureFile.getProject());
        for (PsiFile goFile : goFiles) {
            for (AbstractStepDefinition definition : GodogStepScanner.scan(goFile)) {
                if (definition.matches(stepText)) {
                    definitions.add(definition.getElement());
                }
            }
        }

        return definitions;
    }
}
