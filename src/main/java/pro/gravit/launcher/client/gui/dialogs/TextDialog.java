package pro.gravit.launcher.client.gui.dialogs;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.text.Text;
import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;

import java.util.function.Consumer;

public class TextDialog extends AbstractDialog {
    private String header;
    private String text;

    private final Consumer<String> onAccept;
    private final Runnable onClose;

    private Label textHeader;
    private Label textDescription;
    private TextField textField;
    public TextDialog(JavaFXApplication application, String header, String text, Consumer<String> onAccept, Runnable onClose) {
        super("dialogs/text/dialog.fxml", application);
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
    public String getName() {
        return "text";
    }

    @Override
    protected void doInit() throws Exception {
        textHeader = LookupHelper.lookup(layout, "#dialogHeader");
        textDescription = LookupHelper.lookup(layout, "#dialogDescription");
        textField = LookupHelper.lookup(layout, "#dialogInput");
        textHeader.setText(header);
        textDescription.setText(text);
        LookupHelper.<Button>lookup(layout, "#exit").setOnAction((e) -> {
            try {
                close();
            } catch (Throwable throwable) {
                errorHandle(throwable);
            }
            onClose.run();
        });
        LookupHelper.<Button>lookup(layout, "#dialogSend").setOnAction((e) -> {
            try {
                close();
            } catch (Throwable throwable) {
                errorHandle(throwable);
            }
            onAccept.accept(textField.getText());
        });
    }
}
