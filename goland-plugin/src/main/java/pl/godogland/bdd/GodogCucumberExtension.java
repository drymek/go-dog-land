package pl.godogland.bdd;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.plugins.cucumber.BDDFrameworkType;
import org.jetbrains.plugins.cucumber.CucumberJvmExtensionPoint;
import org.jetbrains.plugins.cucumber.StepDefinitionCreator;
import org.jetbrains.plugins.cucumber.psi.GherkinFile;
import org.jetbrains.plugins.cucumber.psi.GherkinStep;
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class GodogCucumberExtension implements CucumberJvmExtensionPoint {
    private static final BDDFrameworkType FRAMEWORK_TYPE = new BDDFrameworkType(
            FileTypeManager.getInstance().getFileTypeByExtension("go"),
            "godog"
    );
    private static final StepDefinitionCreator STEP_DEFINITION_CREATOR = new GodogStepDefinitionCreator();

    @Override
    public boolean isStepLikeFile(@NotNull PsiElement element) {
        return isGoFile(element.getContainingFile());
    }

    @Override
    public boolean isWritableStepLikeFile(@NotNull PsiElement element) {
        return isStepLikeFile(element);
    }

    @Override
    public @NotNull BDDFrameworkType getStepFileType() {
        return FRAMEWORK_TYPE;
    }

    @Override
    public @NotNull StepDefinitionCreator getStepDefinitionCreator() {
        return STEP_DEFINITION_CREATOR;
    }

    @Override
    public @NotNull List<AbstractStepDefinition> loadStepsFor(@Nullable PsiFile featureFile, @Nullable Module module) {
        if (featureFile == null) {
            return Collections.emptyList();
        }

        List<AbstractStepDefinition> definitions = new ArrayList<>();
        for (PsiFile goFile : goFiles(featureFile.getProject())) {
            definitions.addAll(GodogStepScanner.scan(goFile));
        }

        return definitions;
    }

    @Override
    public @NotNull Collection<PsiFile> getStepDefinitionContainers(@NotNull GherkinFile gherkinFile) {
        List<PsiFile> containers = new ArrayList<>();
        for (PsiFile file : goFiles(gherkinFile.getProject())) {
            if (GodogStepDefinitionGenerator.canHostStepDefinition(file)) {
                containers.add(file);
            }
        }

        return containers;
    }

    static Collection<PsiFile> goFiles(Project project) {
        return GodogReadAction.compute(() -> {
            Collection<VirtualFile> virtualFiles = FilenameIndex.getAllFilesByExt(project, "go", GlobalSearchScope.projectScope(project));
            return psiFiles(project, virtualFiles);
        });
    }

    static Collection<PsiFile> featureFiles(Project project, SearchScope scope) {
        return GodogReadAction.compute(() -> {
            GlobalSearchScope globalScope = scope instanceof GlobalSearchScope
                    ? (GlobalSearchScope) scope
                    : GlobalSearchScope.projectScope(project);
            Collection<VirtualFile> virtualFiles = FilenameIndex.getAllFilesByExt(project, "feature", globalScope);
            return psiFiles(project, virtualFiles);
        });
    }

    private static Collection<PsiFile> psiFiles(Project project, Collection<VirtualFile> virtualFiles) {
        List<PsiFile> psiFiles = new ArrayList<>();
        PsiManager psiManager = PsiManager.getInstance(project);
        for (VirtualFile virtualFile : virtualFiles) {
            PsiFile psiFile = psiManager.findFile(virtualFile);
            if (psiFile != null) {
                psiFiles.add(psiFile);
            }
        }

        return psiFiles;
    }

    private static boolean isGoFile(PsiFile file) {
        return file != null && file.getVirtualFile() != null && "go".equals(file.getVirtualFile().getExtension());
    }

    private static final class GodogStepDefinitionCreator implements StepDefinitionCreator {
        @Override
        public @Nullable PsiFile createStepDefinitionContainer(@NotNull PsiDirectory dir, @NotNull String name) {
            return null;
        }

        @Override
        public boolean createStepDefinition(@NotNull GherkinStep step, @NotNull PsiFile file, boolean withTemplate) {
            return GodogStepDefinitionGenerator.createStepDefinition(step, file);
        }

        @Override
        public @NotNull String getDefaultStepDefinitionFolderPath(@NotNull GherkinStep step) {
            return "features";
        }

        @Override
        public @NotNull String getStepDefinitionFilePath(@NotNull PsiFile file) {
            return file.getVirtualFile() == null ? file.getName() : file.getVirtualFile().getPath();
        }

        @Override
        public @NotNull String getDefaultStepFileName(@NotNull GherkinStep step) {
            return "steps_test.go";
        }
    }
}
