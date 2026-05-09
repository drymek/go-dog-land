package pl.godogland.bdd;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class GodogStepDefinitionInspection extends LocalInspectionTool {
    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitFile(@NotNull PsiFile file) {
                if (!isGoFile(file)) {
                    return;
                }

                for (GodogStepRegistration registration : GodogStepScanner.registrations(file)) {
                    inspectRegistration(holder, file, registration);
                }
            }
        };
    }

    private static void inspectRegistration(ProblemsHolder holder, PsiFile file, GodogStepRegistration registration) {
        Pattern pattern;
        try {
            pattern = Pattern.compile(registration.regex());
        } catch (PatternSyntaxException e) {
            holder.registerProblem(problemElement(file, registration), "Godog step regex is invalid: " + e.getDescription());
            return;
        }

        Collection<?> usages = GodogFeatureUsageFinder.findUsages(pattern, file.getProject(), file.getResolveScope());
        if (usages.isEmpty()) {
            holder.registerProblem(problemElement(file, registration), "Godog step definition is not used by any .feature step");
        }
    }

    private static PsiElement problemElement(PsiFile file, GodogStepRegistration registration) {
        PsiElement element = file.findElementAt(registration.regexStartOffset());
        return element == null ? registration.element() : element;
    }

    private static boolean isGoFile(PsiFile file) {
        return file.getVirtualFile() != null && "go".equals(file.getVirtualFile().getExtension());
    }
}
