package ru.fazbear.campusdirectory.ui;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import ru.fazbear.campusdirectory.model.Contact;
import ru.fazbear.campusdirectory.model.PersonalContact;
import ru.fazbear.campusdirectory.ContactDirectoryApp;
import ru.fazbear.campusdirectory.service.ContactDirectory;
import ru.fazbear.campusdirectory.service.ContactGenerator;
import ru.fazbear.campusdirectory.trie.SynchronizedTrie;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@EnabledIfEnvironmentVariable(named = "CAMPUS_GUI_TESTS", matches = "true")
class TrieCanvasViewTest {
    private static Stage stage;

    @Test
    void exactSearchReplacesEarlierPrefixResultsAndEnterRunsAutocomplete() throws Exception {
        onFx(() -> {
            var app = new ContactDirectoryApp();
            app.start(stage);
            var directory = (ContactDirectory) field(app, "directory");
            var anna = new PersonalContact("Anna", "123", "", "");
            var annabel = new PersonalContact("Annabel", "456", "", "");
            var zoe = new PersonalContact("Zoe", "789", "", "");
            directory.add(anna);
            directory.add(annabel);
            directory.add(zoe);
            var input = (TextField) field(app, "prefixField");
            var table = (TableView<?>) field(app, "table");
            input.setText("Ann");
            input.fireEvent(new javafx.event.ActionEvent());
            assertEquals(java.util.List.of(anna, annabel), table.getItems());
            input.setText("Missing");
            invoke(app, "findContact");
            assertTrue(table.getItems().isEmpty());
            assertTrue(table.getSelectionModel().isEmpty());
            input.setText("Zoe");
            invoke(app, "findContact");
            assertEquals(java.util.List.of(zoe), table.getItems());
            assertSame(zoe, table.getSelectionModel().getSelectedItem());
            input.setText("Anna");
            input.fireEvent(new javafx.event.ActionEvent());
            assertEquals(java.util.List.of(anna, annabel), table.getItems());
            invoke(app, "findContact");
            assertEquals(java.util.List.of(anna), table.getItems());
            invoke(app, "refreshAll");
            assertEquals(3, table.getItems().size());
            return null;
        });
    }

    @Test
    void csvChooserStartsInSamplesAndRemembersAnExistingDirectory() throws Exception {
        onFx(() -> {
            var app = new ContactDirectoryApp();
            var method = ContactDirectoryApp.class.getDeclaredMethod("csvChooser", String.class);
            method.setAccessible(true);
            var chooser = (javafx.stage.FileChooser) method.invoke(app, "CSV");
            assertEquals(new java.io.File("samples").getCanonicalFile(),
                    chooser.getInitialDirectory().getCanonicalFile());
            var remembered = ContactDirectoryApp.class.getDeclaredField("csvDirectory");
            remembered.setAccessible(true);
            remembered.set(app, new java.io.File("src").getAbsoluteFile());
            chooser = (javafx.stage.FileChooser) method.invoke(app, "CSV");
            assertEquals(new java.io.File("src").getCanonicalFile(),
                    chooser.getInitialDirectory().getCanonicalFile());
            remembered.set(app, new java.io.File("missing-directory-for-test"));
            chooser = (javafx.stage.FileChooser) method.invoke(app, "CSV");
            assertTrue(chooser.getInitialDirectory().isDirectory());
            return null;
        });
    }

    @Test
    void comparisonRunsInBackgroundAndShowsBothStoresAndBothSizes() throws Exception {
        var app = onFx(() -> {
            var result = new ContactDirectoryApp();
            result.start(stage);
            invoke(result, "runBenchmarks");
            assertTrue(field(result, "activeTask") != null);
            assertTrue(((javafx.scene.Node) field(result, "dataActions")).isDisabled());
            return result;
        });
        String text = waitForResultDialog("Замеры Trie");
        assertTrue(text.contains("Trie"));
        assertTrue(text.contains("TreeMap"));
        assertTrue(text.replaceAll("[^0-9\\n]", "").contains("100000"));
        assertTrue(onFx(() -> field(app, "activeTask") == null));
    }

