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
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ru.fazbear.campusdirectory.model.Contact;
import ru.fazbear.campusdirectory.model.PersonalContact;
import ru.fazbear.campusdirectory.ContactDirectoryApp;
import ru.fazbear.campusdirectory.service.ContactDirectory;
import ru.fazbear.campusdirectory.service.ContactGenerator;
import ru.fazbear.campusdirectory.io.BinaryContactSerializer;
import ru.fazbear.campusdirectory.io.ContactArchive;
import ru.fazbear.campusdirectory.io.JsonContactSerializer;
import ru.fazbear.campusdirectory.io.Serializer;
import ru.fazbear.campusdirectory.trie.SynchronizedTrie;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@EnabledIfEnvironmentVariable(named = "CAMPUS_GUI_TESTS", matches = "true")
class TrieCanvasViewTest {
    private static Stage stage;

    @TempDir
    java.nio.file.Path temporary;

    @ParameterizedTest
    @ValueSource(strings = {"json", "bin"})
    void archiveImportRefreshesTableSearchAndVisualization(String format) throws Exception {
        Serializer<ContactArchive> serializer = format.equals("json")
                ? new JsonContactSerializer() : new BinaryContactSerializer();
        var file = temporary.resolve("contacts." + format);
        try (var input = getClass().getResourceAsStream("/serialization/contacts." + format)) {
            java.nio.file.Files.copy(input, file);
        }
        var app = onFx(() -> {
            var result = new ContactDirectoryApp();
            result.start(stage);
            ((ContactDirectory) field(result, "directory")).add(new PersonalContact("Keep", "123", "", ""));
            ((TextField) field(result, "prefixField")).setText("Keep");
            invokeArchiveOperation(result, "startArchiveImport", serializer, file);
            assertTrue(((javafx.scene.Node) field(result, "dataActions")).isDisabled());
            return result;
        });
        waitForOperation(app);
        onFx(() -> {
            assertEquals(4, ((TableView<?>) field(app, "table")).getItems().size());
            assertEquals("", ((TextField) field(app, "prefixField")).getText());
            var directory = (ContactDirectory) field(app, "directory");
            assertEquals(2, directory.findByName("Анна Ёлкина").size());
            var view = (TrieCanvasView) field(app, "trieView");
            var root = (ru.fazbear.campusdirectory.trie.TrieNodeSnapshot) field(view, "displayedRoot");
            assertEquals(4, root.subtreeContactCount());
            assertFalse(((javafx.scene.Node) field(app, "dataActions")).isDisabled());
            return null;
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"json", "bin"})
    void failedArchiveImportKeepsCurrentDirectoryAndTable(String format) throws Exception {
        Serializer<ContactArchive> serializer = format.equals("json")
                ? new JsonContactSerializer() : new BinaryContactSerializer();
        var file = temporary.resolve("invalid." + format);
        try (var input = getClass().getResourceAsStream("/serialization/invalid-contact." + format)) {
            java.nio.file.Files.copy(input, file);
        }
        var app = onFx(() -> {
            var result = new ContactDirectoryApp();
            result.start(stage);
            ((ContactDirectory) field(result, "directory")).add(new PersonalContact("Keep", "123", "", ""));
            invoke(result, "refreshAll");
            invokeArchiveOperation(result, "startArchiveImport", serializer, file);
            return result;
        });
        String message = waitForMessageDialog("Ошибка");
        assertTrue(message.contains("Телефон"));
        onFx(() -> {
            assertEquals("Keep", ((ContactDirectory) field(app, "directory")).getAll().getFirst().getName());
            assertEquals(1, ((TableView<?>) field(app, "table")).getItems().size());
            assertNull(field(app, "activeTask"));
            return null;
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"json", "bin"})
    void archiveExportSavesEntireDirectoryWhenTableIsFiltered(String format) throws Exception {
        Serializer<ContactArchive> serializer = format.equals("json")
                ? new JsonContactSerializer() : new BinaryContactSerializer();
        var file = temporary.resolve("export." + format);
        var app = onFx(() -> {
            var result = new ContactDirectoryApp();
            result.start(stage);
            var directory = (ContactDirectory) field(result, "directory");
            directory.add(new PersonalContact("Анна", "123", "", ""));
            directory.add(new PersonalContact("Борис", "456", "", ""));
            ((TextField) field(result, "prefixField")).setText("Анна");
            invoke(result, "findContact");
            assertEquals(1, ((TableView<?>) field(result, "table")).getItems().size());
            invokeArchiveOperation(result, "startArchiveExport", serializer, file);
            assertFalse(((javafx.scene.Node) field(result, "cancelOperationButton")).isVisible());
            return result;
        });
        assertTrue(waitForMessageDialog("Готово").contains("2"));
        assertEquals(2, serializer.load(file).contacts().size());
        assertNull(onFx(() -> field(app, "activeTask")));
    }

    @Test
    void formatComparisonRunsInBackgroundAndKeepsWorkingData() throws Exception {
        var app = onFx(() -> {
            var result = new ContactDirectoryApp();
            result.start(stage);
            ((ContactDirectory) field(result, "directory")).add(new PersonalContact("Keep", "123", "", ""));
            invoke(result, "runSerializationBenchmarks");
            assertTrue(field(result, "activeTask") != null);
            return result;
        });
        String text = waitForResultDialog("Сравнение форматов");
        assertTrue(text.contains("JSON"));
        assertTrue(text.contains("BIN"));
        assertTrue(text.contains("среднее по 5"));
        assertEquals(1, onFx(() -> ((ContactDirectory) field(app, "directory")).size()));
    }

    private static void invokeArchiveOperation(ContactDirectoryApp app, String name,
            Serializer<ContactArchive> serializer, java.nio.file.Path file) throws Exception {
        var method = ContactDirectoryApp.class.getDeclaredMethod(name, Serializer.class, java.nio.file.Path.class);
        method.setAccessible(true);
        method.invoke(app, serializer, file);
    }

    @Test
    void cancelledArchiveImportDoesNotPublishLateResult() throws Exception {
        var reading = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var serializer = new Serializer<ContactArchive>() {
            @Override
            public String format() { return "json"; }

            @Override
            public void save(ContactArchive data, java.nio.file.Path file) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ContactArchive load(java.nio.file.Path file) throws java.io.IOException {
                reading.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new java.io.IOException("Ожидание отмены истекло");
                    }
                    return ContactArchive.from(java.util.List.of(new PersonalContact("Late", "456", "", "")));
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new java.io.IOException(error);
                } finally {
                    finished.countDown();
                }
            }
        };
        var app = onFx(() -> {
            var result = new ContactDirectoryApp();
            result.start(stage);
            ((ContactDirectory) field(result, "directory")).add(new PersonalContact("Keep", "123", "", ""));
            invokeArchiveOperation(result, "startArchiveImport", serializer, temporary.resolve("cancel.json"));
            return result;
        });
        try {
            assertTrue(reading.await(10, TimeUnit.SECONDS));
            onFx(() -> { ((javafx.concurrent.Task<?>) field(app, "activeTask")).cancel(false); return null; });
        } finally {
            release.countDown();
        }
        assertTrue(finished.await(10, TimeUnit.SECONDS));
        waitForOperation(app);
        onFx(() -> {
            assertEquals("Keep", ((ContactDirectory) field(app, "directory")).getAll().getFirst().getName());
            assertFalse(((javafx.scene.Node) field(app, "dataActions")).isDisabled());
            return null;
        });
    }

