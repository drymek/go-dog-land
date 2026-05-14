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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

final class GodogBddRunner {
    private static final Logger LOG = Logger.getInstance(GodogBddRunner.class);

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
            String featurePath = file.getVirtualFile().getPath();
            String executionRoot = GodogTestPackageResolver.executionRootDirectory(basePath, featurePath);
            String testDirectory = GodogTestPackageResolver.testDirectoryPath(basePath, featurePath);
            String packagePath = GodogTestPackageResolver.packagePath(basePath, featurePath);
            Module module = ModuleUtilCore.findModuleForFile(file.getVirtualFile(), project);

            GoTestRunConfigurationType type = GoTestRunConfigurationType.getInstance();
            ConfigurationFactory factory = type.getFactory();
            RunManager runManager = RunManager.getInstance(project);
            RunnerAndConfigurationSettings settings = runManager.createConfiguration(target.title(), factory);
            GoTestRunConfiguration configuration = (GoTestRunConfiguration) settings.getConfiguration();
            if (module != null) {
                configuration.getConfigurationModule().setModule(module);
            }

            configuration.setKind(GoBuildingRunConfiguration.Kind.DIRECTORY);
            configuration.setRootDirectory(executionRoot);
            configuration.setDirectoryPath(testDirectory);
            configuration.setWorkingDirectory(executionRoot);
            configuration.setTestFramework(GoTestFramework.fromName("gotest"));
            configuration.setPassParentEnvironment(true);

            Map<String, String> env = new HashMap<>(GodogBddEnvironment.userEnvironment(project));
            env.put("GODOGLAND_BDD_FEATURE", featurePath);
            String pattern = null;
            if (target.type() == GodogBddRunTarget.Type.SCENARIO) {
                pattern = "^TestFeatures$/" + Pattern.quote(target.testName()) + "$";
                configuration.setPattern(pattern);
                env.put("GODOGLAND_BDD_SCENARIO", pattern);
            }
            configuration.setCustomEnvironment(env);

            LOG.info("GoDogLand run config: name=" + target.title()
                    + ", feature=" + featurePath
                    + ", kind=DIRECTORY"
                    + ", executionRoot=" + executionRoot
                    + ", testDirectory=" + testDirectory
                    + ", package=" + packagePath
                    + ", workingDirectory=" + executionRoot
                    + ", pattern=" + (pattern == null ? "<feature>" : pattern)
                    + ", module=" + (module == null ? "<none>" : module.getName()));

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

    private static void notifyError(Project project, String message) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("GoDogLand BDD Runner")
                .createNotification("Godog BDD runner failed", message, NotificationType.ERROR)
                .notify(project);
    }
}
