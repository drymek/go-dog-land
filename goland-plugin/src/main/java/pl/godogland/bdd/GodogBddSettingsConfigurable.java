package pl.godogland.bdd;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;

public final class GodogBddSettingsConfigurable implements SearchableConfigurable {
    private final Project project;
    private JTextArea environmentTextArea;

    public GodogBddSettingsConfigurable(Project project) {
        this.project = project;
    }

    @Override
    public @NotNull String getId() {
        return "godogland.bdd.runner";
    }

    @Override
    public String getDisplayName() {
        return "GoDogLand BDD Runner";
    }

    @Override
    public JComponent createComponent() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel label = new JLabel("Environment variables for Run/Debug, one KEY=value per line:");
        environmentTextArea = new JTextArea(10, 60);
        environmentTextArea.setText(GodogBddEnvironment.raw(project));

        panel.add(label, BorderLayout.NORTH);
        panel.add(new JScrollPane(environmentTextArea), BorderLayout.CENTER);
        return panel;
    }

    @Override
    public boolean isModified() {
        return environmentTextArea != null && !environmentTextArea.getText().equals(GodogBddEnvironment.raw(project));
    }

    @Override
    public void apply() {
        if (environmentTextArea != null) {
            GodogBddEnvironment.save(project, environmentTextArea.getText());
        }
    }

    @Override
    public void reset() {
        if (environmentTextArea != null) {
            environmentTextArea.setText(GodogBddEnvironment.raw(project));
        }
    }

    @Override
    public void disposeUIResources() {
        environmentTextArea = null;
    }
}
