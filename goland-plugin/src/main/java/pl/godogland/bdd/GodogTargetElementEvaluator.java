package pl.godogland.bdd;

import com.intellij.codeInsight.TargetElementEvaluatorEx2;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public class GodogTargetElementEvaluator extends TargetElementEvaluatorEx2 {
    @Override
    public @Nullable PsiElement getNamedElement(PsiElement element) {
        return GodogReadAction.compute(() -> GodogStepScanner.registrationAt(element) == null ? null : element);
    }

    @Override
    public @Nullable PsiElement adjustElement(Editor editor, int flags, PsiElement contextElement, PsiElement targetElement) {
        return GodogReadAction.compute(() -> GodogStepScanner.registrationAt(contextElement) == null ? targetElement : contextElement);
    }

    @Override
    public @Nullable PsiElement adjustTargetElement(Editor editor, int flags, int offset, PsiElement targetElement) {
        return GodogReadAction.compute(() -> GodogStepScanner.registrationAt(targetElement) == null ? targetElement : targetElement);
    }
}
