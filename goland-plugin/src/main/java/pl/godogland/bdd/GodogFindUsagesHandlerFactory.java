package pl.godogland.bdd;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.SearchScope;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.cucumber.psi.GherkinStep;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class GodogFindUsagesHandlerFactory extends FindUsagesHandlerFactory {
    static @NotNull List<UsageInfo> usageInfosFor(@NotNull PsiElement element, @NotNull SearchScope scope) {
        return GodogReadAction.compute(() -> usageInfosForInReadAction(element, scope));
    }

    private static @NotNull List<UsageInfo> usageInfosForInReadAction(@NotNull PsiElement element, @NotNull SearchScope scope) {
        GodogStepRegistration registration = GodogStepScanner.registrationAt(element);
        if (registration == null) {
            return List.of();
        }

        Pattern pattern;
        try {
            pattern = Pattern.compile(registration.regex());
        } catch (PatternSyntaxException ignored) {
            return List.of();
        }

        List<UsageInfo> usageInfos = new ArrayList<>();
        Collection<GherkinStep> usages = GodogFeatureUsageFinder.findUsages(pattern, element.getProject(), scope);
        for (GherkinStep usage : usages) {
            usageInfos.add(usageInfoFor(usage));
        }

        return usageInfos;
    }

    @Override
    public boolean canFindUsages(@NotNull PsiElement element) {
        return GodogReadAction.compute(() -> GodogStepScanner.registrationAt(element) != null);
    }

    @Override
    public @Nullable FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element, boolean forHighlightUsages) {
        GodogStepRegistration registration = GodogReadAction.compute(() -> GodogStepScanner.registrationAt(element));
        return registration == null ? null : new GodogFindUsagesHandler(element, registration);
    }

    private static final class GodogFindUsagesHandler extends FindUsagesHandler {
        private final GodogStepRegistration registration;

        private GodogFindUsagesHandler(@NotNull PsiElement element, @NotNull GodogStepRegistration registration) {
            super(element);
            this.registration = registration;
        }

        @Override
        public boolean processElementUsages(
                @NotNull PsiElement element,
                @NotNull Processor<? super UsageInfo> processor,
                @NotNull FindUsagesOptions options
        ) {
            for (UsageInfo usageInfo : usageInfosFor(element, options.searchScope)) {
                if (!processor.process(usageInfo)) {
                    return false;
                }
            }

            return true;
        }
    }

    private static UsageInfo usageInfoFor(GherkinStep usage) {
        return new UsageInfo(
                usage.getContainingFile(),
                usage.getTextRange().getStartOffset(),
                usage.getTextRange().getEndOffset()
        );
    }
}
