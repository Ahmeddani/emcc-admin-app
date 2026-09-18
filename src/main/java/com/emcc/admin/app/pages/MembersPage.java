package com.emcc.admin.app.pages;

import com.emcc.admin.app.model.Member;
import com.emcc.admin.app.model.SampleData;
import com.emcc.admin.app.service.ApiClient;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.function.BiConsumer;

public class MembersPage {

    public static Node createContent(BiConsumer<String, Node> detailUpdater, BiConsumer<String, String> messageUpdater) {
        VBox root = new VBox(14);
        root.setPadding(new Insets(18));

        Label header = new Label("Manage Members");
        header.getStyleClass().add("section-title");

        TableView<Member> membersTable = new TableView<>();
        membersTable.setPlaceholder(new Label("No members found"));
        membersTable.setItems(SampleData.getMembers());

        Button addMemberButton = new Button("Add Member");
        addMemberButton.setOnAction(event -> {
            Member newMember = new Member("", "", "", "", "", "Single", "0", "0", "", "", "");
            membersTable.getItems().add(newMember);
            membersTable.getSelectionModel().select(newMember);
            membersTable.scrollTo(newMember);
        });

        Button removeMemberButton = new Button("Remove Member");
        removeMemberButton.disableProperty().bind(membersTable.getSelectionModel().selectedItemProperty().isNull());
        removeMemberButton.setOnAction(event -> {
            Member selected = membersTable.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Remove member \"" + selected.getName() + "\"? This cannot be undone.",
                    ButtonType.OK, ButtonType.CANCEL);
            confirm.setTitle("Remove Member");
            confirm.setHeaderText(null);
            confirm.showAndWait().filter(btn -> btn == ButtonType.OK).ifPresent(btn -> {
                String id = selected.getMembershipId();
                membersTable.getItems().remove(selected);
                membersTable.getSelectionModel().clearSelection();
                if (id != null && !id.isEmpty()) {
                    runVoidAsync(() -> ApiClient.deleteMember(id), null, null);
                }
            });
        });

        HBox actionRow = new HBox(10, addMemberButton, removeMemberButton);
        actionRow.setPadding(new Insets(4, 0, 12, 0));

