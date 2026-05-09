package pl.godogland.bdd;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

final class GodogBddRunAction extends AnAction {
    private final Project project;
    private final PsiFile file;
    private final GodogBddRunTarget target;

    GodogBddRunAction(Project project, PsiFile file, GodogBddRunTarget target) {
        super(target.tooltip(), null, AllIcons.Actions.Execute);
        this.project = project;
        this.file = file;
        this.target = target;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        GodogBddRunner.run(project, file, target);
    }
}
