package com.emcc.admin.app.util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/** The EMCC emblem (same artwork as the app icon and the website logo). */
public final class Branding {

    private static final String EMBLEM = "/emcc-app-icon.png";
    private static Image emblem;

    private Branding() {}

    /** A crisp, square emblem of the given size in pixels. */
    public static ImageView emblem(double size) {
        if (emblem == null) {
            emblem = new Image(Branding.class.getResourceAsStream(EMBLEM));
        }
        ImageView view = new ImageView(emblem);
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }

    /** Compact logo card (emblem + EMCC name) shown at the top right of every page. */
    public static Node pageLogo() {
        ImageView icon = emblem(64);
        icon.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 8, 0, 0, 2);");

        Label logomark = new Label("EMCC");
        logomark.setTextFill(Color.web("#144d29"));
        logomark.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        Label organizationName = new Label("Eritrean Muslims Community\nof Columbus, Ohio");
        organizationName.setTextFill(Color.web("#47525e"));
        organizationName.setStyle("-fx-font-size: 10px;");

        VBox text = new VBox(2, logomark, organizationName);
        text.setAlignment(Pos.CENTER_LEFT);

        HBox box = new HBox(10, icon, text);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(8, 14, 8, 10));
        box.setStyle("-fx-background-color: white; -fx-background-radius: 16; -fx-border-color: rgba(31, 122, 77, 0.24); -fx-border-radius: 16;");
        box.setMaxHeight(Region.USE_PREF_SIZE);
        return box;
    }

    /** A page title on the left with the EMCC logo card on the right. */
    public static HBox pageHeader(Node title) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(16, title, spacer, pageLogo());
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
