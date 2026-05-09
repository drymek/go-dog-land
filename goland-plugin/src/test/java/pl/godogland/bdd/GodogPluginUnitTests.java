package pl.godogland.bdd;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GodogPluginUnitTests {
    private static int assertions;

    private GodogPluginUnitTests() {
    }

    public static void main(String[] args) throws Exception {
        run("environment parsing", GodogPluginUnitTests::environmentParsing);
        run("feature step parsing", GodogPluginUnitTests::featureStepParsing);
        run("run target names", GodogPluginUnitTests::runTargetNames);
        run("run result recording", GodogPluginUnitTests::runResultRecording);
        run("step definition generation", GodogPluginUnitTests::stepDefinitionGeneration);
        run("feature scenario normalization", GodogPluginUnitTests::featureScenarioNormalization);
        run("step registration ranges", GodogPluginUnitTests::stepRegistrationRanges);

        System.out.println("Godog plugin unit tests passed (" + assertions + " assertions).");
    }

    private static void environmentParsing() {
        Map<String, String> environment = GodogBddEnvironment.parse("""
                # comments are ignored
                export API_URL="https://example.test"
                TOKEN='abc=123'
                EMPTY=
                SPACED = spaced value
                invalid-line
                =missing-key
                """);

        assertEquals(4, environment.size());
        assertEquals("https://example.test", environment.get("API_URL"));
        assertEquals("abc=123", environment.get("TOKEN"));
        assertEquals("", environment.get("EMPTY"));
        assertEquals("spaced value", environment.get("SPACED"));
        assertTrue(GodogBddEnvironment.parse(null).isEmpty(), "null environment should be empty");
        assertTrue(GodogBddEnvironment.parse("   \n\t").isEmpty(), "blank environment should be empty");
    }

    private static void featureStepParsing() {
        assertEquals("a dog named \"Figa\" has 3 training points",
                GodogFeatureSteps.stepText("  Given a dog named \"Figa\" has 3 training points"));
        assertEquals("the dog receives 4 training points",
                GodogFeatureSteps.stepText("When  the dog receives 4 training points"));
        assertEquals("", GodogFeatureSteps.stepText("Then"));
        assertTrue(GodogFeatureSteps.isStepLine("And the dog should be rewarded"), "And should be a step line");
        assertNull(GodogFeatureSteps.stepText("Scenario: Giving first training points"));
    }

    private static void runTargetNames() {
        GodogBddRunTarget scenario = GodogBddRunTarget.scenario("Giving first training points to a new dog");
        assertEquals(GodogBddRunTarget.Type.SCENARIO, scenario.type());
        assertEquals("Giving_first_training_points_to_a_new_dog", scenario.testName());
        assertEquals("Godog scenario: Giving first training points to a new dog", scenario.title());
        assertEquals("Debug Godog scenario: Giving first training points to a new dog", scenario.debugTitle());
        assertEquals("Run Godog scenario", scenario.tooltip());
        assertEquals("Debug Godog scenario", scenario.debugTooltip());

        GodogBddRunTarget feature = GodogBddRunTarget.feature("Training points");
        assertEquals(GodogBddRunTarget.Type.FEATURE, feature.type());
        assertEquals("Training_points", feature.testName());
        assertEquals("Godog feature: Training points", feature.title());
        assertEquals("Run Godog feature", feature.accessibleName());
    }

    private static void runResultRecording() {
        String featurePath = "features/training_points_" + System.nanoTime() + ".feature";
        GodogBddRunTarget feature = GodogBddRunTarget.feature("Training points");
        GodogBddRunTarget passingScenario = GodogBddRunTarget.scenario("Giving first training points to a new dog");
        GodogBddRunTarget failingScenario = GodogBddRunTarget.scenario("Rewarding a dog after a successful training session");
        String output = """
                --- PASS: TestFeatures/Giving_first_training_points_to_a_new_dog (0.00s)
                --- \u001B[31mFAIL\u001B[0m: TestFeatures/Rewarding_a_dog_after_a_successful_training_session (0.00s)
                """;

        GodogBddRunResults.record(featurePath, feature, 1, output);

        assertEquals(GodogBddRunStatus.FAILED, GodogBddRunResults.statusFor(featurePath, feature));
        assertEquals(GodogBddRunStatus.PASSED, GodogBddRunResults.statusFor(featurePath, passingScenario));
        assertEquals(GodogBddRunStatus.FAILED, GodogBddRunResults.statusFor(featurePath, failingScenario));

        String directPath = "features/direct_" + System.nanoTime() + ".feature";
        GodogBddRunResults.record(directPath, passingScenario, 0, "");
        assertEquals(GodogBddRunStatus.PASSED, GodogBddRunResults.statusFor(directPath, passingScenario));

        GodogBddRunResults.recordScenario(directPath, failingScenario.testName(), GodogBddRunStatus.FAILED);
        assertEquals(GodogBddRunStatus.FAILED, GodogBddRunResults.statusFor(directPath, feature));
    }

    private static void stepDefinitionGeneration() throws Exception {
        Object generated = generateStep("a dog named \"Figa\" has 3 training points", "");
        assertEquals("^a dog named \"([^\"]*)\" has (\\d+) training points$", call(generated, "regex"));
        assertEquals("aDogNamedValueHasNumberTrainingPoints", call(generated, "methodName"));

        List<?> arguments = (List<?>) call(generated, "arguments");
        assertEquals(2, arguments.size());
        assertEquals("arg1", call(arguments.get(0), "name"));
        assertEquals("string", call(arguments.get(0), "type"));
        assertEquals("arg2", call(arguments.get(1), "name"));
        assertEquals("int", call(arguments.get(1), "type"));

        Object generatedWithConflict = generateStep(
                "a dog named \"Figa\" has 3 training points",
                "func aDogNamedValueHasNumberTrainingPoints(arg1 string, arg2 int) error { return nil }"
        );
        assertEquals("aDogNamedValueHasNumberTrainingPoints2", call(generatedWithConflict, "methodName"));
    }

    private static void featureScenarioNormalization() throws Exception {
        Method normalize = GodogFeatureScenarioLocator.class.getDeclaredMethod("normalizeTestName", String.class);
        normalize.setAccessible(true);

        assertEquals("Giving_first_training_points_to_a_new_dog",
                normalize.invoke(null, "TestFeatures/Giving first training points to a new dog"));
        assertEquals("Rewarding_a_dog_after_a_successful_training_session",
                normalize.invoke(null, "  Rewarding   a dog after a successful training session  "));
    }

    private static void stepRegistrationRanges() {
        GodogStepRegistration registration = new GodogStepRegistration(null, "^step$", 10, 20);

        assertTrue(registration.containsOffsetRange(10, 20), "full regex range should be contained");
        assertTrue(registration.containsOffsetRange(12, 18), "inner regex range should be contained");
        assertFalse(registration.containsOffsetRange(9, 18), "range starting before regex should not be contained");
        assertTrue(registration.intersectsOffsetRange(5, 11), "range crossing start should intersect");
        assertFalse(registration.intersectsOffsetRange(20, 25), "range after regex should not intersect");
    }

    private static Object generateStep(String stepName, String fileText) throws Exception {
        Method generate = GodogStepDefinitionGenerator.class.getDeclaredMethod("generate", String.class, String.class);
        generate.setAccessible(true);
        return generate.invoke(null, stepName, fileText);
    }

    private static Object call(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static void run(String name, ThrowingRunnable runnable) throws Exception {
        try {
            runnable.run();
            System.out.println("PASS " + name);
        } catch (Throwable e) {
            throw new AssertionError("FAILED " + name, e);
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        assertions++;
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected <" + expected + "> but got <" + actual + ">");
        }
    }

    private static void assertNull(Object actual) {
        assertions++;
        if (actual != null) {
            throw new AssertionError("Expected <null> but got <" + actual + ">");
        }
    }

    private static void assertTrue(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertions++;
        if (condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
