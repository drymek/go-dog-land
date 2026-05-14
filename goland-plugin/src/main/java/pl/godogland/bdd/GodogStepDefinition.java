package pl.godogland.bdd;

import com.intellij.psi.PsiElement;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.plugins.cucumber.psi.GherkinStep;
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class GodogStepDefinition extends AbstractStepDefinition {
    private final String regex;

    GodogStepDefinition(@NotNull PsiElement element, @NotNull String regex) {
        super(element);
        this.regex = regex;
    }

    @Override
    public @NotNull List<String> getVariableNames() {
        return Collections.emptyList();
    }

    @Override
    protected @NotNull String getCucumberRegexFromElement(@NotNull PsiElement element) {
        return regex == null ? "" : regex;
    }

    public @NotNull Collection<GherkinStep> findSteps(@NotNull SearchScope scope) {
        return GodogFeatureUsageFinder.findUsages(this, scope);
    }
}
