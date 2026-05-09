package pl.godogland.bdd;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

final class GodogBddDebugAction extends AnAction {
    private final Project project;
    private final PsiFile file;
    private final GodogBddRunTarget target;

    GodogBddDebugAction(Project project, PsiFile file, GodogBddRunTarget target) {
        super(target.debugTooltip(), null, AllIcons.Actions.StartDebugger);
        this.project = project;
        this.file = file;
        this.target = target;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        GodogBddDebugger.debug(project, file, target);
    }
}
