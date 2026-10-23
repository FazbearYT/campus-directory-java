package ru.fazbear.campusdirectory.ui;

import java.util.IdentityHashMap;
import java.util.Map;

import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import ru.fazbear.campusdirectory.trie.TrieNodeSnapshot;

public final class TrieCanvasView extends VBox {
    private static final double HORIZONTAL_STEP = 64;
    private static final double VERTICAL_STEP = 78;
    private static final double NODE_RADIUS = 18;
    private static final double MARGIN = 48;
    private static final double MAXIMUM_VIEWPORT_SIZE = 2_048;

    private final Label summary = new Label("Trie пока пуст");
    private final Canvas canvas = new Canvas(900, 560);
    private final Pane drawingArea = new Pane(canvas);
    private final ScrollPane scrollPane = new ScrollPane(drawingArea);
    private TrieNodeSnapshot displayedRoot;
    private Map<TrieNodeSnapshot, Point2D> displayedPositions;
    private double drawingWidth = 900;
    private double drawingHeight = 560;

    public TrieCanvasView() {
        canvas.setManaged(false);
        setSpacing(8);
        summary.setStyle("-fx-text-fill: #5f6368;");
        summary.setWrapText(true);
        scrollPane.setPannable(true);
        scrollPane.hvalueProperty().addListener((observable, previous, current) -> drawViewport());
        scrollPane.vvalueProperty().addListener((observable, previous, current) -> drawViewport());
        scrollPane.viewportBoundsProperty().addListener((observable, previous, current) -> drawViewport());
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        getChildren().addAll(summary, scrollPane);
    }

    public void setSnapshot(TrieNodeSnapshot root) {
        var leafCounts = new IdentityHashMap<TrieNodeSnapshot, Integer>();
        int leaves = countLeaves(root, leafCounts);
        int depth = maximumDepth(root, 0);
        double width = Math.max(900, leaves * HORIZONTAL_STEP + MARGIN * 2);
        double height = Math.max(560, (depth + 1) * VERTICAL_STEP + MARGIN * 2);
        drawingWidth = width;
        drawingHeight = height;
        drawingArea.setMinSize(width, height);
        drawingArea.setPrefSize(width, height);
        drawingArea.setMaxSize(width, height);

        var positions = new IdentityHashMap<TrieNodeSnapshot, Point2D>();
        placeNodes(root, 0, MARGIN, width - MARGIN * 2, leafCounts, positions);
        displayedRoot = root;
        displayedPositions = positions;
        drawViewport();
        Platform.runLater(this::focusRoot);

        int omittedBranches = countOmittedBranches(root);
        String suffix = omittedBranches == 0 ? "" : "; скрыто ветвей: " + omittedBranches;
        summary.setText("Узлов показано: %d; контактов: %d%s".formatted(
                countNodes(root), root.subtreeContactCount(), suffix
        ));
    }

    private void focusRoot() {
        if (displayedRoot != null) {
            double viewportWidth = scrollPane.getViewportBounds().getWidth();
            double availableScroll = drawingWidth - viewportWidth;
            double rootX = displayedPositions.get(displayedRoot).getX();
            scrollPane.setHvalue(availableScroll <= 0 ? 0
                    : Math.max(0, Math.min(1, (rootX - viewportWidth / 2) / availableScroll)));
            scrollPane.setVvalue(0);
            drawViewport();
        }
    }

    private int countLeaves(TrieNodeSnapshot node, Map<TrieNodeSnapshot, Integer> leafCounts) {
        TrieNodeSnapshot[] children = node.children();
        if (children.length == 0) {
            leafCounts.put(node, 1);
            return 1;
        }
        int count = 0;
        for (TrieNodeSnapshot child : children) {
            count += countLeaves(child, leafCounts);
        }
        leafCounts.put(node, count);
        return count;
    }

    private int maximumDepth(TrieNodeSnapshot node, int depth) {
        int maximum = depth;
        for (TrieNodeSnapshot child : node.children()) {
            maximum = Math.max(maximum, maximumDepth(child, depth + 1));
        }
        return maximum;
    }

