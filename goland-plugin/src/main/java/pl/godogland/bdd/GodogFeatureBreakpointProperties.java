package pl.godogland.bdd;

import com.intellij.xdebugger.breakpoints.XBreakpointProperties;

public class GodogFeatureBreakpointProperties extends XBreakpointProperties<GodogFeatureBreakpointProperties> {
    @Override
    public GodogFeatureBreakpointProperties getState() {
        return this;
    }

    @Override
    public void loadState(GodogFeatureBreakpointProperties state) {
    }
}
