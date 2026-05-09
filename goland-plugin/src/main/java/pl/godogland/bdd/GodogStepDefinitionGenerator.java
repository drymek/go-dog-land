package pl.godogland.bdd;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.cucumber.psi.GherkinStep;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GodogStepDefinitionGenerator {
    private static final Pattern FEATURE_VAR = Pattern.compile("(\\w+)\\s*:=\\s*&([A-Za-z_][A-Za-z0-9_]*)\\s*\\{\\s*}");
    private static final Pattern METHOD_DECLARATION = Pattern.compile("\\bfunc\\s+(?:\\([^)]*\\)\\s*)?([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");

    private GodogStepDefinitionGenerator() {
    }

    static boolean canHostStepDefinition(@NotNull PsiFile file) {
        return file.getVirtualFile() != null
                && "go".equals(file.getVirtualFile().getExtension())
                && file.getText().contains("godog.ScenarioContext");
    }

    static boolean createStepDefinition(@NotNull GherkinStep step, @NotNull PsiFile file) {
        if (!canHostStepDefinition(file)) {
            return false;
        }

        Document document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
        if (document == null) {
            return false;
        }

        GeneratedStep generatedStep = generate(step.getName(), file.getText());
        WriteCommandAction.runWriteCommandAction(file.getProject(), "Generate Godog step definition", null, () -> {
            String text = document.getText();
            ScenarioInitializer initializer = findScenarioInitializer(text);
            Receiver receiver = initializer == null ? null : findReceiver(text.substring(initializer.bodyStart, initializer.bodyEnd));

            if (initializer == null) {
                document.insertString(document.getTextLength(), initializerText(generatedStep));
            } else {
                String callable = receiver == null ? generatedStep.methodName : receiver.variableName + "." + generatedStep.methodName;
                document.insertString(initializer.bodyEnd, "\n\tctx.Step(`" + generatedStep.regex + "`, " + callable + ")");
            }

            document.insertString(document.getTextLength(), "\n\n" + methodText(generatedStep, receiver));
            PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
            FileDocumentManager.getInstance().saveDocument(document);
        });

        runGoFmt(file);
        return true;
    }

    private static GeneratedStep generate(String stepName, String fileText) {
        List<Argument> arguments = new ArrayList<>();
        StringBuilder regex = new StringBuilder("^");
        StringBuilder methodWords = new StringBuilder();

        for (int i = 0; i < stepName.length(); ) {
            char current = stepName.charAt(i);
            if (current == '"') {
                int end = stepName.indexOf('"', i + 1);
                if (end > i) {
                    arguments.add(new Argument("arg" + (arguments.size() + 1), "string"));
                    regex.append("\"([^\"]*)\"");
                    methodWords.append(" value");
                    i = end + 1;
                    continue;
                }
            }

            if (Character.isDigit(current)) {
                int end = i + 1;
                while (end < stepName.length() && Character.isDigit(stepName.charAt(end))) {
                    end++;
                }
                arguments.add(new Argument("arg" + (arguments.size() + 1), "int"));
                regex.append("(\\d+)");
                methodWords.append(" number");
                i = end;
                continue;
            }

            regex.append(escapeRegexChar(current));
            methodWords.append(Character.isLetterOrDigit(current) ? current : ' ');
            i++;
        }

        regex.append("$");
        String methodName = uniqueMethodName(toLowerCamel(methodWords.toString()), fileText);
        return new GeneratedStep(regex.toString(), methodName, arguments);
    }

    private static ScenarioInitializer findScenarioInitializer(String text) {
        int funcOffset = text.indexOf("func InitializeScenario(");
        if (funcOffset < 0) {
            return null;
        }

        int bodyStart = text.indexOf('{', funcOffset);
        if (bodyStart < 0) {
            return null;
        }

        int bodyEnd = matchingBrace(text, bodyStart);
        return bodyEnd < 0 ? null : new ScenarioInitializer(bodyStart + 1, bodyEnd);
    }

    private static Receiver findReceiver(String initializerBody) {
        Matcher matcher = FEATURE_VAR.matcher(initializerBody);
        if (matcher.find()) {
            return new Receiver(matcher.group(1), matcher.group(2));
        }

        return null;
    }

    private static int matchingBrace(String text, int openBrace) {
        int depth = 0;
        for (int i = openBrace; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }

        return -1;
    }

    private static String initializerText(GeneratedStep generatedStep) {
        return "\n\nfunc InitializeScenario(ctx *godog.ScenarioContext) {" +
                "\n\tctx.Step(`" + generatedStep.regex + "`, " + generatedStep.methodName + ")" +
                "\n}";
    }

    private static String methodText(GeneratedStep generatedStep, Receiver receiver) {
        StringBuilder builder = new StringBuilder("func ");
        if (receiver != null) {
            builder.append("(f *").append(receiver.typeName).append(") ");
        }
        builder.append(generatedStep.methodName).append("(");
        for (int i = 0; i < generatedStep.arguments.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            Argument argument = generatedStep.arguments.get(i);
            builder.append(argument.name).append(" ").append(argument.type);
        }
        builder.append(") error {\n\treturn godog.ErrPending\n}");
        return builder.toString();
    }

    private static String toLowerCamel(String text) {
        String[] parts = text.trim().split("[^A-Za-z0-9]+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }

            String normalized = part.toLowerCase(Locale.ROOT);
            if (builder.isEmpty()) {
                builder.append(normalized);
            } else {
                builder.append(Character.toUpperCase(normalized.charAt(0))).append(normalized.substring(1));
            }
        }

        return builder.isEmpty() ? "generatedStep" : builder.toString();
    }

    private static String uniqueMethodName(String baseName, String fileText) {
        List<String> existingNames = new ArrayList<>();
        Matcher matcher = METHOD_DECLARATION.matcher(fileText);
        while (matcher.find()) {
            existingNames.add(matcher.group(1));
        }

        String candidate = baseName;
        int suffix = 2;
        while (existingNames.contains(candidate)) {
            candidate = baseName + suffix;
            suffix++;
        }

        return candidate;
    }

    private static String escapeRegexChar(char c) {
        if ("\\.[]{}()+-*?^$|".indexOf(c) >= 0) {
            return "\\" + c;
        }

        return String.valueOf(c);
    }

    private static void runGoFmt(PsiFile file) {
        if (file.getVirtualFile() == null) {
            return;
        }

        try {
            Process process = new ProcessBuilder(
                    "/bin/zsh",
                    "-lc",
                    "gofmt -w " + shellQuote(file.getVirtualFile().getPath())
            ).start();
            if (process.waitFor() == 0) {
                LocalFileSystem.getInstance().refreshAndFindFileByPath(file.getVirtualFile().getPath());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private record GeneratedStep(String regex, String methodName, List<Argument> arguments) {
    }

    private record Argument(String name, String type) {
    }

    private record ScenarioInitializer(int bodyStart, int bodyEnd) {
    }

    private record Receiver(String variableName, String typeName) {
    }
}
