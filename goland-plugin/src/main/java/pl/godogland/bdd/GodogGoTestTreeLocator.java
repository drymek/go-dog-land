package pl.godogland.bdd;

import com.intellij.execution.Location;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.List;

final class GodogGoTestTreeLocator implements SMTestLocator, DumbAware {
    private final String scenarioTestName;

    GodogGoTestTreeLocator(@NotNull String scenarioTestName) {
        this.scenarioTestName = scenarioTestName;
    }

    @Override
    public @NotNull List<Location> getLocation(
            @NotNull String protocol,
            @NotNull String path,
            @NotNull Project project,
            @NotNull GlobalSearchScope scope
    ) {
        return GodogFeatureScenarioLocator.findScenarioLocations(project, scenarioTestName);
    }
}
