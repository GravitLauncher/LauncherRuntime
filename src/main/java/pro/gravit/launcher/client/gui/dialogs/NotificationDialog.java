package pro.gravit.launcher.client.gui.dialogs;

import javafx.scene.text.Text;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;

public class NotificationDialog extends AbstractDialog {
    private String header;
    private String text;

    private Text textHeader;
    private Text textDescription;
    protected NotificationDialog(JavaFXApplication application, String header, String text) {
        super("components/notification.fxml", application);
        this.header = header;
        this.text = text;
    }

    @Override
    protected void doInit() throws Exception {
        textHeader = LookupHelper.lookup(layout, "#notificationHeading");
        textDescription = LookupHelper.lookup(layout, "#notificationText");
        textHeader.setText(header);
        textDescription.setText(text);
    }

    @Override
    public void reset() {
        super.reset();
    }

    public void setHeader(String header) {
        this.header = header;
        if(isInit())
            textHeader.setText(header);
    }

    public void setText(String text) {
        this.text = text;
        if(isInit())
            textDescription.setText(text);
    }
}
