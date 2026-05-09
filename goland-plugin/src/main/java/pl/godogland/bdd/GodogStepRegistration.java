package pl.godogland.bdd;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

final class GodogStepRegistration {
    private final PsiElement element;
    private final String regex;
    private final int regexStartOffset;
    private final int regexEndOffset;

    GodogStepRegistration(@NotNull PsiElement element, @NotNull String regex, int regexStartOffset, int regexEndOffset) {
        this.element = element;
        this.regex = regex;
        this.regexStartOffset = regexStartOffset;
        this.regexEndOffset = regexEndOffset;
    }

    PsiElement element() {
        return element;
    }

    String regex() {
        return regex;
    }

    int regexStartOffset() {
        return regexStartOffset;
    }

    boolean containsOffsetRange(int startOffset, int endOffset) {
        return startOffset >= regexStartOffset && endOffset <= regexEndOffset;
    }

    boolean intersectsOffsetRange(int startOffset, int endOffset) {
        return startOffset < regexEndOffset && endOffset > regexStartOffset;
    }
}
