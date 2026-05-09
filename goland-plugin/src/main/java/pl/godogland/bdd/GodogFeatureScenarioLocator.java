package pl.godogland.bdd;

import com.intellij.execution.Location;
import com.intellij.execution.testframework.sm.FileUrlProvider;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GodogFeatureScenarioLocator {
    private static final Pattern SCENARIO_LINE = Pattern.compile("^\\s*Scenario(?: Outline)?:\\s*(.+?)\\s*$");

    private GodogFeatureScenarioLocator() {
    }

    static boolean hasScenario(@NotNull Project project, @NotNull String goTestName) {
        return findScenario(project, goTestName) != null;
    }

    static @NotNull List<Location> findScenarioLocations(@NotNull Project project, @NotNull String goTestName) {
        ScenarioLocation scenario = findScenario(project, goTestName);
        if (scenario == null) {
            return List.of();
        }

        return List.of(FileUrlProvider.createLocationFor(project, scenario.file(), scenario.line()));
    }

    static @Nullable ScenarioLocation findScenario(@NotNull Project project, @NotNull String goTestName) {
        return GodogReadAction.compute(() -> findScenarioInReadAction(project, goTestName));
    }

    private static @Nullable ScenarioLocation findScenarioInReadAction(@NotNull Project project, @NotNull String goTestName) {
        String normalizedGoTestName = normalizeTestName(goTestName);
        if (normalizedGoTestName.isEmpty()) {
            return null;
        }

        for (PsiFile featureFile : GodogCucumberExtension.featureFiles(project, GlobalSearchScope.projectScope(project))) {
            Document document = PsiDocumentManager.getInstance(project).getDocument(featureFile);
            VirtualFile virtualFile = featureFile.getVirtualFile();
            if (document == null || virtualFile == null) {
                continue;
            }

            for (int line = 0; line < document.getLineCount(); line++) {
                String lineText = GodogFeatureSteps.lineText(document, line);
                Matcher matcher = SCENARIO_LINE.matcher(lineText);
                if (!matcher.matches()) {
                    continue;
                }

                if (normalizedGoTestName.equals(normalizeTestName(matcher.group(1)))) {
                    return new ScenarioLocation(virtualFile, line);
                }
            }
        }

        return null;
    }

    private static @NotNull String normalizeTestName(@NotNull String name) {
        String trimmed = name.trim();
        int subtestSeparator = trimmed.lastIndexOf('/');
        if (subtestSeparator >= 0 && subtestSeparator + 1 < trimmed.length()) {
            trimmed = trimmed.substring(subtestSeparator + 1);
        }

        return trimmed.replaceAll("\\s+", "_");
    }

    record ScenarioLocation(@NotNull VirtualFile file, int line) {
    }
}
