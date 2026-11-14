package pro.gravit.launcher.gui.dialogs;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.helper.LookupHelper;

public class ApplyDialog extends AbstractDialog {
    private String header;
    private String text;

    private final Runnable onAccept;
    private final Runnable onDeny;
    private final Runnable onClose;

    private Label textHeader;
    private Label textDescription;

    public ApplyDialog(JavaFXApplication application, String header, String text, Runnable onAccept, Runnable onDeny,
            Runnable onClose) {
        super("dialogs/apply/dialog.fxml", application);
        this.header = header;
        this.text = text;
        this.onAccept = onAccept;
        this.onDeny = onDeny;
        this.onClose = onClose;
    }

    public void setHeader(String header) {
        this.header = header;
        if (isInit()) textHeader.setText(header);
    }

    public void setText(String text) {
        this.text = text;
        if (isInit()) textDescription.setText(text);
    }

    @Override public void dismiss() throws Throwable {
        if (isClosed()) return;
        close();
        onClose.run();
    }

    @Override
    public String getName() {
        return "apply";
    }

    @Override
    protected void doInit() {
        textHeader = LookupHelper.lookup(layout, Label.class, "#headingDialog");
        textDescription = LookupHelper.lookup(layout, Label.class, "#textDialog");
        textHeader.setText(header);
        textDescription.setText(text);
        LookupHelper.lookup(layout, Button.class, "#close").setOnAction((e) -> {
            try {
                close();
            } catch (Throwable throwable) {
                errorHandle(throwable);
            }
            onClose.run();
        });
        LookupHelper.lookup(layout, Button.class, "#apply").setOnAction((e) -> {
            try {
                close();
            } catch (Throwable throwable) {
                errorHandle(throwable);
            }
            onAccept.run();
        });
        LookupHelper.lookup(layout, Button.class, "#deny").setOnAction((e) -> {
            try {
                close();
            } catch (Throwable throwable) {
                errorHandle(throwable);
            }
            onDeny.run();
        });
    }
}
