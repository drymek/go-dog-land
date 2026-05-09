package pl.godogland.bdd;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.cucumber.psi.GherkinStep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class GodogGotoDeclarationHandler implements GotoDeclarationHandler {
    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
        return GodogReadAction.compute(() -> getGotoDeclarationTargetsInReadAction(sourceElement));
    }

    private PsiElement @Nullable [] getGotoDeclarationTargetsInReadAction(@Nullable PsiElement sourceElement) {
        GodogStepRegistration registration = GodogStepScanner.registrationAt(sourceElement);
        if (registration == null) {
            return null;
        }

        Pattern pattern;
        try {
            pattern = Pattern.compile(registration.regex());
        } catch (PatternSyntaxException ignored) {
            return null;
        }

        Collection<GherkinStep> usages = GodogFeatureUsageFinder.findUsages(
                pattern,
                sourceElement.getProject(),
                sourceElement.getResolveScope()
        );
        if (usages.isEmpty()) {
            return null;
        }

        List<PsiElement> targets = new ArrayList<>(usages);
        return targets.toArray(PsiElement.EMPTY_ARRAY);
    }
}
