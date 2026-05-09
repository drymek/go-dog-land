package pl.godogland.bdd;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.cucumber.psi.GherkinStep;
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition;

import java.util.ArrayList;
import java.util.List;

public final class GodogAmbiguousStepInspection extends LocalInspectionTool {
    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitFile(@NotNull PsiFile file) {
                if (!isFeatureFile(file)) {
                    return;
                }

                List<AbstractStepDefinition> definitions = allDefinitions(file);
                for (GherkinStep step : PsiTreeUtil.findChildrenOfType(file, GherkinStep.class)) {
                    List<AbstractStepDefinition> matches = matchingDefinitions(step, definitions);
                    if (matches.size() > 1) {
                        holder.registerProblem(step, message(matches));
                    }
                }
            }
        };
    }

    private static List<AbstractStepDefinition> allDefinitions(PsiFile featureFile) {
        List<AbstractStepDefinition> definitions = new ArrayList<>();
        for (PsiFile goFile : GodogCucumberExtension.goFiles(featureFile.getProject())) {
            definitions.addAll(GodogStepScanner.scan(goFile));
        }

        return definitions;
    }

    private static List<AbstractStepDefinition> matchingDefinitions(GherkinStep step, List<AbstractStepDefinition> definitions) {
        List<AbstractStepDefinition> matches = new ArrayList<>();
        String name = step.getName();
        String substitutedName = step.getSubstitutedName();
        for (AbstractStepDefinition definition : definitions) {
            if ((name != null && definition.matches(name)) || (substitutedName != null && definition.matches(substitutedName))) {
                matches.add(definition);
            }
        }

        return matches;
    }

    private static String message(List<AbstractStepDefinition> matches) {
        StringBuilder builder = new StringBuilder("Godog step is ambiguous and matches ");
        builder.append(matches.size()).append(" definitions: ");
        for (int i = 0; i < matches.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(matches.get(i).getCucumberRegex());
        }

        return builder.toString();
    }

    private static boolean isFeatureFile(PsiFile file) {
        return file.getVirtualFile() != null && "feature".equals(file.getVirtualFile().getExtension());
    }
}
