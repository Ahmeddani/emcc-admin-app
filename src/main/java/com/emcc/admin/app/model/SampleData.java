package com.emcc.admin.app.model;

import com.emcc.admin.app.service.ApiClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;

import java.util.List;
import java.util.function.Consumer;

public class SampleData {

    private static final ObservableList<Member> MEMBERS = FXCollections.observableArrayList();
    private static final ObservableList<Payment> PAYMENTS = FXCollections.observableArrayList();
    private static final ObservableList<Asset> ASSETS = FXCollections.observableArrayList();

    public static ObservableList<Member> getMembers() { return MEMBERS; }
    public static ObservableList<Payment> getPayments() { return PAYMENTS; }
    public static ObservableList<Asset> getAssets() { return ASSETS; }

    public static void reload(Runnable onComplete, Consumer<Throwable> onError) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                List<Member> members = ApiClient.getMembers();
                List<Asset> assets = ApiClient.getAssets();
                List<Payment> payments = ApiClient.getPayments();
                Platform.runLater(() -> {
                    MEMBERS.setAll(members);
                    ASSETS.setAll(assets);
                    PAYMENTS.setAll(payments);
                    if (onComplete != null) onComplete.run();
                });
                return null;
            }
        };
        task.setOnFailed(e -> {
            if (onError != null) onError.accept(task.getException());
        });
        Thread t = new Thread(task, "api-reload");
        t.setDaemon(true);
        t.start();
    }
}
