package pro.gravit.launcher.gui;

import javafx.application.Application;
import pro.gravit.launcher.gui.basic.FXApplication;
import pro.gravit.launcher.runtime.LauncherEngine;
import pro.gravit.launcher.runtime.gui.RuntimeProvider;
import pro.gravit.launcher.runtime.utils.LauncherUpdater;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

public class StdJavaRuntimeProvider implements RuntimeProvider {
    public static volatile Path updatePath;
    private static final AtomicReference<StdJavaRuntimeProvider> INSTANCE = new AtomicReference<>();

    public StdJavaRuntimeProvider() {
        INSTANCE.set(this);
    }

    public static StdJavaRuntimeProvider getInstance() {
        return INSTANCE.get();
    }

    public FXApplication getApplication() {
        return FXApplication.getInstance();
    }

    @Override
    public void run(String[] args) {
        LogHelper.debug("Start JavaFX Application");
        Application.launch(FXApplication.class, args);
        LogHelper.debug("Post Application.launch method invoked");
        if (updatePath != null) {
            LauncherUpdater.nothing();
            LauncherEngine.beforeExit(0);
            Path target = IOHelper.getCodeSource(LauncherUpdater.class);
            try {
                try (InputStream input = IOHelper.newInput(updatePath)) {
                    try (OutputStream output = IOHelper.newOutput(target)) {
                        IOHelper.transfer(input, output);
                    }
                }
                Files.deleteIfExists(updatePath);
            } catch (IOException e) {
                LogHelper.error(e);
                LauncherEngine.forceExit(-109);
            }
            LauncherUpdater.restart();
        }
    }

    @Override
    public void preLoad() {
    }

    protected void registerPrivateCommands() {
    }

    @Override
    public void init(boolean clientInstance) {
    }
}
