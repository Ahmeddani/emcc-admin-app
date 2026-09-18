package com.emcc.admin.app.pages;

import com.emcc.admin.app.model.Asset;
import com.emcc.admin.app.model.SampleData;
import com.emcc.admin.app.service.ApiClient;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.function.BiConsumer;

public class AssetsPage {

    public static Node createContent(BiConsumer<String, Node> detailUpdater, BiConsumer<String, String> messageUpdater) {
        VBox root = new VBox(14);
        root.setPadding(new Insets(18));

        Label header = new Label("Manage Assets");
        header.getStyleClass().add("section-title");

        TableView<Asset> assetsTable = new TableView<>();
        assetsTable.setPlaceholder(new Label("No assets found"));
        assetsTable.setItems(SampleData.getAssets());

        Button addAssetButton = new Button("Add Asset");
        addAssetButton.setOnAction(event -> {
            Asset newAsset = new Asset("", "", "", "Good");
            assetsTable.getItems().add(newAsset);
            assetsTable.getSelectionModel().select(newAsset);
            assetsTable.scrollTo(newAsset);
        });

        Button removeAssetButton = new Button("Remove Asset");
        removeAssetButton.disableProperty().bind(assetsTable.getSelectionModel().selectedItemProperty().isNull());
        removeAssetButton.setOnAction(event -> {
            Asset selected = assetsTable.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Remove asset \"" + selected.getAssetName() + "\"? This cannot be undone.",
                    ButtonType.OK, ButtonType.CANCEL);
            confirm.setTitle("Remove Asset");
            confirm.setHeaderText(null);
            confirm.showAndWait().filter(btn -> btn == ButtonType.OK).ifPresent(btn -> {
                Long id = selected.getId();
                assetsTable.getItems().remove(selected);
                assetsTable.getSelectionModel().clearSelection();
                if (id != null) {
                    runVoidAsync(() -> ApiClient.deleteAsset(id), null, null);
                }
            });
        });

        HBox actionRow = new HBox(10, addAssetButton, removeAssetButton);
        actionRow.setPadding(new Insets(4, 0, 12, 0));

        TableColumn<Asset, String> nameCol = new TableColumn<>("Asset Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("assetName"));
        nameCol.setCellFactory(TextFieldTableCell.forTableColumn());
        nameCol.setOnEditCommit(event -> {
            event.getRowValue().setAssetName(event.getNewValue());
            assetsTable.refresh();
        });

        TableColumn<Asset, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeCol.setCellFactory(TextFieldTableCell.forTableColumn());
        typeCol.setOnEditCommit(event -> {
            event.getRowValue().setType(event.getNewValue());
            assetsTable.refresh();
        });

        TableColumn<Asset, String> countCol = new TableColumn<>("Count");
        countCol.setCellValueFactory(new PropertyValueFactory<>("count"));
        countCol.setCellFactory(TextFieldTableCell.forTableColumn());
        countCol.setOnEditCommit(event -> {
            if (!event.getNewValue().matches("\\d*")) { assetsTable.refresh(); return; }
            event.getRowValue().setCount(event.getNewValue());
            assetsTable.refresh();
        });

        TableColumn<Asset, String> conditionCol = new TableColumn<>("Condition");
        conditionCol.setCellValueFactory(new PropertyValueFactory<>("condition"));
        conditionCol.setCellFactory(TextFieldTableCell.forTableColumn());
        conditionCol.setOnEditCommit(event -> {
            event.getRowValue().setCondition(event.getNewValue());
            assetsTable.refresh();
        });

        assetsTable.getColumns().setAll(nameCol, typeCol, countCol, conditionCol);
        assetsTable.setEditable(true);
        assetsTable.setPrefHeight(420);

        messageUpdater.accept("Manage Assets", "Add new assets using the form or edit existing asset details directly in the table.");

        assetsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldAsset, newAsset) -> {
            if (newAsset != null) {
                String title = newAsset.getAssetName().isEmpty() ? "New Asset" : newAsset.getAssetName();
                detailUpdater.accept(title, createAssetDetailForm(newAsset, assetsTable));
            } else {
                messageUpdater.accept("Manage Assets", "Add new assets using the form or edit existing asset details directly in the table.");
            }
        });

        root.getChildren().addAll(header, actionRow, assetsTable);
        return root;
    }

    private static VBox createAssetDetailForm(Asset asset, TableView<Asset> table) {
        Label assetNameLabel = new Label("Asset Name");
        TextField assetNameField = new TextField(asset.getAssetName());
        Label typeLabel = new Label("Type");
        TextField typeField = new TextField(asset.getType());
        Label countLabel = new Label("Count");
        TextField countField = new TextField(asset.getCount());
        Label conditionLabel = new Label("Condition");
        TextField conditionField = new TextField(asset.getCondition());

        countField.textProperty().addListener((obs, old, val) -> {
            if (!val.matches("\\d*")) countField.setText(old);
        });

        Label saveMessage = new Label();
        saveMessage.getStyleClass().add("detail-message");

        Button saveButton = new Button("Save");
        saveButton.setOnAction(event -> {
            asset.setAssetName(assetNameField.getText());
            asset.setType(typeField.getText());
            asset.setCount(countField.getText());
            asset.setCondition(conditionField.getText());
            table.refresh();

            saveButton.setDisable(true);
            saveMessage.setText("Saving…");
            runAsync(
                    () -> ApiClient.saveAsset(asset),
                    saved -> {
                        asset.setId(saved.getId());
                        saveButton.setDisable(false);
                        saveMessage.setText("Asset saved.");
                    },
                    err -> { saveButton.setDisable(false); saveMessage.setText("Save failed: " + err.getMessage()); }
            );
        });

        VBox form = new VBox(12, assetNameLabel, assetNameField, typeLabel, typeField,
                countLabel, countField, conditionLabel, conditionField, saveButton, saveMessage);
        form.setPadding(new Insets(8));
        form.getStyleClass().add("detail-form");
        return form;
    }

    private static <T> void runAsync(ApiCall<T> call, java.util.function.Consumer<T> onSuccess,
                                     java.util.function.Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return call.execute(); }
        };
        if (onSuccess != null) task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        if (onError != null) task.setOnFailed(e -> onError.accept(task.getException()));
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private static void runVoidAsync(VoidApiCall call, Runnable onSuccess,
                                     java.util.function.Consumer<Throwable> onError) {
        runAsync(() -> { call.execute(); return null; },
                onSuccess != null ? v -> onSuccess.run() : null, onError);
    }

    @FunctionalInterface
    private interface ApiCall<T> { T execute() throws Exception; }

    @FunctionalInterface
    private interface VoidApiCall { void execute() throws Exception; }
}