    private static void waitForOperation(ContactDirectoryApp app) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            if (onFx(() -> field(app, "activeTask") == null)) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Фоновая операция не завершилась");
    }

    private static String waitForMessageDialog(String title) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            String text = onFx(() -> {
                for (var window : java.util.List.copyOf(javafx.stage.Window.getWindows())) {
                    if (window instanceof Stage dialog && title.equals(dialog.getTitle())
                            && dialog.getScene().getRoot() instanceof javafx.scene.control.DialogPane pane) {
                        String message = pane.getContentText();
                        ((javafx.scene.control.Button) pane.lookupButton(javafx.scene.control.ButtonType.OK)).fire();
                        return message;
                    }
                }
                return null;
            });
            if (text != null) {
                return text;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Не появилось окно: " + title);
    }

    @Test
    void contactFormKeepsInvalidInputOpenAndSavesTheReturnedInstanceOnce() throws Exception {
        onFx(() -> {
            var directory = new ContactDirectory();
            var attempts = new java.util.concurrent.atomic.AtomicInteger();
            var form = contactForm(null, contact -> {
                attempts.incrementAndGet();
                directory.add(contact);
            });
            var dialog = (javafx.scene.control.Dialog<?>) field(form, "dialog");
            var saveType = (javafx.scene.control.ButtonType) field(form, "saveButton");
            var save = (javafx.scene.control.Button) dialog.getDialogPane().lookupButton(saveType);
            dialog.show();
            try {
                ((TextField) field(form, "phoneField")).setText("123");
                save.fire();
                assertTrue(dialog.isShowing());
                assertEquals(0, directory.size());
                assertEquals("Имя обязательно", ((javafx.scene.control.Label) field(form, "errorLabel")).getText());

                ((TextField) field(form, "nameField")).setText("  Анна  ");
                save.fire();
                assertFalse(dialog.isShowing());
                assertEquals(2, attempts.get());
                assertEquals(1, directory.size());
                assertSame(dialog.getResult(), directory.getAll().getFirst());
                assertEquals("Анна", directory.getAll().getFirst().getName());
            } finally {
                dialog.close();
            }
            return null;
        });
    }

    @Test
    void editingFormPreservesOriginalOnErrorAndCancel() throws Exception {
        onFx(() -> {
            var directory = new ContactDirectory();
            var original = new PersonalContact("Анна", "123", "", "");
            directory.add(original);
            var form = contactForm(original, replacement -> directory.update(original, replacement));
            var dialog = (javafx.scene.control.Dialog<?>) field(form, "dialog");
            dialog.show();
            try {
                ((TextField) field(form, "phoneField")).setText("---");
                var saveType = (javafx.scene.control.ButtonType) field(form, "saveButton");
                ((javafx.scene.control.Button) dialog.getDialogPane().lookupButton(saveType)).fire();
                assertTrue(dialog.isShowing());
                assertSame(original, directory.getAll().getFirst());

                ((TextField) field(form, "phoneField")).setText("456");
                var cancelType = (javafx.scene.control.ButtonType) field(form, "cancelButton");
                ((javafx.scene.control.Button) dialog.getDialogPane().lookupButton(cancelType)).fire();
                assertFalse(dialog.isShowing());
                assertSame(original, directory.getAll().getFirst());
                assertNull(dialog.getResult());
            } finally {
                dialog.close();
            }
            return null;
        });
    }

    private static ContactDialog contactForm(Contact contact, java.util.function.Consumer<Contact> saveContact)
            throws Exception {
        var constructor = ContactDialog.class.getDeclaredConstructor(
                javafx.stage.Window.class, Contact.class, java.util.function.Consumer.class);
        constructor.setAccessible(true);
        return constructor.newInstance(stage, contact, saveContact);
    }

    @Test
    void exactSearchShowsAllFourNamesakesAndExcludesLongerNames() throws Exception {
        onFx(() -> {
            var app = new ContactDirectoryApp();
            app.start(stage);
            var directory = (ContactDirectory) field(app, "directory");
            for (int index = 0; index < 4; index++) {
                directory.add(new PersonalContact("Анна Волкова", "123" + index, "", ""));
            }
            directory.add(new PersonalContact("Анна Волкова-Миронова", "456", "", ""));
            var input = (TextField) field(app, "prefixField");
            var table = (TableView<?>) field(app, "table");
            input.setText("  АННА ВОЛКОВА  ");
            invoke(app, "autocomplete");
            assertEquals(5, table.getItems().size());
            invoke(app, "findContact");
            assertEquals(4, table.getItems().size());
            assertEquals(directory.findByName("Анна Волкова"), table.getItems());
            assertTrue(((javafx.scene.control.Label) field(app, "statusLabel")).getText().endsWith("4"));
            assertEquals(5, directory.size());
            return null;
        });
    }

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

    private static Object field(Object app, String name) throws Exception {
        var field = app.getClass().getDeclaredField(name);
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
