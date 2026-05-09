package pl.godogland.bdd;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import org.jetbrains.annotations.NotNull;

public class GodogFeatureBreakpointType extends XLineBreakpointType<GodogFeatureBreakpointProperties> {
    static final String ID = "godogland-feature-step-breakpoint";

    public GodogFeatureBreakpointType() {
        super(ID, "Godog BDD Step Breakpoints");
    }

    @Override
    public boolean canPutAt(@NotNull VirtualFile file, int line, @NotNull Project project) {
        if (!"feature".equals(file.getExtension())) {
            return false;
        }

        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document == null || line < 0 || line >= document.getLineCount()) {
            return false;
        }

        return GodogFeatureSteps.isStepLine(GodogFeatureSteps.lineText(document, line));
    }

    @Override
    public GodogFeatureBreakpointProperties createBreakpointProperties(@NotNull VirtualFile file, int line) {
        return new GodogFeatureBreakpointProperties();
    }
}