    private void placeNodes(TrieNodeSnapshot node, int depth, double left, double availableWidth,
                            Map<TrieNodeSnapshot, Integer> leafCounts,
                            Map<TrieNodeSnapshot, Point2D> positions) {
        TrieNodeSnapshot[] children = node.children();
        if (children.length == 0) {
            positions.put(node, new Point2D(left + availableWidth / 2, MARGIN + depth * VERTICAL_STEP));
            return;
        }
        int totalLeaves = leafCounts.get(node);
        double childLeft = left;
        for (TrieNodeSnapshot child : children) {
            double childWidth = availableWidth * leafCounts.get(child) / totalLeaves;
            placeNodes(child, depth + 1, childLeft, childWidth, leafCounts, positions);
            childLeft += childWidth;
        }
        Point2D first = positions.get(children[0]);
        Point2D last = positions.get(children[children.length - 1]);
        positions.put(node, new Point2D((first.getX() + last.getX()) / 2, MARGIN + depth * VERTICAL_STEP));
    }

    private void drawViewport() {
        if (displayedRoot == null) {
            return;
        }
        double viewportWidth = Math.max(1, scrollPane.getViewportBounds().getWidth());
        double viewportHeight = Math.max(1, scrollPane.getViewportBounds().getHeight());
        double offsetX = Math.max(0, drawingWidth - viewportWidth) * scrollPane.getHvalue();
        double offsetY = Math.max(0, drawingHeight - viewportHeight) * scrollPane.getVvalue();
        canvas.setWidth(Math.min(MAXIMUM_VIEWPORT_SIZE, viewportWidth));
        canvas.setHeight(Math.min(MAXIMUM_VIEWPORT_SIZE, viewportHeight));
        canvas.relocate(offsetX, offsetY);
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        graphics.setFill(Color.WHITE);
        graphics.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        graphics.save();
        graphics.translate(-offsetX, -offsetY);
        drawEdges(displayedRoot, displayedPositions, graphics);
        drawNodes(displayedRoot, displayedPositions, graphics);
        graphics.restore();
    }

    private void drawEdges(TrieNodeSnapshot node, Map<TrieNodeSnapshot, Point2D> positions,
                           GraphicsContext graphics) {
        Point2D start = positions.get(node);
        graphics.setStroke(Color.web("#9aa0a6"));
        graphics.setLineWidth(1.5);
        for (TrieNodeSnapshot child : node.children()) {
            Point2D end = positions.get(child);
            graphics.strokeLine(start.getX(), start.getY() + NODE_RADIUS, end.getX(), end.getY() - NODE_RADIUS);
            drawEdges(child, positions, graphics);
        }
    }

    private void drawNodes(TrieNodeSnapshot node, Map<TrieNodeSnapshot, Point2D> positions,
                           GraphicsContext graphics) {
        Point2D point = positions.get(node);
        Color fill = node.visited()
                ? Color.web("#ffb74d")
                : node.contactCount() > 0 ? Color.web("#81c784") : Color.web("#90caf9");
        graphics.setFill(fill);
        graphics.fillOval(point.getX() - NODE_RADIUS, point.getY() - NODE_RADIUS,
                NODE_RADIUS * 2, NODE_RADIUS * 2);
        graphics.setStroke(node.contactCount() > 0 ? Color.web("#1b5e20") : Color.web("#24527a"));
        graphics.setLineWidth(node.contactCount() > 0 ? 3 : 1.5);
        graphics.strokeOval(point.getX() - NODE_RADIUS, point.getY() - NODE_RADIUS,
                NODE_RADIUS * 2, NODE_RADIUS * 2);

        graphics.setFill(Color.web("#202124"));
        graphics.setTextAlign(TextAlignment.CENTER);
        graphics.setFont(Font.font("System", FontWeight.BOLD, node.isRoot() ? 9 : 14));
        graphics.fillText(node.isRoot() ? "ROOT" : Character.toString(node.symbol()),
                point.getX(), point.getY() + 5);
        graphics.setFont(Font.font("System", 10));
        graphics.fillText(Integer.toString(node.subtreeContactCount()), point.getX(), point.getY() + 31);
        if (node.omittedChildCount() > 0) {
            graphics.setFill(Color.web("#b3261e"));
            graphics.fillText("+" + node.omittedChildCount() + " ветв.", point.getX(), point.getY() + 45);
        }
        for (TrieNodeSnapshot child : node.children()) {
            drawNodes(child, positions, graphics);
        }
    }

    private int countNodes(TrieNodeSnapshot node) {
        int count = 1;
        for (TrieNodeSnapshot child : node.children()) {
            count += countNodes(child);
        }
        return count;
    }

    private int countOmittedBranches(TrieNodeSnapshot node) {
        int count = node.omittedChildCount();
        for (TrieNodeSnapshot child : node.children()) {
            count += countOmittedBranches(child);
        }
        return count;
    }
}
