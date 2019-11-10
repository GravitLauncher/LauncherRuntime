package pro.gravit.launcher.client;

import pro.gravit.launcher.client.events.ClientPreGuiPhase;
import pro.gravit.launcher.gui.RuntimeProvider;
import pro.gravit.launcher.modules.LauncherInitContext;
import pro.gravit.launcher.modules.LauncherModule;
import pro.gravit.launcher.modules.LauncherModuleInfo;
import pro.gravit.utils.Version;

public class JavaRuntimeModule extends LauncherModule {
    public RuntimeProvider provider;
    public JavaRuntimeModule() {
        super(new LauncherModuleInfo("StdJavaRuntime", new Version(1,0,0),
                0, new String[]{}, new String[] {"runtime"}));
    }

    @Override
    public void init(LauncherInitContext initContext) {
        registerEvent(this::preGuiPhase, ClientPreGuiPhase.class);
    }
    public void preGuiPhase(ClientPreGuiPhase phase)
    {
        provider = new StdJavaRuntimeProvider();
        phase.runtimeProvider = provider;
    }
}
