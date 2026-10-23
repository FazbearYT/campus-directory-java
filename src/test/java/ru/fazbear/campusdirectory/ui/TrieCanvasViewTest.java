package ru.fazbear.campusdirectory.ui;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import ru.fazbear.campusdirectory.model.PersonalContact;
import ru.fazbear.campusdirectory.service.ContactGenerator;
import ru.fazbear.campusdirectory.trie.ContactTrie;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "CAMPUS_GUI_TESTS", matches = "true")
class TrieCanvasViewTest {
    private static Stage stage;

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
        var trie = new ContactTrie();
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
        var trie = new ContactTrie();
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
