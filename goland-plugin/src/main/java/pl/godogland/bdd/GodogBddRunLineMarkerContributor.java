package pl.godogland.bdd;

import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

public class GodogBddRunLineMarkerContributor extends RunLineMarkerContributor {
    @Override
    public @Nullable Info getInfo(@NotNull PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (file == null || file.getVirtualFile() == null || !file.getName().endsWith(".feature")) {
            return null;
        }

        Project project = element.getProject();
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (document == null || element.getTextRange().isEmpty() || !isLeafElement(element)) {
            return null;
        }

        int offset = element.getTextRange().getStartOffset();
        int lineNumber = document.getLineNumber(offset);
        int lineStart = document.getLineStartOffset(lineNumber);
        int lineEnd = document.getLineEndOffset(lineNumber);
        String line = document.getText(new TextRange(lineStart, lineEnd));
        int keywordOffset = firstNonWhitespaceOffset(line);
        if (keywordOffset < 0 || offset != lineStart + keywordOffset) {
            return null;
        }

        GodogBddRunTarget target = targetForLine(line.trim(), element.getText());
        if (target == null) {
            return null;
        }

        return new Info(
                iconFor(file, target),
                new AnAction[] {
                        new GodogBddRunAction(project, file, target),
                        new GodogBddDebugAction(project, file, target)
                },
                ignored -> target.tooltip()
        );
    }

    private static Icon iconFor(PsiFile file, GodogBddRunTarget target) {
        GodogBddRunStatus status = GodogBddRunResults.statusFor(file.getVirtualFile().getPath(), target);
        if (status == GodogBddRunStatus.PASSED) {
            return AllIcons.RunConfigurations.TestState.Green2;
        }

        if (status == GodogBddRunStatus.FAILED) {
            return AllIcons.RunConfigurations.TestState.Red2;
        }

        return AllIcons.RunConfigurations.TestState.Run;
    }

    private static @Nullable GodogBddRunTarget targetForLine(String line, String elementText) {
        if (line.startsWith("Feature:") && elementText.startsWith("Feature")) {
            return GodogBddRunTarget.feature(line.substring("Feature:".length()).trim());
        }

        if (line.startsWith("Scenario:") && elementText.startsWith("Scenario")) {
            return GodogBddRunTarget.scenario(line.substring("Scenario:".length()).trim());
        }

        return null;
    }

    private static boolean isLeafElement(PsiElement element) {
        return element.getNode() != null && element.getNode().getFirstChildNode() == null;
    }

    private static int firstNonWhitespaceOffset(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (!Character.isWhitespace(line.charAt(i))) {
                return i;
            }
        }

        return -1;
    }
}
