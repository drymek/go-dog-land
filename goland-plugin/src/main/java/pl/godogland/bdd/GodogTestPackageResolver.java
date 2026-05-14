package pl.godogland.bdd;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

final class GodogTestPackageResolver {
    private GodogTestPackageResolver() {
    }

    static @NotNull String packagePath(@NotNull String basePath, @NotNull String featurePath) {
        Path root = executionRoot(basePath, featurePath);
        Path testDirectory = testDirectory(basePath, featurePath);
        return packagePath(root, testDirectory);
    }

    static @NotNull String testDirectoryPath(@NotNull String basePath, @NotNull String featurePath) {
        return testDirectory(basePath, featurePath).toString();
    }

    static @NotNull String executionRootDirectory(@NotNull String basePath, @NotNull String featurePath) {
        return executionRoot(basePath, featurePath).toString();
    }

    static @NotNull String relativeFeaturePath(@NotNull String basePath, @NotNull String featurePath) {
        Path root = executionRoot(basePath, featurePath);
        try {
            return root.relativize(Path.of(featurePath).normalize()).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return featurePath;
        }
    }

    private static @NotNull Path executionRoot(@NotNull String basePath, @NotNull String featurePath) {
        Path feature = Path.of(featurePath).normalize();
        Path directory = testDirectory(basePath, featurePath);
        Path moduleRoot = findModuleRoot(Path.of(basePath).normalize(), directory);
        return moduleRoot == null ? fallbackDirectory(feature, Path.of(basePath).normalize()) : moduleRoot;
    }

    private static @NotNull Path testDirectory(@NotNull String basePath, @NotNull String featurePath) {
        Path base = Path.of(basePath).normalize();
        Path feature = Path.of(featurePath).normalize();
        Path featureDirectory = Files.isDirectory(feature) ? feature : feature.getParent();
        Path testDirectory = findGodogTestDirectory(base, featureDirectory);
        return testDirectory == null ? fallbackDirectory(feature, base) : testDirectory;
    }

    private static @NotNull String packagePath(@NotNull Path root, @NotNull Path testDirectory) {
        try {
            Path relative = root.relativize(testDirectory).normalize();
            String relativePath = relative.toString().replace('\\', '/');
            if (relativePath.isEmpty() || ".".equals(relativePath)) {
                return ".";
            }

            return "./" + relativePath;
        } catch (IllegalArgumentException ignored) {
            return ".";
        }
    }

    private static @NotNull Path fallbackDirectory(Path feature, Path base) {
        Path featureDirectory = Files.isDirectory(feature) ? feature : feature.getParent();
        return featureDirectory == null ? base : featureDirectory;
    }

    private static Path findModuleRoot(Path base, Path startDirectory) {
        Path current = startDirectory;
        while (current != null && current.normalize().startsWith(base)) {
            if (Files.isRegularFile(current.resolve("go.mod"))) {
                return current;
            }

            if (current.equals(base)) {
                break;
            }
            current = current.getParent();
        }

        return Files.isRegularFile(base.resolve("go.mod")) ? base : null;
    }

    private static Path findGodogTestDirectory(Path base, Path startDirectory) {
        Path current = startDirectory;
        while (current != null && current.normalize().startsWith(base)) {
            if (hasGodogTestFile(current)) {
                return current;
            }

            if (current.equals(base)) {
                break;
            }
            current = current.getParent();
        }

        return null;
    }

    private static boolean hasGodogTestFile(Path directory) {
        if (!Files.isDirectory(directory)) {
            return false;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*_test.go")) {
            for (Path file : stream) {
                if (isGodogTestFile(file)) {
                    return true;
                }
            }
        } catch (IOException ignored) {
        }

        return false;
    }

    private static boolean isGodogTestFile(Path file) {
        try {
            String text = Files.readString(file);
            return text.contains("godog.ScenarioContext")
                    || text.contains("godog.TestSuite")
                    || text.contains("InitializeScenario(");
        } catch (IOException ignored) {
            return false;
        }
    }
}
