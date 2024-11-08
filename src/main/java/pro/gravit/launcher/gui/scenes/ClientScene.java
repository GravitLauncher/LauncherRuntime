package pro.gravit.launcher.gui.scenes;

import javafx.scene.control.TextArea;
import pro.gravit.launcher.core.backend.LauncherBackendAPI;
import pro.gravit.launcher.gui.basic.FxThreadExecutor;
import pro.gravit.launcher.gui.basic.ResourcePath;
import pro.gravit.launcher.gui.basic.Scenes;
import pro.gravit.launcher.gui.utils.LimitedBuffer;
import pro.gravit.launcher.gui.utils.OneTimeAction;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;


@ResourcePath("scenes/debug/debug.fxml")
public class ClientScene extends FxScene {
    public static final int MAX_BUFFER_SIZE = 1024*1024*2;
    public static final int MAX_OUTPUT_SIZE = 1024*1024*4;
    public static final int DELETE_OUTPUT_SIZE = 1024*256;
    private TextArea output;
    private Runnable terminateAction;
    private LimitedBuffer buffer;
    private final OneTimeAction action = new OneTimeAction();

    @Override
    protected void doInit() {
        output = lookup("#output");
        buffer = new LimitedBuffer(MAX_BUFFER_SIZE);
    }

    public void runClient(LauncherBackendAPI.ReadyProfile profile) {
        try {
            profile.run(new RunCallbackImpl());
        } catch (Exception e) {
            errorHandle(e);
        }
    }

    @Override
    protected void errorHandle(Throwable e) {
        super.errorHandle(e);
        CompletableFuture.runAsync(() -> {
            output.appendText(String.format("[LAUNCHER] Exception %s\n", e));
        }, FxThreadExecutor.getInstance());
    }

    private class RunCallbackImpl extends LauncherBackendAPI.RunCallback {

        @Override
        public void onStarted() {
            CompletableFuture.runAsync(() -> {
                output.appendText("[LAUNCHER] Process started\n");
            }, FxThreadExecutor.getInstance());
        }

        @Override
        public void onCanTerminate(Runnable terminate) {
            CompletableFuture.runAsync(() -> {
                terminateAction = terminate;
            }, FxThreadExecutor.getInstance());
        }

        @Override
        public void onFinished(int code) {
            CompletableFuture.runAsync(() -> {
                output.appendText(String.format("[LAUNCHER] Exit code %d\n", code));
            }, FxThreadExecutor.getInstance());
        }

        @Override
        public void onNormalOutput(byte[] buf, int offset, int size) {
            buffer.put(buf, offset, size);
            update();
        }

        @Override
        public void onErrorOutput(byte[] buf, int offset, int size) {
            buffer.put(buf, offset, size);
            update();
        }

        public void update() {
            action.run(() -> {
                AtomicReference<String> stringRef = new AtomicReference<>();
                buffer.accessAndClear(((buf, off, len) -> {
                    stringRef.set(new String(buf, off, len));
                }));
                if(output.lengthProperty().get() > MAX_OUTPUT_SIZE) {
                    output.deleteText(0, Math.max(DELETE_OUTPUT_SIZE, stringRef.get().length()));
                }
                output.appendText(stringRef.get());
            }, FxThreadExecutor.getInstance());
        }
    }
}
