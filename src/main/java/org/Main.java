package org;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
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
    private ListView<String> expenseListView = new ListView<>();
    private TextArea balanceTextArea = new TextArea();

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
        leftNav.setStyle("-fx-background-color: #f0f0f0;");
        leftNav.setPrefWidth(100);

        mainContent = new VBox(10);
        mainContent.setPadding(new Insets(10));

        ScrollPane scrollPane = new ScrollPane(mainContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        root.setLeft(leftNav);
        root.setCenter(scrollPane);
        root.setStyle("-fx-font-size: 14px;");

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        stage.setScene(scene);
        stage.show();

        showGroupsPanel();
    }

    private Button createNavButton(String text) {
        Button btn = new Button(text);
        btn.setPrefWidth(80);
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

                List<User> participants = new ArrayList<>();
            for (javafx.scene.Node node : participantsBox.getChildren()) {
                if (node instanceof CheckBox cb && cb.isSelected()) {
                    currentGroup.getMembers().stream()
                            .filter(u -> u.getName().equals(cb.getId()))
                            .findFirst().ifPresent(participants::add);
                }
            }

            if (!participants.isEmpty()) {
                Expense exp = new Expense(amount, payerMap, participants, category, description, expenseDate);
                currentGroup.addExpense(exp);
                DataStore.saveData();
                refreshExpenseList();
                amountField.clear();
                descriptionField.clear();
                resetPayerBox(payerBox);
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

    private void autoDistribute(VBox payerBox, TextField amountField) {
        try {
            int total = Integer.parseInt(amountField.getText());
            if (total <= 0) return;
            List<HBox> checkedRows = new ArrayList<>();
            for (javafx.scene.Node node : payerBox.getChildren()) {
                if (node instanceof HBox row) {
                    for (javafx.scene.Node child : row.getChildren()) {
                        if (child instanceof CheckBox cb && cb.isSelected()) {
                            checkedRows.add(row);
                            break;
                        }
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

    private void refreshExpenseList() {
        expenseListView.getItems().clear();
        if (currentGroup != null) {
            List<Expense> sortedExpenses = currentGroup.getExpenses().stream()
                    .sorted((a, b) -> b.getExpenseDate().compareTo(a.getExpenseDate()))
                    .toList();
            for (Expense e : sortedExpenses) {
                String desc = e.getDescription().isEmpty() ? "—" : e.getDescription();
                expenseListView.getItems().add(String.format("[%s]  %6d NTD  %-10s  %-12s  %s",
                        e.getFormattedDate(), e.getAmount(), e.getPaidByNames(), e.getCategory(), desc));
            }
        }
    }

    private void deleteExpense() {
        int idx = expenseListView.getSelectionModel().getSelectedIndex();
        if (idx >= 0) {
            List<Expense> sortedExpenses = currentGroup.getExpenses().stream()
                    .sorted((a, b) -> b.getExpenseDate().compareTo(a.getExpenseDate()))
                    .toList();
            Expense selected = sortedExpenses.get(idx);
            currentGroup.getExpenses().remove(selected);
            DataStore.saveData();
            refreshExpenseList();
        }
    }

    private void showGroupsPanel() {
        mainContent.getChildren().clear();

        Label title = new Label("Groups");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        groupListView.getItems().clear();
        for (Group g : DataStore.groups) {
            groupListView.getItems().add(g.getName() + " (" + g.getMembers().size() + " members)");
        }
        groupListView.setPrefHeight(200);
        groupListView.setStyle("-fx-font-size: 16px;");

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
                showMembersPanel();
            }
        });

        TextField newGroupField = new TextField();
        newGroupField.setPromptText("New group name");

        Button addGroupBtn = new Button("Add Group");
        Button deleteGroupBtn = new Button("Delete Group");
        deleteGroupBtn.getStyleClass().add("delete-button");

        HBox inputRow = new HBox(10, newGroupField, addGroupBtn);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        addGroupBtn.setOnAction(e -> addGroup(newGroupField));
        newGroupField.setOnAction(e -> addGroup(newGroupField));

        deleteGroupBtn.setOnAction(e -> deleteGroup());
        deleteGroupBtn.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) deleteGroup();
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

        mainContent.getChildren().addAll(title, groupListView, inputRow, deleteGroupBtn, selectBtn);
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
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        memberListView.getItems().clear();
        for (User u : currentGroup.getMembers()) {
            memberListView.getItems().add(u.getName());
        }
        memberListView.setPrefHeight(200);
        memberListView.setStyle("-fx-font-size: 16px;");

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
        Button deleteMemberBtn = new Button("Remove Member");
        deleteMemberBtn.getStyleClass().add("delete-button");
        Button backBtn = new Button("Back to Groups");

        HBox inputRow = new HBox(10, newMemberField, addMemberBtn);

        addMemberBtn.setOnAction(e -> addMember(newMemberField));
        newMemberField.setOnAction(e -> addMember(newMemberField));

        deleteMemberBtn.setOnAction(e -> deleteMember());
        deleteMemberBtn.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) deleteMember();
        });

        backBtn.setOnAction(e -> showGroupsPanel());
        backBtn.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) showGroupsPanel();
        });

        mainContent.getChildren().addAll(title, memberListView, inputRow, deleteMemberBtn, backBtn);
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
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        expenseListView.setPrefHeight(250);
        expenseListView.setStyle("-fx-font-size: 16px;");
        refreshExpenseList();

        expenseListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                int idx = expenseListView.getSelectionModel().getSelectedIndex();
                if (idx >= 0) {
                    List<Expense> sorted = currentGroup.getExpenses().stream()
                            .sorted((a, b) -> b.getExpenseDate().compareTo(a.getExpenseDate()))
                            .toList();
                    showExpenseDetail(sorted.get(idx));
                }
            }
        });

        TextField amountField = new TextField();
        amountField.setPromptText("Amount");

        VBox payerBox = new VBox(5);
        payerBox.setPadding(new Insets(5));
        payerBox.setStyle("-fx-border-color: #ccc; -fx-border-radius: 3;");
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
        participantsBox.setStyle("-fx-border-color: #ccc; -fx-border-radius: 3;");
        Label partLabel = new Label("Participants:");
        participantsBox.getChildren().add(partLabel);

        for (User u : currentGroup.getMembers()) {
            CheckBox cb = new CheckBox(u.getName());
            cb.setSelected(true);
            cb.setId(u.getName());
            participantsBox.getChildren().add(cb);
        }

        Button addExpenseBtn = new Button("Add Expense");
        Button autoSplitBtn = new Button("Auto Split");
        Button deleteExpenseBtn = new Button("Delete Expense");
        deleteExpenseBtn.getStyleClass().add("delete-button");
        Button backBtn = new Button("Back to Groups");

        addExpenseBtn.setOnAction(e -> addExpense(amountField, payerBox, categoryCombo, descriptionField, datePicker, participantsBox));
        amountField.setOnAction(e -> addExpense(amountField, payerBox, categoryCombo, descriptionField, datePicker, participantsBox));
        descriptionField.setOnAction(e -> addExpense(amountField, payerBox, categoryCombo, descriptionField, datePicker, participantsBox));

        autoSplitBtn.setOnAction(e -> autoDistribute(payerBox, amountField));

        for (javafx.scene.Node node : payerBox.getChildren()) {
            if (node instanceof HBox row) {
                for (javafx.scene.Node child : row.getChildren()) {
                    if (child instanceof CheckBox cb) {
                        cb.selectedProperty().addListener((obs, old, val) -> autoDistribute(payerBox, amountField));
                    }
                }
            }
        }

        deleteExpenseBtn.setOnAction(e -> deleteExpense());
        deleteExpenseBtn.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) deleteExpense();
        });

        backBtn.setOnAction(e -> showGroupsPanel());
        backBtn.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) showGroupsPanel();
        });

        VBox form = new VBox(10, amountField, payerBox, autoSplitBtn, categoryCombo, descriptionField, datePicker, participantsBox, addExpenseBtn);

        mainContent.getChildren().addAll(title, expenseListView, form, deleteExpenseBtn, backBtn);
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
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        balanceTextArea.setEditable(false);
        balanceTextArea.setPrefHeight(200);

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

        mainContent.getChildren().addAll(title, balanceTextArea, calcBtn, simplifyBtn, settleBtn, backBtn);
        calculateBalances(false);
    }

    private void calculateBalances(boolean simplify) {
        Map<User, Integer> balances = BillSplitterService.calculateBalances(currentGroup);
        if (simplify) {
            List<String> debts = BillSplitterService.simplifyDebts(balances);
            StringBuilder sb = new StringBuilder("Settlement Plan:\n\n");
            if (debts.isEmpty()) {
                sb.append("All settled up!");
            } else {
                for (String debt : debts) {
                    sb.append(debt).append("\n");
                }
            }
            balanceTextArea.setText(sb.toString());
        } else {
            StringBuilder sb = new StringBuilder("Individual Balances:\n\n");
            for (Map.Entry<User, Integer> entry : balances.entrySet()) {
                sb.append(String.format("%s: %d NTD %s\n",
                        entry.getKey().getName(),
                        entry.getValue(),
                        entry.getValue() >= 0 ? "(is owed)" : "(owes)"));
            }
            int total = BillSplitterService.getTotalGroupSpent(currentGroup);
            sb.append(String.format("\nTotal Group Spending: %d NTD", total));
            balanceTextArea.setText(sb.toString());
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
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

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
            Expense settlement = new Expense(amount, payerMap, new ArrayList<>(List.of(receiver)), "Settlement", "Settlement", LocalDate.now());
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
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

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
        detailPayerBox.setStyle("-fx-border-color: #ccc; -fx-border-radius: 3;");
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
        participantsBox.setStyle("-fx-border-color: #ccc; -fx-border-radius: 3;");
        Label partLabel = new Label("Participants:");
        participantsBox.getChildren().add(partLabel);

        for (User u : currentGroup.getMembers()) {
            CheckBox cb = new CheckBox(u.getName());
            cb.setId(u.getName());
            if (expense.getParticipants().contains(u)) cb.setSelected(true);
            participantsBox.getChildren().add(cb);
        }

        int share = expense.getAmount() / expense.getParticipants().size();
        Label breakdownLabel = new Label("Each person charged: " + share + " NTD");
        breakdownLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        StringBuilder sb = new StringBuilder();
        for (User u : expense.getParticipants()) {
            sb.append(String.format("  %s: %d NTD\n", u.getName(), share));
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

                List<User> participants = new ArrayList<>();
                for (javafx.scene.Node node : participantsBox.getChildren()) {
                    if (node instanceof CheckBox cb && cb.isSelected()) {
                        currentGroup.getMembers().stream()
                                .filter(u -> u.getName().equals(cb.getId()))
                                .findFirst().ifPresent(participants::add);
                    }
                }

                if (!participants.isEmpty()) {
                    expense.getParticipants().clear();
                    expense.getParticipants().addAll(participants);
                    expense.setAmount(amount);
                    expense.setPaidByAmounts(payerMap);
                    expense.setCategory(detailCategoryCombo.getValue());
                    expense.setDescription(detailDescField.getText().trim());
                    expense.setExpenseDate(detailDatePicker.getValue());
                    DataStore.saveData();
                    refreshExpenseList();
                    detailStage.close();
                } else {
                    Alert alert = new Alert(Alert.AlertType.WARNING, "Must have at least one participant");
                    alert.showAndWait();
                }
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
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        ListView<String> memberExpenseListView = new ListView<>();
        memberExpenseListView.setPrefHeight(200);
        memberExpenseListView.setStyle("-fx-font-size: 16px;");

        Label balanceLabel = new Label();
        balanceLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Runnable refreshList = () -> {
            memberExpenseListView.getItems().clear();
            List<Expense> sorted = currentGroup.getExpenses().stream()
                    .filter(e -> e.getParticipants().contains(member) || e.getPaidByAmounts().containsKey(member))
                    .sorted((a, b) -> b.getExpenseDate().compareTo(a.getExpenseDate()))
                    .toList();
            for (Expense e : sorted) {
                String desc = e.getDescription().isEmpty() ? "—" : e.getDescription();
                memberExpenseListView.getItems().add(String.format("[%s]  %6d NTD  %-10s  %-12s  %s",
                        e.getFormattedDate(), e.getAmount(), e.getPaidByNames(), e.getCategory(), desc));
            }
        };
        refreshList.run();

        memberExpenseListView.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getClickCount() == 2) {
                int idx = memberExpenseListView.getSelectionModel().getSelectedIndex();
                if (idx >= 0) {
                    List<Expense> sorted = currentGroup.getExpenses().stream()
                            .filter(exp -> exp.getParticipants().contains(member) || exp.getPaidByAmounts().containsKey(member))
                            .sorted((a, b) -> b.getExpenseDate().compareTo(a.getExpenseDate()))
                            .toList();
                    Expense expense = sorted.get(idx);
                    Runnable originalRefresh = () -> {
                        refreshList.run();
                        updateBalanceLabel(member, balanceLabel);
                    };
                    showExpenseDetailForExpense(expense, originalRefresh);
                }
            }
        });

        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> detailStage.close());

        HBox buttonRow = new HBox(10, closeBtn);

        updateBalanceLabel(member, balanceLabel);

        root.getChildren().addAll(titleLabel, memberExpenseListView, balanceLabel, buttonRow);
        Scene memberScene = new Scene(root);
        memberScene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        detailStage.setScene(memberScene);
        detailStage.show();
    }

    private void updateBalanceLabel(User member, Label balanceLabel) {
        int balance = BillSplitterService.getUserBalance(currentGroup, member);
        if (balance >= 0) {
            balanceLabel.setText(String.format("Total Balance: %d NTD (is owed)", balance));
            balanceLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: green;");
        } else {
            balanceLabel.setText(String.format("Total Balance: %d NTD (owes)", balance));
            balanceLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: red;");
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
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

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
        detailPayerBox.setStyle("-fx-border-color: #ccc; -fx-border-radius: 3;");
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
        participantsBox.setStyle("-fx-border-color: #ccc; -fx-border-radius: 3;");
        Label partLabel = new Label("Participants:");
        participantsBox.getChildren().add(partLabel);

        for (User u : currentGroup.getMembers()) {
            CheckBox cb = new CheckBox(u.getName());
            cb.setId(u.getName());
            if (expense.getParticipants().contains(u)) cb.setSelected(true);
            participantsBox.getChildren().add(cb);
        }

        int share = expense.getAmount() / expense.getParticipants().size();
        Label breakdownLabel = new Label("Each person charged: " + share + " NTD");
        breakdownLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        StringBuilder sb = new StringBuilder();
        for (User u : expense.getParticipants()) {
            sb.append(String.format("  %s: %d NTD\n", u.getName(), share));
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

                List<User> participants = new ArrayList<>();
                for (javafx.scene.Node node : participantsBox.getChildren()) {
                    if (node instanceof CheckBox cb && cb.isSelected()) {
                        currentGroup.getMembers().stream()
                                .filter(u -> u.getName().equals(cb.getId()))
                                .findFirst().ifPresent(participants::add);
                    }
                }

                if (!participants.isEmpty()) {
                    expense.getParticipants().clear();
                    expense.getParticipants().addAll(participants);
                    expense.setAmount(amount);
                    expense.setPaidByAmounts(payerMap);
                    expense.setCategory(detailCategoryCombo.getValue());
                    expense.setDescription(detailDescField.getText().trim());
                    expense.setExpenseDate(detailDatePicker.getValue());
                    DataStore.saveData();
                    onRefresh.run();
                    detailStage.close();
                } else {
                    Alert alert = new Alert(Alert.AlertType.WARNING, "Must have at least one participant");
                    alert.showAndWait();
                }
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
