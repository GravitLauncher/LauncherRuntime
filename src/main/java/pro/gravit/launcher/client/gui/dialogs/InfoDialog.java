package pro.gravit.launcher.client.gui.dialogs;

import javafx.scene.control.Button;
import javafx.scene.text.Text;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;

public class InfoDialog extends AbstractDialog {
    private String header;
    private String text;

    private final Runnable onAccept;
    private final Runnable onClose;

    private Text textHeader;
    private Text textDescription;
    public InfoDialog(JavaFXApplication application, String header, String text, Runnable onAccept, Runnable onClose) {
        super("dialogs/info/dialog.fxml", application);
        this.header = header;
        this.text = text;
        this.onAccept = onAccept;
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
        //textDescription = LookupHelper.lookup(layout, "#textDialog");
        textHeader.setText(header);
        //textDescription.setText(text);
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
