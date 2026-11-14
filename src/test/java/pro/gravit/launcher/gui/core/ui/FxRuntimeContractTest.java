package pro.gravit.launcher.gui.core.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.Node;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxRuntimeContractTest {
    private static final List<Path> FXML_FILES = findFxmlFiles();
    private static final List<String> LOCALES = List.of("be", "en", "pl", "ru", "uk");

    @Test
    void everyRuntimeFxmlLoadsAndAppliesCssForEveryLocale() throws Exception {
        for (String locale : LOCALES) {
            ResourceBundle resources = resourceBundle(locale);
            for (Path file : FXML_FILES) {
                FxTestThread.run(() -> {
                    try {
                        FXMLLoader loader = new FXMLLoader(file.toUri().toURL());
                        loader.setResources(resources);
                        Node loaded = loader.load();
                        Parent root = assertInstanceOf(Parent.class, loaded, file.toString());
                        Scene scene = new Scene(root);
                        scene.getStylesheets().add(Path.of("runtime/styles/variables.css").toUri().toString());
                        scene.getStylesheets().add(Path.of("runtime/styles/global.css").toUri().toString());
                        root.applyCss();
                    } catch (Exception failure) {
                        throw new AssertionError("FXML load failed for " + file + " using locale " + locale, failure);
                    }
                });
            }
        }
    }

    @Test
    void uiActionIgnoresDuplicateExecuteAndRecoversAfterAsyncFailure() throws Exception {
        CompletableFuture<Void> pending = new CompletableFuture<>();
        AtomicInteger attempts = new AtomicInteger();
        List<Throwable> errors = new ArrayList<>();
        UiAction action = FxTestThread.call(() -> new UiAction("Run", () -> {
            return attempts.incrementAndGet() == 1 ? pending : CompletableFuture.completedFuture(null);
        }, errors::add));

        FxTestThread.run(() -> {
            action.execute();
            action.execute();
            assertTrue(action.runningProperty().get());
            assertEquals(1, attempts.get());
        });
        pending.completeExceptionally(new IllegalStateException("expected failure"));
        FxTestThread.run(() -> {
            assertFalse(action.runningProperty().get());
            assertEquals(1, errors.size());
            assertEquals("expected failure", errors.getFirst().getMessage());
            action.execute();
            assertFalse(action.runningProperty().get());
            assertEquals(2, attempts.get());
        });
    }

    @Test
    void uiActionAttachmentBindingsAndListenerAreRemovedByScope() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        CompletableFuture<Void> pending = new CompletableFuture<>();
        FxTestThread.run(() -> {
            UiScope scope = new UiScope();
            Button button = new Button();
            UiAction action = new UiAction("Start", () -> {
                attempts.incrementAndGet();
                return pending;
            }, error -> { throw new AssertionError(error); });
            action.attach(scope, button);
            assertEquals("Start", button.getText());
            action.availableProperty().set(false);
            assertTrue(button.isDisabled());
            action.availableProperty().set(true);
            assertFalse(button.isDisabled());
            button.fire();
            assertTrue(button.isDisabled());
            scope.close();
            assertEquals("Start", button.getText());
            action.textProperty().set("Changed after detach");
            assertEquals("Start", button.getText());
            button.setDisable(false);
            button.fire();
            assertEquals(1, attempts.get());
        });
        pending.complete(null);
    }

    private static List<Path> findFxmlFiles() {
        try (Stream<Path> paths = Files.walk(Path.of("runtime"))) {
            return paths.filter(path -> path.toString().endsWith(".fxml")).sorted().toList();
        } catch (Exception error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static ResourceBundle resourceBundle(String locale) throws Exception {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(Path.of("runtime/runtime_" + locale + ".properties"))) {
            properties.load(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8));
        }
        return new ResourceBundle() {
            @Override protected Object handleGetObject(String key) { return properties.getProperty(key); }
            @Override public boolean containsKey(String key) { return properties.containsKey(key); }
            @Override public java.util.Enumeration<String> getKeys() {
                return java.util.Collections.enumeration(properties.stringPropertyNames());
            }
        };
    }
}
