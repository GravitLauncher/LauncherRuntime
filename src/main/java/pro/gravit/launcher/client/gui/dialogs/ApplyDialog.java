package pro.gravit.launcher.client.gui.dialogs;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.text.Text;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;

public class ApplyDialog extends AbstractDialog {
    private String header;
    private String text;

    private final Runnable onAccept;
    private final Runnable onDeny;
    private final Runnable onClose;

    private Label textHeader;
    private Label textDescription;
    public ApplyDialog(JavaFXApplication application, String header, String text, Runnable onAccept, Runnable onDeny, Runnable onClose) {
        super("dialogs/apply/dialog.fxml", application);
        this.header = header;
        this.text = text;
        this.onAccept = onAccept;
        this.onDeny = onDeny;
        this.onClose = onClose;
    }

    public void setHeader(String header) {
        this.header = header;
        if(isInit())
            textDescription.setText(text);
    }

    public void setText(String text) {
        this.text = text;
        if(isInit())
            textHeader.setText(header);
    }

    @Override
    protected void doInit() throws Exception {
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
        LookupHelper.<Button>lookup(layout, "#deny").setOnAction((e) -> {
            try {
                close();
            } catch (Throwable throwable) {
                errorHandle(throwable);
            }
            onDeny.run();
        });
    }
}
