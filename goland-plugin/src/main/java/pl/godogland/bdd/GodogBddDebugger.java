package pl.godogland.bdd;

import com.goide.execution.GoBuildingRunConfiguration;
import com.goide.dlv.breakpoint.DlvBreakpointProperties;
import com.goide.dlv.breakpoint.DlvBreakpointType;
import com.goide.execution.testing.GoTestFramework;
import com.goide.execution.testing.GoTestRunConfiguration;
import com.goide.execution.testing.GoTestRunConfigurationType;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class GodogBddDebugger {
    private GodogBddDebugger() {
    }

    static void debug(Project project, PsiFile file, GodogBddRunTarget target) {
        try {
            String basePath = project.getBasePath();
            if (basePath == null || file.getVirtualFile() == null) {
                notifyError(project, "Cannot resolve project or feature file path.");
                return;
            }

            GodogBddRunner.saveDocuments(file);
            mirrorFeatureBreakpoints(project, file);

            GoTestRunConfigurationType type = GoTestRunConfigurationType.getInstance();
            ConfigurationFactory factory = type.getFactory();
            RunManager runManager = RunManager.getInstance(project);
            RunnerAndConfigurationSettings settings = runManager.createConfiguration(target.debugTitle(), factory);
            GoTestRunConfiguration configuration = (GoTestRunConfiguration) settings.getConfiguration();

            configuration.setKind(GoBuildingRunConfiguration.Kind.PACKAGE);
            configuration.setRootDirectory(basePath);
            configuration.setPackage(packagePath(basePath, file.getVirtualFile().getPath()));
            configuration.setWorkingDirectory(basePath);
            configuration.setTestFramework(GoTestFramework.fromName("gotest"));
            configuration.setPassParentEnvironment(true);

            Map<String, String> env = new HashMap<>(GodogBddEnvironment.userEnvironment(project));
            env.put("GODOGLAND_BDD_FEATURE", relativeFeaturePath(basePath, file.getVirtualFile().getPath()));
            if (target.type() == GodogBddRunTarget.Type.SCENARIO) {
                String pattern = "^TestFeatures$/" + Pattern.quote(target.testName()) + "$";
                configuration.setPattern(pattern);
                env.put("GODOGLAND_BDD_SCENARIO", pattern);
            }
            configuration.setCustomEnvironment(env);

            runManager.setTemporaryConfiguration(settings);
            runManager.setSelectedConfiguration(settings);
            executeConfiguration(project, settings, DefaultDebugExecutor.getDebugExecutorInstance());
        } catch (Throwable t) {
            notifyError(project, t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static void mirrorFeatureBreakpoints(Project project, PsiFile featureFile) {
        XBreakpointManager manager = XDebuggerManager.getInstance(project).getBreakpointManager();
        GodogFeatureBreakpointType featureBreakpointType = findBreakpointType(GodogFeatureBreakpointType.class);
        DlvBreakpointType goBreakpointType = findBreakpointType(DlvBreakpointType.class);
        if (featureBreakpointType == null || goBreakpointType == null || featureFile.getVirtualFile() == null) {
            return;
        }

        Collection<? extends XBreakpoint<?>> breakpoints = manager.getBreakpoints(featureBreakpointType);
        Document featureDocument = FileDocumentManager.getInstance().getDocument(featureFile.getVirtualFile());
        if (featureDocument == null) {
            return;
        }

        for (XBreakpoint<?> rawBreakpoint : breakpoints) {
            if (!(rawBreakpoint instanceof XLineBreakpoint<?>)) {
                continue;
            }

            XLineBreakpoint<?> breakpoint = (XLineBreakpoint<?>) rawBreakpoint;
            if (!breakpoint.isEnabled() || !featureFile.getVirtualFile().getUrl().equals(breakpoint.getFileUrl())) {
                continue;
            }

            String stepText = GodogFeatureSteps.stepText(GodogFeatureSteps.lineText(featureDocument, breakpoint.getLine()));
            if (stepText == null) {
                continue;
            }

            PsiElement definition = GodogStepMatcher.findDefinition(featureFile, stepText);
            if (definition == null || definition.getContainingFile() == null) {
                continue;
            }

            VirtualFile goFile = definition.getContainingFile().getVirtualFile();
            if (goFile == null) {
                continue;
            }

            Document goDocument = FileDocumentManager.getInstance().getDocument(goFile);
            if (goDocument == null) {
                continue;
            }

            int line = goDocument.getLineNumber(definition.getTextRange().getStartOffset());
            if (manager.findBreakpointsAtLine(goBreakpointType, goFile, line).isEmpty()) {
                XLineBreakpoint<DlvBreakpointProperties> goBreakpoint = manager.addLineBreakpoint(
                        goBreakpointType,
                        goFile.getUrl(),
                        line,
                        goBreakpointType.createBreakpointProperties(goFile, line),
                        true
                );
                goBreakpoint.setTemporary(true);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends XBreakpointType<?, ?>> T findBreakpointType(Class<T> clazz) {
        for (XBreakpointType<?, ?> type : XBreakpointType.EXTENSION_POINT_NAME.getExtensionList()) {
            if (clazz.isInstance(type)) {
                return (T) type;
            }
        }

        return null;
    }

    private static void executeConfiguration(
            Project project,
            RunnerAndConfigurationSettings settings,
            Executor executor
    ) throws com.intellij.execution.ExecutionException {
        ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.createOrNull(executor, settings);
        if (builder == null) {
            notifyError(project, "Cannot create debug execution environment.");
            return;
        }

        builder.buildAndExecute();
    }

    private static String relativeFeaturePath(String basePath, String featurePath) {
        try {
            return Path.of(basePath).relativize(Path.of(featurePath)).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return featurePath;
        }
    }

    private static String packagePath(String basePath, String featurePath) {
        String relativeFeaturePath = relativeFeaturePath(basePath, featurePath);
        String relativeDirectory = Path.of(relativeFeaturePath).getParent() == null
                ? "."
                : Path.of(relativeFeaturePath).getParent().toString().replace('\\', '/');
        String modulePath = modulePath(basePath);
        if (modulePath == null || ".".equals(relativeDirectory)) {
            return modulePath == null ? relativeDirectory : modulePath;
        }

        return modulePath + "/" + relativeDirectory;
    }

    private static String modulePath(String basePath) {
        try {
            List<String> lines = Files.readAllLines(Path.of(basePath, "go.mod"));
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("module ")) {
                    return trimmed.substring("module ".length()).trim();
                }
            }
        } catch (IOException ignored) {
        }

        return null;
    }

    private static void notifyError(Project project, String message) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("GoDogLand BDD Runner")
                .createNotification("Godog BDD debugger failed", message, NotificationType.ERROR)
                .notify(project);
    }
}
