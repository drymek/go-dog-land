package pl.godogland.bdd;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestStatusListener;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;

public class GodogTestTreeLocationListener extends TestStatusListener {
    @Override
    public void testSuiteFinished(@NotNull AbstractTestProxy root) {
    }

    @Override
    public void testSuiteFinished(@NotNull AbstractTestProxy root, @NotNull Project project) {
        for (AbstractTestProxy proxy : root.getAllTests()) {
            if (!(proxy instanceof SMTestProxy)) {
                continue;
            }

            String testName = proxy.getName();
            if (testName == null) {
                continue;
            }

            GodogFeatureScenarioLocator.ScenarioLocation scenario = GodogFeatureScenarioLocator.findScenario(project, testName);
            if (scenario == null) {
                continue;
            }

            SMTestProxy testProxy = (SMTestProxy) proxy;
            testProxy.setLocator(new GodogGoTestTreeLocator(testName));
            clearLocationCache(testProxy);
            recordStatus(project, scenario, testName, proxy);
        }
    }

    private static void recordStatus(
            Project project,
            GodogFeatureScenarioLocator.ScenarioLocation scenario,
            String testName,
            AbstractTestProxy proxy
    ) {
        GodogBddRunStatus status = proxy.isPassed()
                ? GodogBddRunStatus.PASSED
                : proxy.isDefect() ? GodogBddRunStatus.FAILED : null;
        if (status == null) {
            return;
        }

        GodogBddRunResults.recordScenario(scenario.file().getPath(), testName, status);
        GodogBddStatusRefresher.refresh(project, scenario.file());
    }

    private static void clearLocationCache(SMTestProxy testProxy) {
        try {
            Field field = SMTestProxy.class.getDeclaredField("myLocationMapCachedValue");
            field.setAccessible(true);
            field.set(testProxy, null);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