    @Test
    void concurrentExperimentShowsResultsAndKeepsDirectoryUntouched() throws Exception {
        var app = onFx(() -> {
            var result = new ContactDirectoryApp();
            result.start(stage);
            ((ContactDirectory) field(result, "directory")).add(new PersonalContact("Keep", "123", "", ""));
            invoke(result, "runConcurrencyExperiment");
            return result;
        });
        String text = waitForResultDialog("Конкурентная запись");
        assertTrue(text.contains("Без синхронизации"));
        assertTrue(text.contains("С synchronized"));
        assertTrue(text.contains("16000"));
        assertEquals(1, onFx(() -> ((ContactDirectory) field(app, "directory")).size()));
    }

    private static String waitForResultDialog(String title) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            String text = onFx(() -> {
                for (var window : java.util.List.copyOf(javafx.stage.Window.getWindows())) {
                    if (window instanceof Stage dialog && title.equals(dialog.getTitle())
                            && dialog.getScene().getRoot() instanceof javafx.scene.control.DialogPane pane) {
                        try {
                            return ((javafx.scene.control.TextArea) pane.getContent()).getText();
                        } finally {
                            ((javafx.scene.control.Button) pane.lookupButton(javafx.scene.control.ButtonType.OK)).fire();
                        }
                    }
                }
                return null;
            });
            if (text != null) { return text; }
            Thread.sleep(10);
        }
        throw new AssertionError("Не появилось окно результата: " + title);
    }

    @Test
    void wideViewportIsFullyCoveredWithoutOversizedTexture() throws Exception {
        onFx(() -> {
            var trie = new SynchronizedTrie<Contact>(Contact::getName, Contact[]::new);
            for (var contact : new ContactGenerator().generate(1000)) {
                trie.add(contact);
            }
            var view = new TrieCanvasView();
            new Scene(view);
            view.setSnapshot(trie.snapshot(500));
            view.resize(3200, 900);
            view.applyCss();
            view.layout();
            var scroll = (ScrollPane) view.getChildren().get(1);
            var canvas = (Canvas) ((Pane) scroll.getContent()).getChildren().getFirst();
            assertTrue(scroll.getViewportBounds().getWidth() > 3000);
            assertTrue(canvas.getWidth() <= 2048);
            assertEquals(scroll.getViewportBounds().getWidth(), canvas.getBoundsInParent().getWidth(), 0.1);
            assertDrawn(canvas);
            return null;
        });
    }

    @Test
    void windowCannotCloseWhileBackgroundOperationIsActive() throws Exception {
        onFx(() -> {
            var app = new ContactDirectoryApp();
            app.start(stage);
            var active = ContactDirectoryApp.class.getDeclaredField("activeTask");
            active.setAccessible(true);
            active.set(app, new javafx.concurrent.Task<Void>() {
                @Override protected Void call() { return null; }
            });
            var event = new javafx.stage.WindowEvent(stage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST);
            stage.getOnCloseRequest().handle(event);
            assertTrue(event.isConsumed());
            assertTrue(stage.isShowing());
            active.set(app, null);
            var idleEvent = new javafx.stage.WindowEvent(stage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST);
            stage.getOnCloseRequest().handle(idleEvent);
            assertTrue(!idleEvent.isConsumed());
            return null;
        });
    }

    @Test
    void refreshAndSearchKeepTheSelectedTableSortOrder() throws Exception {
        onFx(() -> {
            var app = new ContactDirectoryApp();
            app.start(stage);
            var directory = (ContactDirectory) field(app, "directory");
            directory.add(new PersonalContact("Anna", "123", "", ""));
            directory.add(new PersonalContact("Zoe", "456", "", ""));
            invoke(app, "refreshAll");
            var table = (TableView<?>) field(app, "table");
            sortByNameDescending(table);
            invoke(app, "refreshAll");
            assertEquals("Zoe", ((PersonalContact) table.getItems().getFirst()).getName());
            ((TextField) field(app, "prefixField")).setText("");
            invoke(app, "autocomplete");
            assertEquals("Zoe", ((PersonalContact) table.getItems().getFirst()).getName());
            ((TextField) field(app, "prefixField")).setText("Anna");
            invoke(app, "findContact");
            assertEquals(1, table.getItems().size());
            assertEquals("Anna", ((PersonalContact) table.getItems().getFirst()).getName());
            invoke(app, "refreshAll");
            assertEquals("Zoe", ((PersonalContact) table.getItems().getFirst()).getName());
            return null;
        });
    }

    @Test
    void selectingEqualContactKeepsItsIdentity() throws Exception {
        onFx(() -> {
            var app = new ContactDirectoryApp();
            app.start(stage);
            var directory = (ContactDirectory) field(app, "directory");
            var first = new PersonalContact("Anna", "123", "", "");
            var second = new PersonalContact("Anna", "123", "", "");
            directory.add(first);
            directory.add(second);
            invoke(app, "refreshAll");
            var method = ContactDirectoryApp.class.getDeclaredMethod("selectContact",
                    ru.fazbear.campusdirectory.model.Contact.class);
            method.setAccessible(true);
            method.invoke(app, second);
            assertSame(second, ((TableView<?>) field(app, "table")).getSelectionModel().getSelectedItem());
            return null;
        });
    }

    private static <T> void sortByNameDescending(TableView<T> table) {
        TableColumn<T, ?> name = table.getColumns().get(1);
        name.setSortType(TableColumn.SortType.DESCENDING);
        table.getSortOrder().add(name);
        table.sort();
    }

    private static Object field(ContactDirectoryApp app, String name) throws Exception {
        var field = ContactDirectoryApp.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(app);
    }

    private static void invoke(ContactDirectoryApp app, String name) throws Exception {
        var method = ContactDirectoryApp.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(app);
    }

    @BeforeAll
    static void startJavaFx() throws Exception {
        var ready = new CountDownLatch(1);
        Platform.startup(ready::countDown);
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        onFx(() -> { stage = new Stage(); return null; });
    }

    @AfterAll
    static void stopJavaFx() throws Exception {
        onFx(() -> { stage.hide(); return null; });
        Platform.exit();
    }

    @Test
    void rendersBroadTreeUsingOnlyViewportSizedTexture() throws Exception {
        var trie = new SynchronizedTrie<Contact>(Contact::getName, Contact[]::new);
        for (var contact : new ContactGenerator().generate(10_000)) {
            trie.add(contact);
        }
        TrieCanvasView view = onFx(() -> {
            var result = new TrieCanvasView();
            stage.setScene(new Scene(result, 800, 600));
            stage.show();
            result.setSnapshot(trie.snapshot(500));
            return result;
        });
        onFx(() -> {
            view.applyCss();
            view.layout();
            ScrollPane scroll = (ScrollPane) view.getChildren().get(1);
            Pane area = (Pane) scroll.getContent();
            Canvas canvas = (Canvas) area.getChildren().getFirst();
            assertTrue(area.getPrefWidth() > 12_000);
            assertTrue(canvas.getWidth() <= 2048);
            assertTrue(canvas.getHeight() <= 2048);
            assertTrue(canvas.getWidth() < area.getPrefWidth());
            assertDrawn(canvas);
            scroll.setHvalue(1);
            assertTrue(canvas.getLayoutX() > 10_000);
            assertDrawn(canvas);
            return null;
        });
    }

    @Test
    void scrollsToBottomOfLongKeyWithoutAllocatingFullHeightCanvas() throws Exception {
        var trie = new SynchronizedTrie<Contact>(Contact::getName, Contact[]::new);
        trie.add(new PersonalContact("a".repeat(256), "123", "", ""));
        TrieCanvasView view = onFx(() -> {
            var result = new TrieCanvasView();
            stage.setScene(new Scene(result, 800, 600));
            stage.show();
            result.setSnapshot(trie.snapshot(500, 128));
            return result;
        });
        onFx(() -> {
            view.applyCss();
            view.layout();
            ScrollPane scroll = (ScrollPane) view.getChildren().get(1);
            Pane area = (Pane) scroll.getContent();
            Canvas canvas = (Canvas) area.getChildren().getFirst();
            scroll.setVvalue(1);
            assertTrue(area.getPrefHeight() > 10_000);
            assertTrue(canvas.getLayoutY() > 9000);
            assertTrue(canvas.getHeight() <= 2048);
            assertDrawn(canvas);
            return null;
        });
    }

    private static void assertDrawn(Canvas canvas) {
        var image = canvas.snapshot(null, null);
        int colouredPixels = 0;
        for (int y = 0; y < (int) image.getHeight(); y += 3) {
            for (int x = 0; x < (int) image.getWidth(); x += 3) {
                int rgb = image.getPixelReader().getArgb(x, y) & 0xffffff;
                if (rgb != 0xffffff) {
                    colouredPixels++;
                }
            }
        }
        assertTrue(colouredPixels > 100, "Canvas must contain rendered nodes or edges");
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(15, TimeUnit.SECONDS);
    }
}
