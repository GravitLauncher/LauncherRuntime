package pro.gravit.launcher.gui.scenes.debug;

import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import pro.gravit.launcher.gui.JavaRuntimeModule;
import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.scenes.AbstractScene;
import pro.gravit.launcher.gui.service.LaunchService;
import pro.gravit.utils.helper.LogHelper;

import java.io.*;

public class DebugScene extends AbstractScene {
    private ProcessLogOutput processLogOutput;
    private LaunchService.ClientInstance clientInstance;

    public DebugScene(JavaFXApplication application) {
        super("scenes/debug/debug.fxml", application);
        this.isResetOnShow = true;
    }

    @Override
    protected void doInit() {
        processLogOutput = new ProcessLogOutput(LookupHelper.lookup(layout, "#output"));
        LookupHelper.<ButtonBase>lookupIfPossible(header, "#controls", "#kill").ifPresent((x) -> x.setOnAction((e) -> {
            if(clientInstance != null) clientInstance.kill();
        }));

        LookupHelper.<Label>lookupIfPossible(layout, "#version")
                    .ifPresent((v) -> v.setText(JavaRuntimeModule.getMiniLauncherInfo()));
        LookupHelper.<ButtonBase>lookupIfPossible(header, "#controls", "#copy").ifPresent((x) -> x.setOnAction((e) -> processLogOutput.copyToClipboard()));
        LookupHelper.<ButtonBase>lookup(header, "#back").setOnAction((e) -> {
            if(clientInstance != null) {
                clientInstance.unregisterListener(processLogOutput);
            }
            try {
                switchToBackScene();
            } catch (Exception ex) {
                errorHandle(ex);
            }
        });
    }


    @Override
    public void reset() {
        processLogOutput.clear();
    }

    public void onClientInstance(LaunchService.ClientInstance clientInstance) {
        this.clientInstance = clientInstance;
        this.clientInstance.registerListener(processLogOutput);
        this.clientInstance.getOnWriteParamsFuture().thenAccept((ok) -> processLogOutput.append("[START] Write param successful\n")).exceptionally((e) -> {
            errorHandle(e);
            return null;
        });
        this.clientInstance.start().thenAccept((code) -> processLogOutput.append(String.format("[START] Process exit with code %d", code))).exceptionally((e) -> {
            errorHandle(e);
            return null;
        });
    }

    public void append(String text) {
        processLogOutput.append(text);
    }

    @Override
    public void errorHandle(Throwable e) {
        if (!(e instanceof EOFException)) {
            if (LogHelper.isDebugEnabled()) processLogOutput.append(e.toString());
        }
    }

    @Override
    public String getName() {
        return "debug";
    }
}
