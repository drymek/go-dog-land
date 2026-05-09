package pl.godogland.bdd;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;

import java.util.function.Supplier;

final class GodogReadAction {
    private GodogReadAction() {
    }

    static <T> T compute(Supplier<T> supplier) {
        if (ApplicationManager.getApplication().isReadAccessAllowed()) {
            return supplier.get();
        }

        return ApplicationManager.getApplication().runReadAction((Computable<T>) supplier::get);
    }
}
