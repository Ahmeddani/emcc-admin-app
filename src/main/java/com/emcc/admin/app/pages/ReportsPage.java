package com.emcc.admin.app.pages;

import com.emcc.admin.app.pages.reports.AssetInventoryTab;
import com.emcc.admin.app.pages.reports.BoardSummaryTab;
import com.emcc.admin.app.pages.reports.CollectionsSummaryTab;
import com.emcc.admin.app.pages.reports.MemberStatementTab;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import java.util.function.BiConsumer;

/**
 * Reports hub — each tab is a self-contained report with its own filters and
 * its own Export CSV / Export PDF actions.
 */
public class ReportsPage {

    public static Node createContent(BiConsumer<String, Node> detailUpdater,
                                     BiConsumer<String, String> messageUpdater) {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        tabs.getTabs().addAll(
                tab("Collections Summary", CollectionsSummaryTab.create()),
                tab("Member Statement", MemberStatementTab.create()),
                tab("Board Summary", BoardSummaryTab.create()),
                tab("Asset Inventory", AssetInventoryTab.create())
        );

        messageUpdater.accept("Reports",
                "Generate collection reports, member statements, board summaries, and asset inventory — each exportable to CSV or PDF.");

        javafx.scene.control.Label header = new javafx.scene.control.Label("Reports");
        header.getStyleClass().add("section-title");
        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(16,
                com.emcc.admin.app.util.Branding.pageHeader(header), tabs);
        javafx.scene.layout.VBox.setVgrow(tabs, javafx.scene.layout.Priority.ALWAYS);
        root.setPadding(new javafx.geometry.Insets(18));
        return root;
    }

    private static Tab tab(String title, Node content) {
        Tab t = new Tab(title, content);
        t.setClosable(false);
        return t;
    }
}
