package pl.godogland.bdd;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GodogBddRunResults {
    private static final Pattern SUBTEST_RESULT = Pattern.compile("---\\s+(PASS|FAIL):\\s+TestFeatures/([^\\s]+)");
    private static final Pattern ANSI_ESCAPE = Pattern.compile("\\u001B\\[[;\\d]*m");
    private static final Map<String, FileResults> RESULTS = new ConcurrentHashMap<>();

    private GodogBddRunResults() {
    }

    static GodogBddRunStatus statusFor(String featurePath, GodogBddRunTarget target) {
        FileResults results = RESULTS.get(featurePath);
        if (results == null) {
            return null;
        }

        if (target.type() == GodogBddRunTarget.Type.FEATURE) {
            return results.featureStatus;
        }

        return results.scenarioStatuses.get(target.testName());
    }

    static void record(String featurePath, GodogBddRunTarget target, int exitCode, String output) {
        FileResults results = RESULTS.computeIfAbsent(featurePath, ignored -> new FileResults());
        GodogBddRunStatus targetStatus = exitCode == 0 ? GodogBddRunStatus.PASSED : GodogBddRunStatus.FAILED;

        if (target.type() == GodogBddRunTarget.Type.FEATURE) {
            results.featureStatus = targetStatus;
            recordScenarioStatuses(results, output);
        } else {
            results.scenarioStatuses.put(target.testName(), targetStatus);
        }
    }

    static void recordScenario(String featurePath, String scenarioTestName, GodogBddRunStatus status) {
        FileResults results = RESULTS.computeIfAbsent(featurePath, ignored -> new FileResults());
        results.scenarioStatuses.put(scenarioTestName, status);
        if (status == GodogBddRunStatus.FAILED) {
            results.featureStatus = GodogBddRunStatus.FAILED;
        } else if (results.featureStatus != GodogBddRunStatus.FAILED) {
            results.featureStatus = GodogBddRunStatus.PASSED;
        }
    }

    private static void recordScenarioStatuses(FileResults results, String output) {
        Matcher matcher = SUBTEST_RESULT.matcher(ANSI_ESCAPE.matcher(output).replaceAll(""));
        while (matcher.find()) {
            GodogBddRunStatus status = "PASS".equals(matcher.group(1))
                    ? GodogBddRunStatus.PASSED
                    : GodogBddRunStatus.FAILED;
            results.scenarioStatuses.put(matcher.group(2), status);
        }
    }

    private static final class FileResults {
        private volatile GodogBddRunStatus featureStatus;
        private final Map<String, GodogBddRunStatus> scenarioStatuses = new ConcurrentHashMap<>();
    }
}
