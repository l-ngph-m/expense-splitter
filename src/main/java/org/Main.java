package org;

import javafx.application.Application;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import org.model.*;
import org.service.BillSplitterService;
import org.service.DataStore;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main extends Application {
    private Stage stage;
    private Group currentGroup;
    private VBox mainContent;

    private ListView<String> groupListView = new ListView<>();
    private ListView<String> memberListView = new ListView<>();
    private TableView<Expense> expenseListView = new TableView<>();
    private VBox balancePanel = new VBox(5);

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("Expense Splitter");
        stage.setWidth(650);
        stage.setHeight(650);
        stage.setMinWidth(650);
        stage.setMinHeight(650);

        DataStore.loadData();

        BorderPane root = new BorderPane();

        Button groupsBtn = createNavButton("Groups");
        Button membersBtn = createNavButton("Members");
        Button expensesBtn = createNavButton("Expenses");
        Button balancesBtn = createNavButton("Balances");

        groupsBtn.setOnAction(e -> showGroupsPanel());
        membersBtn.setOnAction(e -> showMembersPanel());
        expensesBtn.setOnAction(e -> showExpensesPanel());
        balancesBtn.setOnAction(e -> showBalancesPanel());

        VBox leftNav = new VBox(10, groupsBtn, membersBtn, expensesBtn, balancesBtn);
        leftNav.setPadding(new Insets(10));
        leftNav.getStyleClass().add("left-nav");
        leftNav.setPrefWidth(120);

        mainContent = new VBox(10);
        mainContent.setPadding(new Insets(10));

        ScrollPane scrollPane = new ScrollPane(mainContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        root.setLeft(leftNav);
        root.setCenter(scrollPane);
        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        stage.setScene(scene);
        stage.show();

        showGroupsPanel();
    }

    private Button createNavButton(String text) {
        Button btn = new Button(text);
        btn.setPrefWidth(120);
        btn.getStyleClass().add("nav-button");
        return btn;
    }

    private void addGroup(TextField field) {
        String name = field.getText().trim();
        if (!name.isEmpty()) {
            if (DataStore.groups.stream().anyMatch(g -> g.getName().equals(name))) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Group already exists");
                alert.showAndWait();
                return;
            }
            Group g = new Group(name, java.time.LocalDate.now().toString(), false, new ArrayList<>(), new ArrayList<>());
            DataStore.groups.add(g);
            DataStore.saveData();
            groupListView.getItems().add(name + " (0 members)");
            groupListView.scrollTo(groupListView.getItems().size() - 1);
            groupListView.getSelectionModel().select(groupListView.getItems().size() - 1);
            field.clear();
        }
    }

    private void deleteGroup() {
        if (currentGroup != null) {
            DataStore.groups.remove(currentGroup);
            DataStore.history.add(currentGroup);
            DataStore.saveData();
            groupListView.getItems().removeIf(item -> item.startsWith(currentGroup.getName()));
            currentGroup = null;
        }
    }

    private void addMember(TextField field) {
        String name = field.getText().trim();
        if (!name.isEmpty()) {
            if (currentGroup.getMembers().stream().anyMatch(u -> u.getName().equals(name))) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Member already exists");
                alert.showAndWait();
                return;
            }
            User u = new User(name, 0.0);
            currentGroup.addMember(u);
            DataStore.saveData();
            memberListView.getItems().add(name);
            memberListView.scrollTo(memberListView.getItems().size() - 1);
            memberListView.getSelectionModel().select(memberListView.getItems().size() - 1);
            field.clear();
        }
    }

    private void deleteMember() {
        String selected = memberListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            currentGroup.getMembers().removeIf(u -> u.getName().equals(selected));
            DataStore.saveData();
            memberListView.getItems().remove(selected);
        }
    }

    private void addExpense(TextField amountField, VBox payerBox, ComboBox<String> categoryCombo, TextField descriptionField, DatePicker datePicker, VBox participantsBox) {
        try {
            int amount = Integer.parseInt(amountField.getText());
            if (amount <= 0) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Amount must be positive");
                alert.showAndWait();
                return;
            }
            String category = categoryCombo.getValue();
            String description = descriptionField.getText().trim();
            LocalDate expenseDate = datePicker.getValue();

            Map<User, Integer> payerMap = buildPayerMap(payerBox);
            if (payerMap.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Please select at least one payer");
                alert.showAndWait();
                return;
            }
            if (!validatePayerMap(payerMap, amount)) return;

            Map<User, Integer> shareMap = buildParticipantShareMap(participantsBox);
            if (shareMap.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Please select at least one participant");
                alert.showAndWait();
                return;
            }
            if (!validateParticipantShares(shareMap, amount)) return;

            Expense exp = new Expense(amount, payerMap, shareMap, category, description, expenseDate);
            currentGroup.addExpense(exp);
            DataStore.saveData();
            refreshExpenseList();
            amountField.clear();
            descriptionField.clear();
            resetPayerBox(payerBox);
            for (javafx.scene.Node node : participantsBox.getChildren()) {
                if (node instanceof HBox row) {
                    for (javafx.scene.Node child : row.getChildren()) {
                        if (child instanceof CheckBox cb) {
                            cb.setSelected(true);
                        } else if (child instanceof TextField tf) {
                            tf.setText("0");
                        }
                    }
                }
            }
        } catch (NumberFormatException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Invalid amount");
            alert.showAndWait();
        }
    }

    private Map<User, Integer> buildPayerMap(VBox payerBox) {
        Map<User, Integer> map = new HashMap<>();
        for (javafx.scene.Node node : payerBox.getChildren()) {
            if (node instanceof HBox row) {
                for (javafx.scene.Node child : row.getChildren()) {
                    if (child instanceof CheckBox cb && cb.isSelected()) {
                        String name = cb.getId();
                        User user = currentGroup.getMembers().stream()
                                .filter(u -> u.getName().equals(name)).findFirst().orElse(null);
                        if (user != null) {
                            int paid = 0;
                            for (javafx.scene.Node c2 : row.getChildren()) {
                                if (c2 instanceof TextField tf) {
                                    try {
                                        paid = Integer.parseInt(tf.getText());
                                    } catch (NumberFormatException ignored) {}
                                }
                            }
                            map.put(user, paid);
                        }
                    }
                }
            }
        }
        return map;
    }

    private boolean validatePayerMap(Map<User, Integer> payerMap, int expectedTotal) {
        int sum = payerMap.values().stream().mapToInt(Integer::intValue).sum();
        if (sum != expectedTotal) {
            Alert alert = new Alert(Alert.AlertType.WARNING,
                    "Payer amounts sum to " + sum + " NTD, but total expense is " + expectedTotal + " NTD");
            alert.showAndWait();
            return false;
        }
        return true;
    }

    private void resetPayerBox(VBox payerBox) {
        for (javafx.scene.Node node : payerBox.getChildren()) {
            if (node instanceof HBox row) {
                for (javafx.scene.Node child : row.getChildren()) {
                    if (child instanceof CheckBox cb) {
                        cb.setSelected(false);
                    } else if (child instanceof TextField tf) {
                        tf.setText("0");
                    }
                }
            }
        }
    }

    private Map<User, Integer> buildParticipantShareMap(VBox participantsBox) {
        Map<User, Integer> map = new HashMap<>();
        for (javafx.scene.Node node : participantsBox.getChildren()) {
            if (node instanceof HBox row) {
                for (javafx.scene.Node child : row.getChildren()) {
                    if (child instanceof CheckBox cb && cb.isSelected()) {
                        String name = cb.getId();
                        User user = currentGroup.getMembers().stream()
                                .filter(u -> u.getName().equals(name)).findFirst().orElse(null);
                        if (user != null) {
                            int share = 0;
                            for (javafx.scene.Node c2 : row.getChildren()) {
                                if (c2 instanceof TextField tf) {
                                    try {
                                        share = Integer.parseInt(tf.getText());
                                    } catch (NumberFormatException ignored) {}
                                }
                            }
                            map.put(user, share);
                        }
                    }
                }
            }
        }
        return map;
    }

    private boolean validateParticipantShares(Map<User, Integer> shareMap, int expectedTotal) {
        int sum = shareMap.values().stream().mapToInt(Integer::intValue).sum();
        if (sum != expectedTotal) {
            Alert alert = new Alert(Alert.AlertType.WARNING,
                    "Participant shares sum to " + sum + " NTD, but total expense is " + expectedTotal + " NTD");
            alert.showAndWait();
            return false;
        }
        return true;
    }

    private void resetParticipantSharesBox(VBox participantsBox) {
        for (javafx.scene.Node node : participantsBox.getChildren()) {
            if (node instanceof HBox row) {
                for (javafx.scene.Node child : row.getChildren()) {
                    if (child instanceof CheckBox cb) {
                        cb.setSelected(false);
                    } else if (child instanceof TextField tf) {
                        tf.setText("0");
                    }
                }
            }
        }
    }

    private void autoDistributeParticipantShares(VBox participantsBox, int total) {
        if (total <= 0) return;
        List<HBox> checkedRows = new ArrayList<>();
        List<HBox> allRows = new ArrayList<>();
        for (javafx.scene.Node node : participantsBox.getChildren()) {
            if (node instanceof HBox row) {
                allRows.add(row);
                for (javafx.scene.Node child : row.getChildren()) {
                    if (child instanceof CheckBox cb && cb.isSelected()) {
                        checkedRows.add(row);
                        break;
                    }
                }
            }
        }
        for (HBox row : allRows) {
            if (!checkedRows.contains(row)) {
                for (javafx.scene.Node child : row.getChildren()) {
                    if (child instanceof TextField tf) tf.setText("0");
                }
            }
        }
        if (checkedRows.isEmpty()) return;
        int share = total / checkedRows.size();
        int remainder = total - share * checkedRows.size();
        for (int i = 0; i < checkedRows.size(); i++) {
            for (javafx.scene.Node child : checkedRows.get(i).getChildren()) {
                if (child instanceof TextField tf) {
                    tf.setText(String.valueOf(share + (i < remainder ? 1 : 0)));
                }
            }
        }
    }

    private void autoDistribute(VBox payerBox, TextField amountField) {
        try {
            int total = Integer.parseInt(amountField.getText());
            if (total <= 0) return;
            List<HBox> checkedRows = new ArrayList<>();
            List<HBox> allRows = new ArrayList<>();
            for (javafx.scene.Node node : payerBox.getChildren()) {
                if (node instanceof HBox row) {
                    allRows.add(row);
                    for (javafx.scene.Node child : row.getChildren()) {
                        if (child instanceof CheckBox cb && cb.isSelected()) {
                            checkedRows.add(row);
                            break;
                        }
                    }
                }
            }
            for (HBox row : allRows) {
                if (!checkedRows.contains(row)) {
                    for (javafx.scene.Node child : row.getChildren()) {
                        if (child instanceof TextField tf) tf.setText("0");
                    }
                }
            }
            if (checkedRows.isEmpty()) return;
            int share = total / checkedRows.size();
            int remainder = total - share * checkedRows.size();
            for (int i = 0; i < checkedRows.size(); i++) {
                for (javafx.scene.Node child : checkedRows.get(i).getChildren()) {
                    if (child instanceof TextField tf) {
                        tf.setText(String.valueOf(share + (i < remainder ? 1 : 0)));
                    }
                }
            }
        } catch (NumberFormatException ignored) {}
    }

    private void setupExpenseTableColumns(TableView<Expense> table) {
        table.getColumns().clear();
        TableColumn<Expense, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFormattedDate()));
        dateCol.setSortType(TableColumn.SortType.DESCENDING);
        dateCol.setPrefWidth(80);

        TableColumn<Expense, Integer> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().getAmount()));
        amountCol.setCellFactory(col -> new TableCell<Expense, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item + " NTD");
            }
        });
        amountCol.setPrefWidth(85);

        TableColumn<Expense, String> paidByCol = new TableColumn<>("Paid By");
        paidByCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getPaidByNames()));
        paidByCol.setPrefWidth(85);

        TableColumn<Expense, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getCategory()));
        catCol.setPrefWidth(85);

        TableColumn<Expense, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(cd -> {
            String d = cd.getValue().getDescription();
            return new SimpleStringProperty(d.isEmpty() ? "—" : d);
        });
        descCol.setPrefWidth(200);

        table.getColumns().addAll(dateCol, amountCol, paidByCol, catCol, descCol);
        table.getSortOrder().add(dateCol);
    }

    private void refreshExpenseList() {
        expenseListView.getItems().clear();
        if (currentGroup != null) {
            List<Expense> sorted = currentGroup.getExpenses().stream()
                    .sorted((a, b) -> b.getExpenseDate().compareTo(a.getExpenseDate()))
                    .toList();
            expenseListView.getItems().addAll(sorted);
            expenseListView.sort();
        }
    }

    private void deleteExpense() {
        Expense selected = expenseListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            currentGroup.getExpenses().remove(selected);
            DataStore.saveData();
            refreshExpenseList();
        }
    }

    private void showGroupsPanel() {
        mainContent.getChildren().clear();

        Label title = new Label("Groups");
        title.getStyleClass().add("panel-title");

        groupListView.getItems().clear();
        for (Group g : DataStore.groups) {
            groupListView.getItems().add(g.getName() + " (" + g.getMembers().size() + " members)");
        }
        groupListView.setPrefHeight(200);
        groupListView.getStyleClass().add("list-view-large");

        groupListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                String groupName = newVal.split(" \\(")[0];
                currentGroup = DataStore.groups.stream()
                        .filter(g -> g.getName().equals(groupName))
                        .findFirst().orElse(null);
            }
        });

        groupListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && currentGroup != null) {
                if (currentGroup.getMembers().isEmpty()) {
                    showMembersPanel();
                } else {
                    showExpensesPanel();
                }
            }
        });

        TextField newGroupField = new TextField();
        newGroupField.setPromptText("New group name");

        Button addGroupBtn = new Button("Add Group");
        addGroupBtn.getStyleClass().add("save-button");
        Button deleteGroupBtn = new Button("Delete Group");
        deleteGroupBtn.getStyleClass().add("delete-button");
        Button renameGroupBtn = new Button("Rename Group");

        HBox inputRow = new HBox(10, newGroupField, addGroupBtn);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        addGroupBtn.setOnAction(e -> addGroup(newGroupField));
        newGroupField.setOnAction(e -> addGroup(newGroupField));

        deleteGroupBtn.setOnAction(e -> deleteGroup());
        deleteGroupBtn.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) deleteGroup();
        });

        renameGroupBtn.setOnAction(e -> {
            if (currentGroup == null) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Please select a group first");
                alert.showAndWait();
                return;
            }
            TextInputDialog dialog = new TextInputDialog(currentGroup.getName());
            dialog.setTitle("Rename Group");
            dialog.setHeaderText("Enter a new name for the group");
            dialog.setContentText("New name:");
            dialog.showAndWait().ifPresent(newName -> {
                String trimmed = newName.trim();
                if (trimmed.isEmpty()) return;
                if (trimmed.equals(currentGroup.getName())) return;
                boolean exists = DataStore.groups.stream()
                        .anyMatch(g -> g.getName().equals(trimmed));
                if (exists) {
                    Alert alert = new Alert(Alert.AlertType.WARNING, "A group with that name already exists");
                    alert.showAndWait();
                    return;
                }
                currentGroup.setName(trimmed);
                DataStore.saveData();
                showGroupsPanel();
            });
        });

        Button selectBtn = new Button("Select Group");
        selectBtn.setOnAction(e -> {
            if (currentGroup != null) {
                showMembersPanel();
            } else {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Please select a group first");
                alert.showAndWait();
            }
        });
        selectBtn.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER && currentGroup != null) {
                showMembersPanel();
            }
        });

        Label groupHint = new Label("Please choose a group to continue");
        groupHint.getStyleClass().add("hint-label");

        mainContent.getChildren().addAll(title, groupListView, groupHint, inputRow, renameGroupBtn, deleteGroupBtn, selectBtn);
    }

    private void showMembersPanel() {
        mainContent.getChildren().clear();

        if (currentGroup == null) {
            Label noGroup = new Label("No group selected");
            Button backBtn = new Button("Back to Groups");
            backBtn.setOnAction(e -> showGroupsPanel());
            backBtn.setOnKeyPressed(e -> {
                if (e.getCode() == javafx.scene.input.KeyCode.ENTER) showGroupsPanel();
            });
            mainContent.getChildren().addAll(noGroup, backBtn);
            return;
        }

        Label title = new Label("Members: " + currentGroup.getName());
        title.getStyleClass().add("panel-title");

        memberListView.getItems().clear();
        for (User u : currentGroup.getMembers()) {
            memberListView.getItems().add(u.getName());
        }
        memberListView.setPrefHeight(200);
        memberListView.getStyleClass().add("list-view-large");

        memberListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String selected = memberListView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    User member = currentGroup.getMembers().stream()
                            .filter(u -> u.getName().equals(selected)).findFirst().orElse(null);
                    if (member != null) showMemberDetail(member);
                }
            }
        });

        TextField newMemberField = new TextField();
        newMemberField.setPromptText("Member name");

        Button addMemberBtn = new Button("Add Member");
        addMemberBtn.getStyleClass().add("save-button");
        Button deleteMemberBtn = new Button("Remove Member");
        deleteMemberBtn.getStyleClass().add("delete-button");
        Button renameMemberBtn = new Button("Rename Member");
        Button backBtn = new Button("Back to Groups");

        HBox inputRow = new HBox(10, newMemberField, addMemberBtn);

        addMemberBtn.setOnAction(e -> addMember(newMemberField));
        newMemberField.setOnAction(e -> addMember(newMemberField));

        deleteMemberBtn.setOnAction(e -> deleteMember());
        deleteMemberBtn.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) deleteMember();
        });

        renameMemberBtn.setOnAction(e -> {
            String selected = memberListView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Please select a member first");
                alert.showAndWait();
                return;
            }
            User oldUser = currentGroup.getMembers().stream()
                    .filter(u -> u.getName().equals(selected)).findFirst().orElse(null);
            if (oldUser == null) return;

            TextInputDialog dialog = new TextInputDialog(oldUser.getName());
            dialog.setTitle("Rename Member");
            dialog.setHeaderText("Enter a new name for " + oldUser.getName());
            dialog.setContentText("New name:");
            dialog.showAndWait().ifPresent(newName -> {
                String trimmed = newName.trim();
                if (trimmed.isEmpty()) return;
                if (trimmed.equals(oldUser.getName())) return;
                boolean exists = currentGroup.getMembers().stream()
                        .anyMatch(u -> u.getName().equals(trimmed));
                if (exists) {
                    Alert alert = new Alert(Alert.AlertType.WARNING, "A member with that name already exists");
                    alert.showAndWait();
                    return;
                }

                User newUser = new User(trimmed, 0.0);

                for (Expense exp : currentGroup.getExpenses()) {
                    Map<User, Integer> paid = exp.getPaidByAmounts();
                    if (paid.containsKey(oldUser)) {
                        paid.put(newUser, paid.remove(oldUser));
                    }
                    Map<User, Integer> shares = exp.getParticipantShares();
                    if (shares.containsKey(oldUser)) {
                        shares.put(newUser, shares.remove(oldUser));
                    }
                }

                int idx = currentGroup.getMembers().indexOf(oldUser);
                if (idx >= 0) {
                    currentGroup.getMembers().set(idx, newUser);
                }

                DataStore.saveData();
                showMembersPanel();
            });
        });

        backBtn.setOnAction(e -> showGroupsPanel());
        backBtn.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) showGroupsPanel();
        });

        Label memberHint = new Label("Double click a member to see more details");
        memberHint.getStyleClass().add("hint-label");

        mainContent.getChildren().addAll(title, memberListView, memberHint, inputRow, renameMemberBtn, deleteMemberBtn, backBtn);
    }

    private void showExpensesPanel() {
        mainContent.getChildren().clear();

        if (currentGroup == null) {
            Label noGroup = new Label("No group selected");
            Button backBtn = new Button("Back to Groups");
            backBtn.setOnAction(e -> showGroupsPanel());
            backBtn.setOnKeyPressed(e -> {
                if (e.getCode() == javafx.scene.input.KeyCode.ENTER) showGroupsPanel();
            });
            mainContent.getChildren().addAll(noGroup, backBtn);
            return;
        }

        Label title = new Label("Expenses: " + currentGroup.getName());
        title.getStyleClass().add("panel-title");

        setupExpenseTableColumns(expenseListView);
        expenseListView.setPrefHeight(250);
        expenseListView.getStyleClass().add("table-view-large");
        expenseListView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        refreshExpenseList();

        expenseListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Expense selected = expenseListView.getSelectionModel().getSelectedItem();
                if (selected != null) showExpenseDetail(selected);
            }
        });

        TextField amountField = new TextField();
        amountField.setPromptText("Amount");

        VBox payerBox = new VBox(5);
        payerBox.setPadding(new Insets(5));
        payerBox.getStyleClass().add("bordered-box");
        Label payerLabel = new Label("Paid by:");
        payerBox.getChildren().add(payerLabel);
        for (User u : currentGroup.getMembers()) {
            CheckBox cb = new CheckBox(u.getName());
            cb.setId(u.getName());
            TextField tf = new TextField("0");
            tf.setPrefWidth(80);
            HBox row = new HBox(10, cb, tf);
            payerBox.getChildren().add(row);
        }

        ComboBox<String> categoryCombo = new ComboBox<>();
        categoryCombo.getItems().addAll("Food", "Utilities", "Entertainment", "Transportation", "Settlement", "Other");
        categoryCombo.setValue("Other");

        TextField descriptionField = new TextField();
        descriptionField.setPromptText("Description (optional)");

        DatePicker datePicker = new DatePicker(LocalDate.now());

        VBox participantsBox = new VBox(5);
        participantsBox.setPadding(new Insets(5));
        participantsBox.getStyleClass().add("bordered-box");
        Label partLabel = new Label("Participants:");
        participantsBox.getChildren().add(partLabel);

        for (User u : currentGroup.getMembers()) {
            CheckBox cb = new CheckBox(u.getName());
            cb.setSelected(true);
            cb.setId(u.getName());
            TextField tf = new TextField("0");
            tf.setPrefWidth(80);
            HBox row = new HBox(10, cb, tf);
            participantsBox.getChildren().add(row);
        }

        Button addExpenseBtn = new Button("Add Expense");
        addExpenseBtn.getStyleClass().add("save-button");
        Button autoSplitBtn = new Button("Auto Split");
        Button deleteExpenseBtn = new Button("Delete Expense");
        deleteExpenseBtn.getStyleClass().add("delete-button");
        Button backBtn = new Button("Back to Groups");

        Button partAutoSplitBtn = new Button("Auto Split");

        addExpenseBtn.setOnAction(e -> addExpense(amountField, payerBox, categoryCombo, descriptionField, datePicker, participantsBox));
        amountField.setOnAction(e -> addExpense(amountField, payerBox, categoryCombo, descriptionField, datePicker, participantsBox));
        descriptionField.setOnAction(e -> addExpense(amountField, payerBox, categoryCombo, descriptionField, datePicker, participantsBox));

        autoSplitBtn.setOnAction(e -> autoDistribute(payerBox, amountField));

        partAutoSplitBtn.setOnAction(e -> {
            try {
                int total = Integer.parseInt(amountField.getText());
                autoDistributeParticipantShares(participantsBox, total);
            } catch (NumberFormatException ignored) {}
        });

        for (javafx.scene.Node node : payerBox.getChildren()) {
            if (node instanceof HBox row) {
                for (javafx.scene.Node child : row.getChildren()) {
                    if (child instanceof CheckBox cb) {
                        cb.selectedProperty().addListener((obs, old, val) -> autoDistribute(payerBox, amountField));
                    }
                }
            }
        }

        for (javafx.scene.Node node : participantsBox.getChildren()) {
            if (node instanceof HBox row) {
                for (javafx.scene.Node child : row.getChildren()) {
                    if (child instanceof CheckBox cb) {
                        cb.selectedProperty().addListener((obs, old, val) -> {
                            try {
                                int total = Integer.parseInt(amountField.getText());
                                autoDistributeParticipantShares(participantsBox, total);
                            } catch (NumberFormatException ignored) {}
                        });
                    }
                }
            }
        }

        amountField.textProperty().addListener((obs, old, val) -> {
            if (!val.equals(old)) {
                try {
                    int total = Integer.parseInt(val);
                    if (total > 0) {
                        autoDistributeParticipantShares(participantsBox, total);
                    }
                } catch (NumberFormatException ignored) {}
            }
        });

        deleteExpenseBtn.setOnAction(e -> deleteExpense());
        deleteExpenseBtn.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) deleteExpense();
        });

        backBtn.setOnAction(e -> showGroupsPanel());
        backBtn.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) showGroupsPanel();
        });

        Label expenseHint = new Label("Double click an expense to see more details");
        expenseHint.getStyleClass().add("hint-label");

        VBox form = new VBox(10, amountField, payerBox, autoSplitBtn, categoryCombo, descriptionField, datePicker, participantsBox, partAutoSplitBtn, addExpenseBtn);

        mainContent.getChildren().addAll(title, expenseListView, expenseHint, form, deleteExpenseBtn, backBtn);
    }

    private void showBalancesPanel() {
        mainContent.getChildren().clear();

        if (currentGroup == null) {
            Label noGroup = new Label("No group selected");
            Button backBtn = new Button("Back to Groups");
            backBtn.setOnAction(e -> showGroupsPanel());
            backBtn.setOnKeyPressed(e -> {
                if (e.getCode() == javafx.scene.input.KeyCode.ENTER) showGroupsPanel();
            });
            mainContent.getChildren().addAll(noGroup, backBtn);
            return;
        }

        Label title = new Label("Balances: " + currentGroup.getName());
        title.getStyleClass().add("panel-title");

        balancePanel.setPrefHeight(200);
        ScrollPane balanceScroll = new ScrollPane(balancePanel);
        balanceScroll.setFitToWidth(true);
        balanceScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        Button calcBtn = new Button("Calculate Balances");
        Button simplifyBtn = new Button("Simplify Debts");
        Button settleBtn = new Button("Settle Balance");
        Button backBtn = new Button("Back to Groups");

        calcBtn.setOnAction(e -> calculateBalances(false));
        calcBtn.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                calcBtn.fire();
            }
        });

        simplifyBtn.setOnAction(e -> calculateBalances(true));
        simplifyBtn.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                simplifyBtn.fire();
            }
        });

        settleBtn.setOnAction(e -> showSettleDialog());
        settleBtn.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) showSettleDialog();
        });

        backBtn.setOnAction(e -> showGroupsPanel());
        backBtn.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) showGroupsPanel();
        });

        mainContent.getChildren().addAll(title, balanceScroll, calcBtn, simplifyBtn, settleBtn, backBtn);
        calculateBalances(false);
    }

    private void calculateBalances(boolean simplify) {
        balancePanel.getChildren().clear();
        Map<User, Integer> balances = BillSplitterService.calculateBalances(currentGroup);
        if (simplify) {
            Label header = new Label("Settlement Plan:");
            header.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            balancePanel.getChildren().add(header);
            List<String> debts = BillSplitterService.simplifyDebts(balances);
            if (debts.isEmpty()) {
                balancePanel.getChildren().add(new Label("All settled up!"));
            } else {
                for (String debt : debts) {
                    balancePanel.getChildren().add(new Label(debt));
                }
            }
        } else {
            Label header = new Label("Individual Balances:");
            header.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            balancePanel.getChildren().add(header);

            for (Map.Entry<User, Integer> entry : balances.entrySet()) {
                int amount = entry.getValue();
                Text namePart = new Text(entry.getKey().getName() + ": ");
                Text amountPart = new Text(String.valueOf(amount));
                Text suffixPart;
                if (amount >= 0) {
                    amountPart.setFill(javafx.scene.paint.Color.GREEN);
                    suffixPart = new Text(" NTD (is owed)");
                } else {
                    amountPart.setFill(javafx.scene.paint.Color.RED);
                    suffixPart = new Text(" NTD (owes)");
                }
                TextFlow line = new TextFlow(namePart, amountPart, suffixPart);
                balancePanel.getChildren().add(line);
            }

            int total = BillSplitterService.getTotalGroupSpent(currentGroup);
            Label totalLabel = new Label("\nTotal Group Spending: " + total + " NTD");
            totalLabel.setStyle("-fx-font-weight: bold;");
            balancePanel.getChildren().add(totalLabel);

            Map<String, Double> catTotals = BillSplitterService.getCategoryTotal(currentGroup);
            Label catHeader = new Label("  Per Category:");
            balancePanel.getChildren().add(catHeader);
            for (Map.Entry<String, Double> cat : catTotals.entrySet()) {
                if ("Settlement".equals(cat.getKey())) continue;
                balancePanel.getChildren().add(new Label("    \u2022 " + cat.getKey() + ": " + cat.getValue().intValue() + " NTD"));
            }
        }
    }

    private void showSettleDialog() {
        Stage settleStage = new Stage();
        settleStage.setTitle("Settle Balance");
        settleStage.setWidth(350);
        settleStage.setHeight(250);

        VBox root = new VBox(10);
        root.setPadding(new Insets(15));

        Label titleLabel = new Label("Record Settlement");
        titleLabel.getStyleClass().add("dialog-title");

        List<String> memberNames = currentGroup.getMembers().stream().map(User::getName).toList();

        ComboBox<String> payerCombo = new ComboBox<>();
        payerCombo.getItems().addAll(memberNames);
        payerCombo.setPromptText("Payer");

        ComboBox<String> receiverCombo = new ComboBox<>();
        receiverCombo.getItems().addAll(memberNames);
        receiverCombo.setPromptText("Receiver");

        TextField amountField = new TextField();
        amountField.setPromptText("Amount");

        Button confirmBtn = new Button("Confirm");

        confirmBtn.setOnAction(e -> confirmSettlement(payerCombo, receiverCombo, amountField, settleStage));
        amountField.setOnAction(e -> confirmSettlement(payerCombo, receiverCombo, amountField, settleStage));

        root.getChildren().addAll(titleLabel, payerCombo, receiverCombo, amountField, confirmBtn);
        Scene settleScene = new Scene(root);
        settleScene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        settleStage.setScene(settleScene);
        settleStage.show();
    }

    private void confirmSettlement(ComboBox<String> payerCombo, ComboBox<String> receiverCombo, TextField amountField, Stage settleStage) {
        String payerName = payerCombo.getValue();
        String receiverName = receiverCombo.getValue();

        if (payerName == null || receiverName == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Please select both payer and receiver");
            alert.showAndWait();
            return;
        }

        if (payerName.equals(receiverName)) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Payer and receiver must be different");
            alert.showAndWait();
            return;
        }

        try {
            int amount = Integer.parseInt(amountField.getText());
            if (amount <= 0) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Amount must be positive");
                alert.showAndWait();
                return;
            }

            User payer = currentGroup.getMembers().stream()
                    .filter(u -> u.getName().equals(payerName)).findFirst().orElse(null);
            User receiver = currentGroup.getMembers().stream()
                    .filter(u -> u.getName().equals(receiverName)).findFirst().orElse(null);

            if (payer == null || receiver == null) return;

            Map<User, Integer> payerMap = new HashMap<>();
            payerMap.put(payer, amount);
            Map<User, Integer> settleShares = new HashMap<>();
            settleShares.put(receiver, amount);
            Expense settlement = new Expense(amount, payerMap, settleShares, "Settlement", "Settlement", LocalDate.now());
            currentGroup.addExpense(settlement);
            DataStore.saveData();
            refreshExpenseList();

            settleStage.close();
            calculateBalances(false);
        } catch (NumberFormatException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Invalid amount");
            alert.showAndWait();
        }
    }

    private void showExpenseDetail(Expense expense) {
        Stage detailStage = new Stage();
        detailStage.setTitle("Expense Details");
        detailStage.setWidth(500);
        detailStage.setHeight(600);

        VBox root = new VBox(10);
        root.setPadding(new Insets(15));

        Label titleLabel = new Label("Expense Details");
        titleLabel.getStyleClass().add("panel-title");

        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(10);
        infoGrid.setVgap(5);
        infoGrid.setPadding(new Insets(5));

        infoGrid.add(new Label("Date:"), 0, 0);
        DatePicker detailDatePicker = new DatePicker(expense.getExpenseDate());
        infoGrid.add(detailDatePicker, 1, 0);

        infoGrid.add(new Label("Amount:"), 0, 1);
        TextField detailAmountField = new TextField(String.valueOf(expense.getAmount()));
        infoGrid.add(detailAmountField, 1, 1);

        infoGrid.add(new Label("Category:"), 0, 2);
        ComboBox<String> detailCategoryCombo = new ComboBox<>();
        detailCategoryCombo.getItems().addAll("Food", "Utilities", "Entertainment", "Transportation", "Settlement", "Other");
        detailCategoryCombo.setValue(expense.getCategory());
        infoGrid.add(detailCategoryCombo, 1, 2);

        infoGrid.add(new Label("Description:"), 0, 3);
        TextField detailDescField = new TextField(expense.getDescription());
        infoGrid.add(detailDescField, 1, 3);

        VBox detailPayerBox = new VBox(5);
        detailPayerBox.setPadding(new Insets(5));
        detailPayerBox.getStyleClass().add("bordered-box");
        Label detailPayerLabel = new Label("Paid by:");
        detailPayerBox.getChildren().add(detailPayerLabel);
        for (User u : currentGroup.getMembers()) {
            CheckBox cb = new CheckBox(u.getName());
            cb.setId(u.getName());
            Integer paid = expense.getPaidByAmounts().get(u);
            cb.setSelected(paid != null);
            TextField tf = new TextField(paid != null ? String.valueOf(paid) : "0");
            tf.setPrefWidth(80);
            HBox row = new HBox(10, cb, tf);
            detailPayerBox.getChildren().add(row);
        }

        VBox participantsBox = new VBox(5);
        participantsBox.setPadding(new Insets(5));
        participantsBox.getStyleClass().add("bordered-box");
        Label partLabel = new Label("Participants:");
        participantsBox.getChildren().add(partLabel);

        Map<User, Integer> existingShares = expense.getParticipantShares();
        for (User u : currentGroup.getMembers()) {
            CheckBox cb = new CheckBox(u.getName());
            cb.setId(u.getName());
            Integer share = existingShares.get(u);
            cb.setSelected(share != null);
            TextField tf = new TextField(share != null ? String.valueOf(share) : "0");
            tf.setPrefWidth(80);
            HBox row = new HBox(10, cb, tf);
            participantsBox.getChildren().add(row);
        }

        Label breakdownLabel = new Label("Charge Breakdown:");
        breakdownLabel.getStyleClass().add("breakdown-label");

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<User, Integer> entry : existingShares.entrySet()) {
            sb.append(String.format("  %s: %d NTD\n", entry.getKey().getName(), entry.getValue()));
        }
        TextArea breakdownArea = new TextArea(sb.toString().trim());
        breakdownArea.setEditable(false);
        breakdownArea.setPrefHeight(100);

        HBox buttonRow = new HBox(10);
        Button saveBtn = new Button("Save");
        saveBtn.getStyleClass().add("save-button");
        Button deleteBtn = new Button("Delete");
        deleteBtn.getStyleClass().add("delete-button");
        Button closeBtn = new Button("Close");
        buttonRow.getChildren().addAll(saveBtn, deleteBtn, closeBtn);

        saveBtn.setOnAction(e -> {
            try {
                int amount = Integer.parseInt(detailAmountField.getText());
                if (amount <= 0) {
                    Alert alert = new Alert(Alert.AlertType.WARNING, "Amount must be positive");
                    alert.showAndWait();
                    return;
                }
                Map<User, Integer> payerMap = buildPayerMap(detailPayerBox);
                if (payerMap.isEmpty()) {
                    Alert alert = new Alert(Alert.AlertType.WARNING, "Please select at least one payer");
                    alert.showAndWait();
                    return;
                }
                if (!validatePayerMap(payerMap, amount)) return;

                Map<User, Integer> shareMap = buildParticipantShareMap(participantsBox);
                if (shareMap.isEmpty()) {
                    Alert alert = new Alert(Alert.AlertType.WARNING, "Must have at least one participant");
                    alert.showAndWait();
                    return;
                }
                if (!validateParticipantShares(shareMap, amount)) return;

                expense.setAmount(amount);
                expense.setPaidByAmounts(payerMap);
                expense.setParticipantShares(shareMap);
                expense.setCategory(detailCategoryCombo.getValue());
                expense.setDescription(detailDescField.getText().trim());
                expense.setExpenseDate(detailDatePicker.getValue());
                DataStore.saveData();
                refreshExpenseList();
                detailStage.close();
            } catch (NumberFormatException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Invalid amount");
                alert.showAndWait();
            }
        });

        deleteBtn.setOnAction(e -> {
            currentGroup.getExpenses().remove(expense);
            DataStore.saveData();
            refreshExpenseList();
            detailStage.close();
        });

        closeBtn.setOnAction(e -> detailStage.close());

        root.getChildren().addAll(titleLabel, infoGrid, detailPayerBox, new Label("Charge Breakdown:"), breakdownLabel, breakdownArea, participantsBox, buttonRow);
        ScrollPane detailScroll = new ScrollPane(root);
        detailScroll.setFitToWidth(true);
        detailScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        Scene detailScene = new Scene(detailScroll);
        detailScene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        detailStage.setScene(detailScene);
        detailStage.show();
    }

    private void showMemberDetail(User member) {
        Stage detailStage = new Stage();
        detailStage.setTitle("Member Details: " + member.getName());
        detailStage.setWidth(600);
        detailStage.setHeight(600);

        VBox root = new VBox(10);
        root.setPadding(new Insets(15));

        Label titleLabel = new Label("Expenses: " + member.getName());
        titleLabel.getStyleClass().add("panel-title");

        TableView<Expense> memberExpenseListView = new TableView<>();
        memberExpenseListView.setPrefHeight(200);
        memberExpenseListView.getStyleClass().add("table-view-large");
        memberExpenseListView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        Label balanceLabel = new Label();
        balanceLabel.getStyleClass().add("breakdown-label");

        TextArea memberSpendingArea = new TextArea();
        memberSpendingArea.setEditable(false);
        memberSpendingArea.setPrefHeight(200);

        Runnable refreshList = () -> {
            memberExpenseListView.getItems().clear();
            List<Expense> sorted = currentGroup.getExpenses().stream()
                    .filter(e -> e.getParticipants().contains(member) || e.getPaidByAmounts().containsKey(member))
                    .sorted((a, b) -> b.getExpenseDate().compareTo(a.getExpenseDate()))
                    .toList();

            int totalShare = 0;
            Map<String, Integer> catShare = new java.util.HashMap<>();
            for (Expense e : sorted) {
                Integer share = e.getParticipantShares().get(member);
                if (share == null) continue;
                totalShare += share;
                catShare.merge(e.getCategory(), share, Integer::sum);
            }

            int totalPaid = 0;
            for (Expense e : sorted) {
                Integer paid = e.getPaidByAmounts().get(member);
                if (paid != null) totalPaid += paid;
            }

            StringBuilder spendingSb = new StringBuilder("Spending Summary:\n");
            spendingSb.append(String.format("  Total Share: %d NTD\n", totalShare));
            spendingSb.append(String.format("  Has Paid: %d NTD\n", totalPaid));
            spendingSb.append("  Per Category:\n");
            for (Map.Entry<String, Integer> cat : catShare.entrySet()) {
                if ("Settlement".equals(cat.getKey())) continue;
                spendingSb.append(String.format("    • %s: %d NTD\n", cat.getKey(), cat.getValue()));
            }
            memberSpendingArea.setText(spendingSb.toString().trim());

            memberExpenseListView.getItems().addAll(sorted);
        };
        setupExpenseTableColumns(memberExpenseListView);
        refreshList.run();

        memberExpenseListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Expense selected = memberExpenseListView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    Runnable originalRefresh = () -> {
                        refreshList.run();
                        updateBalanceLabel(member, balanceLabel);
                    };
                    showExpenseDetailForExpense(selected, originalRefresh);
                }
            }
        });

        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> detailStage.close());

        HBox buttonRow = new HBox(10, closeBtn);

        updateBalanceLabel(member, balanceLabel);

        root.getChildren().addAll(titleLabel, memberExpenseListView, balanceLabel, memberSpendingArea, buttonRow);
        ScrollPane memberScroll = new ScrollPane(root);
        memberScroll.setFitToWidth(true);
        memberScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        Scene memberScene = new Scene(memberScroll);
        memberScene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        detailStage.setScene(memberScene);
        detailStage.show();
    }

    private void updateBalanceLabel(User member, Label balanceLabel) {
        int balance = BillSplitterService.getUserBalance(currentGroup, member);
        balanceLabel.getStyleClass().removeAll("balance-positive", "balance-negative");
        if (balance >= 0) {
            balanceLabel.setText(String.format("Total Balance: %d NTD (is owed)", balance));
            balanceLabel.getStyleClass().add("balance-positive");
        } else {
            balanceLabel.setText(String.format("Total Balance: %d NTD (owes)", balance));
            balanceLabel.getStyleClass().add("balance-negative");
        }
    }

    private void showExpenseDetailForExpense(Expense expense, Runnable onRefresh) {
        Stage detailStage = new Stage();
        detailStage.setTitle("Expense Details");
        detailStage.setWidth(500);
        detailStage.setHeight(600);

        VBox root = new VBox(10);
        root.setPadding(new Insets(15));

        Label titleLabel = new Label("Expense Details");
        titleLabel.getStyleClass().add("panel-title");

        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(10);
        infoGrid.setVgap(5);
        infoGrid.setPadding(new Insets(5));

        infoGrid.add(new Label("Date:"), 0, 0);
        DatePicker detailDatePicker = new DatePicker(expense.getExpenseDate());
        infoGrid.add(detailDatePicker, 1, 0);

        infoGrid.add(new Label("Amount:"), 0, 1);
        TextField detailAmountField = new TextField(String.valueOf(expense.getAmount()));
        infoGrid.add(detailAmountField, 1, 1);

        infoGrid.add(new Label("Category:"), 0, 2);
        ComboBox<String> detailCategoryCombo = new ComboBox<>();
        detailCategoryCombo.getItems().addAll("Food", "Utilities", "Entertainment", "Transportation", "Settlement", "Other");
        detailCategoryCombo.setValue(expense.getCategory());
        infoGrid.add(detailCategoryCombo, 1, 2);

        infoGrid.add(new Label("Description:"), 0, 3);
        TextField detailDescField = new TextField(expense.getDescription());
        infoGrid.add(detailDescField, 1, 3);

        VBox detailPayerBox = new VBox(5);
        detailPayerBox.setPadding(new Insets(5));
        detailPayerBox.getStyleClass().add("bordered-box");
        Label detailPayerLabel = new Label("Paid by:");
        detailPayerBox.getChildren().add(detailPayerLabel);
        for (User u : currentGroup.getMembers()) {
            CheckBox cb = new CheckBox(u.getName());
            cb.setId(u.getName());
            Integer paid = expense.getPaidByAmounts().get(u);
            cb.setSelected(paid != null);
            TextField tf = new TextField(paid != null ? String.valueOf(paid) : "0");
            tf.setPrefWidth(80);
            HBox row = new HBox(10, cb, tf);
            detailPayerBox.getChildren().add(row);
        }

        VBox participantsBox = new VBox(5);
        participantsBox.setPadding(new Insets(5));
        participantsBox.getStyleClass().add("bordered-box");
        Label partLabel = new Label("Participants:");
        participantsBox.getChildren().add(partLabel);

        Map<User, Integer> existingShares = expense.getParticipantShares();
        for (User u : currentGroup.getMembers()) {
            CheckBox cb = new CheckBox(u.getName());
            cb.setId(u.getName());
            Integer share = existingShares.get(u);
            cb.setSelected(share != null);
            TextField tf = new TextField(share != null ? String.valueOf(share) : "0");
            tf.setPrefWidth(80);
            HBox row = new HBox(10, cb, tf);
            participantsBox.getChildren().add(row);
        }

        Label breakdownLabel = new Label("Charge Breakdown:");
        breakdownLabel.getStyleClass().add("breakdown-label");

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<User, Integer> entry : existingShares.entrySet()) {
            sb.append(String.format("  %s: %d NTD\n", entry.getKey().getName(), entry.getValue()));
        }
        TextArea breakdownArea = new TextArea(sb.toString().trim());
        breakdownArea.setEditable(false);
        breakdownArea.setPrefHeight(100);

        HBox buttonRow = new HBox(10);
        Button saveBtn = new Button("Save");
        saveBtn.getStyleClass().add("save-button");
        Button deleteBtn = new Button("Delete");
        deleteBtn.getStyleClass().add("delete-button");
        Button closeBtn = new Button("Close");
        buttonRow.getChildren().addAll(saveBtn, deleteBtn, closeBtn);

        saveBtn.setOnAction(e -> {
            try {
                int amount = Integer.parseInt(detailAmountField.getText());
                if (amount <= 0) {
                    Alert alert = new Alert(Alert.AlertType.WARNING, "Amount must be positive");
                    alert.showAndWait();
                    return;
                }
                Map<User, Integer> payerMap = buildPayerMap(detailPayerBox);
                if (payerMap.isEmpty()) {
                    Alert alert = new Alert(Alert.AlertType.WARNING, "Please select at least one payer");
                    alert.showAndWait();
                    return;
                }
                if (!validatePayerMap(payerMap, amount)) return;

                Map<User, Integer> shareMap = buildParticipantShareMap(participantsBox);
                if (shareMap.isEmpty()) {
                    Alert alert = new Alert(Alert.AlertType.WARNING, "Must have at least one participant");
                    alert.showAndWait();
                    return;
                }
                if (!validateParticipantShares(shareMap, amount)) return;

                expense.setAmount(amount);
                expense.setPaidByAmounts(payerMap);
                expense.setParticipantShares(shareMap);
                expense.setCategory(detailCategoryCombo.getValue());
                expense.setDescription(detailDescField.getText().trim());
                expense.setExpenseDate(detailDatePicker.getValue());
                DataStore.saveData();
                onRefresh.run();
                detailStage.close();
            } catch (NumberFormatException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Invalid amount");
                alert.showAndWait();
            }
        });

        deleteBtn.setOnAction(e -> {
            currentGroup.getExpenses().remove(expense);
            DataStore.saveData();
            onRefresh.run();
            detailStage.close();
        });

        closeBtn.setOnAction(e -> detailStage.close());

        root.getChildren().addAll(titleLabel, infoGrid, detailPayerBox, new Label("Charge Breakdown:"), breakdownLabel, breakdownArea, participantsBox, buttonRow);
        ScrollPane detailScroll = new ScrollPane(root);
        detailScroll.setFitToWidth(true);
        detailScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        Scene detailScene = new Scene(detailScroll);
        detailScene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        detailStage.setScene(detailScene);
        detailStage.show();
    }
}