        TableColumn<Member, String> idCol = new TableColumn<>("Membership ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("membershipId"));
        idCol.setCellFactory(TextFieldTableCell.forTableColumn());
        idCol.setOnEditCommit(event -> {
            event.getRowValue().setMembershipId(event.getNewValue());
            membersTable.refresh();
        });

        TableColumn<Member, String> fullNameCol = new TableColumn<>("Full Name");
        fullNameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        fullNameCol.setCellFactory(TextFieldTableCell.forTableColumn());
        fullNameCol.setOnEditCommit(event -> {
            event.getRowValue().setName(event.getNewValue());
            membersTable.refresh();
        });

        TableColumn<Member, String> addressCol = new TableColumn<>("Address");
        addressCol.setCellValueFactory(new PropertyValueFactory<>("address"));
        addressCol.setCellFactory(TextFieldTableCell.forTableColumn());
        addressCol.setOnEditCommit(event -> {
            event.getRowValue().setAddress(event.getNewValue());
            membersTable.refresh();
        });

        TableColumn<Member, String> phoneCol = new TableColumn<>("Phone Number");
        phoneCol.setCellValueFactory(new PropertyValueFactory<>("phoneNumber"));
        phoneCol.setCellFactory(TextFieldTableCell.forTableColumn());
        phoneCol.setOnEditCommit(event -> {
            event.getRowValue().setPhoneNumber(event.getNewValue());
            membersTable.refresh();
        });

        TableColumn<Member, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(new PropertyValueFactory<>("email"));
        emailCol.setCellFactory(TextFieldTableCell.forTableColumn());
        emailCol.setOnEditCommit(event -> {
            event.getRowValue().setEmail(event.getNewValue());
            membersTable.refresh();
        });

        TableColumn<Member, String> maritalCol = new TableColumn<>("Marital Status");
        maritalCol.setCellValueFactory(new PropertyValueFactory<>("maritalStatus"));
        maritalCol.setCellFactory(ComboBoxTableCell.forTableColumn("Single", "Married", "Widowed"));
        maritalCol.setOnEditCommit(event -> {
            event.getRowValue().setMaritalStatus(event.getNewValue());
            membersTable.refresh();
        });

        TableColumn<Member, String> childrenUnder10Col = new TableColumn<>("Children <10");
        childrenUnder10Col.setCellValueFactory(new PropertyValueFactory<>("childrenUnder10"));
        childrenUnder10Col.setCellFactory(TextFieldTableCell.forTableColumn());
        childrenUnder10Col.setOnEditCommit(event -> {
            if (!event.getNewValue().matches("\\d*")) { membersTable.refresh(); return; }
            event.getRowValue().setChildrenUnder10(event.getNewValue());
            membersTable.refresh();
        });

        TableColumn<Member, String> children10To18Col = new TableColumn<>("Children 10-18");
        children10To18Col.setCellValueFactory(new PropertyValueFactory<>("children10To18"));
        children10To18Col.setCellFactory(TextFieldTableCell.forTableColumn());
        children10To18Col.setOnEditCommit(event -> {
            if (!event.getNewValue().matches("\\d*")) { membersTable.refresh(); return; }
            event.getRowValue().setChildren10To18(event.getNewValue());
            membersTable.refresh();
        });

        TableColumn<Member, String> occupationCol = new TableColumn<>("Occupation");
        occupationCol.setCellValueFactory(new PropertyValueFactory<>("occupation"));
        occupationCol.setCellFactory(TextFieldTableCell.forTableColumn());
        occupationCol.setOnEditCommit(event -> {
            event.getRowValue().setOccupation(event.getNewValue());
            membersTable.refresh();
        });

        TableColumn<Member, String> monthlyDuesCol = new TableColumn<>("Monthly Dues");
        monthlyDuesCol.setCellValueFactory(new PropertyValueFactory<>("monthlyDues"));
        monthlyDuesCol.setCellFactory(TextFieldTableCell.forTableColumn());
        monthlyDuesCol.setOnEditCommit(event -> {
            if (!event.getNewValue().matches("\\d*\\.?\\d*")) { membersTable.refresh(); return; }
            event.getRowValue().setMonthlyDues(event.getNewValue());
            membersTable.refresh();
        });

        membersTable.getColumns().setAll(idCol, fullNameCol, addressCol, phoneCol, emailCol, maritalCol,
                childrenUnder10Col, children10To18Col, occupationCol, monthlyDuesCol);
        membersTable.setEditable(true);
        membersTable.setPrefHeight(420);

        messageUpdater.accept("Manage Members", "Add new members inline or select an existing member to edit their details.");

        membersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldMember, newMember) -> {
            if (newMember != null) {
                String title = newMember.getName().isEmpty() ? "New Member" : newMember.getName();
                detailUpdater.accept(title, createMemberDetailForm(newMember, membersTable));
            } else {
                messageUpdater.accept("Manage Members", "Add new members inline or select an existing member to edit their details.");
            }
        });

        root.getChildren().addAll(header, actionRow, membersTable);
        return root;
    }

    private static VBox createMemberDetailForm(Member member, TableView<Member> table) {
        Label membershipIdLabel = new Label("Membership ID");
        TextField membershipIdField = new TextField(member.getMembershipId());
        Label nameLabel = new Label("Full Name");
        TextField nameField = new TextField(member.getName());
        Label addressLabel = new Label("Address");
        TextField addressField = new TextField(member.getAddress());
        Label phoneLabel = new Label("Phone Number");
        TextField phoneField = new TextField(member.getPhoneNumber());
        Label emailLabel = new Label("Email");
        TextField emailField = new TextField(member.getEmail());
        Label maritalLabel = new Label("Marital Status");
        ComboBox<String> maritalCombo = new ComboBox<>();
        maritalCombo.getItems().addAll("Single", "Married", "Widowed");
        maritalCombo.setValue(member.getMaritalStatus().isEmpty() ? "Single" : member.getMaritalStatus());
        Label childrenUnder10Label = new Label("Children <10");
        TextField childrenUnder10Field = new TextField(member.getChildrenUnder10());
        Label children10To18Label = new Label("Children 10-18");
        TextField children10To18Field = new TextField(member.getChildren10To18());
        Label occupationLabel = new Label("Occupation");
        TextField occupationField = new TextField(member.getOccupation());
        Label monthlyDuesLabel = new Label("Monthly Dues ($)");
        TextField monthlyDuesField = new TextField(member.getMonthlyDues());
        monthlyDuesField.setPromptText("e.g. 20.00");

        childrenUnder10Field.textProperty().addListener((obs, old, val) -> {
            if (!val.matches("\\d*")) childrenUnder10Field.setText(old);
        });
        children10To18Field.textProperty().addListener((obs, old, val) -> {
            if (!val.matches("\\d*")) children10To18Field.setText(old);
        });
        monthlyDuesField.textProperty().addListener((obs, old, val) -> {
            if (!val.matches("\\d*\\.?\\d*")) monthlyDuesField.setText(old);
        });

        Label saveMessage = new Label();
        saveMessage.getStyleClass().add("detail-message");

        Button saveButton = new Button("Save");
        saveButton.setOnAction(event -> {
            member.setMembershipId(membershipIdField.getText());
            member.setName(nameField.getText());
            member.setAddress(addressField.getText());
            member.setPhoneNumber(phoneField.getText());
            member.setEmail(emailField.getText());
            member.setMaritalStatus(maritalCombo.getValue());
            member.setChildrenUnder10(childrenUnder10Field.getText());
            member.setChildren10To18(children10To18Field.getText());
            member.setOccupation(occupationField.getText());
            member.setMonthlyDues(monthlyDuesField.getText());
            if (member.getCreatedDate() == null || member.getCreatedDate().isEmpty()) {
                member.setCreatedDate(java.time.LocalDate.now().toString());
            }
            table.refresh();

            saveButton.setDisable(true);
            saveMessage.setText("Saving…");
            runAsync(
                    () -> ApiClient.saveMember(member),
                    saved -> { saveButton.setDisable(false); saveMessage.setText("Member saved."); },
                    err -> { saveButton.setDisable(false); saveMessage.setText("Save failed: " + err.getMessage()); }
            );
        });

        VBox form = new VBox(12,
                membershipIdLabel, membershipIdField,
                nameLabel, nameField,
                addressLabel, addressField,
                phoneLabel, phoneField,
                emailLabel, emailField,
                maritalLabel, maritalCombo,
                childrenUnder10Label, childrenUnder10Field,
                children10To18Label, children10To18Field,
                occupationLabel, occupationField,
                monthlyDuesLabel, monthlyDuesField,
                saveButton, saveMessage);
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
