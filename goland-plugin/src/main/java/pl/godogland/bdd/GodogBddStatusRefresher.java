package pl.godogland.bdd;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

final class GodogBddStatusRefresher {
    private GodogBddStatusRefresher() {
    }

    static void refresh(@NotNull Project project, @NotNull VirtualFile file) {
        ApplicationManager.getApplication().invokeLater(() -> {
            PsiFile psiFile = GodogReadAction.compute(() -> PsiManager.getInstance(project).findFile(file));
            if (psiFile != null) {
                DaemonCodeAnalyzer.getInstance(project).restart(psiFile);
            }
        });
    }
}
