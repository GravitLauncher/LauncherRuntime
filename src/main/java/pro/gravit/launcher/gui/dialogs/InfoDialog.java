package pro.gravit.launcher.gui.dialogs;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.helper.LookupHelper;

public class InfoDialog extends AbstractDialog {
    private String header;
    private String text;

    private final Runnable onAccept;
    private final Runnable onClose;

    private Label textHeader;
    private Label textDescription;

    public InfoDialog(JavaFXApplication application, String header, String text, Runnable onAccept, Runnable onClose) {
        super("dialogs/info/dialog.fxml", application);
        this.header = header;
        this.text = text;
        this.onAccept = onAccept;
        this.onClose = onClose;
    }

    public void setHeader(String header) {
        this.header = header;
        if (isInit()) textDescription.setText(text);
    }

    public void setText(String text) {
        this.text = text;
        if (isInit()) textHeader.setText(header);
    }

    @Override
    public String getName() {
        return "info";
    }

    @Override
    protected void doInit() {
        textHeader = LookupHelper.lookup(layout, "#headingDialog");
        textDescription = LookupHelper.lookup(layout, "#textDialog");
        textHeader.setText(header);
        textDescription.setText(text);
        LookupHelper.<Button>lookup(layout, "#close").setOnAction((e) -> {
            try {
                close();
            } catch (Throwable throwable) {
                errorHandle(throwable);
            }
            onClose.run();
        });
        LookupHelper.<Button>lookup(layout, "#apply").setOnAction((e) -> {
            try {
                close();
            } catch (Throwable throwable) {
                errorHandle(throwable);
            }
            onAccept.run();
        });
    }
}
