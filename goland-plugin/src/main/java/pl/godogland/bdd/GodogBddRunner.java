package pl.godogland.bdd;

import com.goide.execution.GoBuildingRunConfiguration;
import com.goide.execution.testing.GoTestFramework;
import com.goide.execution.testing.GoTestRunConfiguration;
import com.goide.execution.testing.GoTestRunConfigurationType;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class GodogBddRunner {
    private GodogBddRunner() {
    }

    static void run(Project project, PsiFile file, GodogBddRunTarget target) {
        try {
            String basePath = project.getBasePath();
            if (basePath == null || file.getVirtualFile() == null) {
                notifyError(project, "Cannot resolve project or feature file path.");
                return;
            }

            saveDocuments(file);

            GoTestRunConfigurationType type = GoTestRunConfigurationType.getInstance();
            ConfigurationFactory factory = type.getFactory();
            RunManager runManager = RunManager.getInstance(project);
            RunnerAndConfigurationSettings settings = runManager.createConfiguration(target.title(), factory);
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
            recordRunResult(project, settings, file.getVirtualFile(), target);
            executeConfiguration(project, settings, DefaultRunExecutor.getRunExecutorInstance());
        } catch (Throwable t) {
            notifyError(project, t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    static void saveDocuments(PsiFile file) {
        Document document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
        if (document != null) {
            FileDocumentManager.getInstance().saveDocument(document);
        }

        FileDocumentManager.getInstance().saveAllDocuments();
    }

    private static void recordRunResult(
            Project project,
            RunnerAndConfigurationSettings settings,
            VirtualFile featureFile,
            GodogBddRunTarget target
    ) {
        MessageBusConnection connection = project.getMessageBus().connect();
        connection.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
            @Override
            public void processStarted(
                    @NotNull String executorId,
                    @NotNull ExecutionEnvironment environment,
                    @NotNull ProcessHandler handler
            ) {
                if (environment.getRunnerAndConfigurationSettings() != settings
                        && environment.getRunProfile() != settings.getConfiguration()
                        && !settings.getName().equals(environment.getRunProfile().getName())) {
                    return;
                }

                connection.disconnect();
                StringBuilder output = new StringBuilder();
                handler.addProcessListener(new ProcessListener() {
                    @Override
                    public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
                        output.append(event.getText());
                    }

                    @Override
                    public void processTerminated(@NotNull ProcessEvent event) {
                        GodogBddRunResults.record(featureFile.getPath(), target, event.getExitCode(), output.toString());
                        GodogBddStatusRefresher.refresh(project, featureFile);
                    }
                });
            }
        });
    }

    private static void executeConfiguration(
            Project project,
            RunnerAndConfigurationSettings settings,
            Executor executor
    ) throws com.intellij.execution.ExecutionException {
        ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.createOrNull(executor, settings);
        if (builder == null) {
            notifyError(project, "Cannot create execution environment.");
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
                .createNotification("Godog BDD runner failed", message, NotificationType.ERROR)
                .notify(project);
    }
}
