package pro.gravit.launcher.gui.scenes.login;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import pro.gravit.launcher.gui.JavaFXApplication;

public class LoginAuthButtonComponent {
    private final JavaFXApplication application;
    private final Button button;
    private AuthButtonState state = AuthButtonState.UNACTIVE;
    private String originalText;

    public enum AuthButtonState {
        ACTIVE("activeButton"), UNACTIVE("unactiveButton"), ERROR("errorButton");
        private final String styleClass;

        public String getStyleClass() {
            return styleClass;
        }

        AuthButtonState(String styleClass) {
            this.styleClass = styleClass;
        }
    }

    public LoginAuthButtonComponent(Button authButton, JavaFXApplication application,
            EventHandler<ActionEvent> eventHandler) {
        this.application = application;
        this.button = authButton;
        this.button.setOnAction(eventHandler);
        this.originalText = button.getText();
    }

    public AuthButtonState getState() {
        return state;
    }

    public void setState(AuthButtonState state) {
        if(state == null) {
            throw new NullPointerException("State can't be null");
        }
        if(state == this.state) {
            return;
        }
        if(this.state != null) {
            button.getStyleClass().remove(this.state.getStyleClass());
        }
        button.getStyleClass().add(state.getStyleClass());
        if(state == AuthButtonState.ERROR) {
            button.setText("ERROR");
        } else if(this.state == AuthButtonState.ERROR) {
            button.setText(originalText);
        }
        this.state = state;
    }

    public String getText() {
        return button.getText();
    }

    public void setText(String text) {
        button.setText(text);
        originalText = text;
    }
}
