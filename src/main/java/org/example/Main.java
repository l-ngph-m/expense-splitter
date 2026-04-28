package org.example;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

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
        stage.setWidth(400);
        stage.setHeight(650);
        stage.setResizable(false);

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

        root.setLeft(leftNav);
        root.setCenter(mainContent);

        stage.setScene(new Scene(root));
        stage.show();

        showGroupsPanel();
    }

    private Button createNavButton(String text) {
        Button btn = new Button(text);
        btn.setPrefWidth(80);
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
            field.clear();
            
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    groupListView.getItems().add(name + " (0 members)");
                    int idx = groupListView.getItems().size() - 1;
                    groupListView.scrollTo(idx);
                    groupListView.getSelectionModel().select(idx);
                }
            }, 100);
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
            field.clear();
            
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    memberListView.getItems().add(name);
                    int idx = memberListView.getItems().size() - 1;
                    memberListView.scrollTo(idx);
                    memberListView.getSelectionModel().select(idx);
                }
            }, 100);
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

    private void addExpense(TextField amountField, ComboBox<String> paidByCombo, TextField categoryField, VBox participantsBox) {
        try {
            double amount = Double.parseDouble(amountField.getText());
            String payerName = paidByCombo.getValue();
            String category = categoryField.getText().trim().isEmpty() ? "Other" : categoryField.getText().trim();

            if (payerName == null) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Please select who paid");
                alert.showAndWait();
                return;
            }

            User payer = currentGroup.getMembers().stream()
                    .filter(u -> u.getName().equals(payerName)).findFirst().orElse(null);

            List<User> participants = new ArrayList<>();
            for (javafx.scene.Node node : participantsBox.getChildren()) {
                if (node instanceof CheckBox cb && cb.isSelected()) {
                    currentGroup.getMembers().stream()
                            .filter(u -> u.getName().equals(cb.getId()))
                            .findFirst().ifPresent(participants::add);
                }
            }

            if (payer != null && !participants.isEmpty()) {
                Expense exp = new Expense(amount, payer, participants, category);
                currentGroup.addExpense(exp);
                DataStore.saveData();
                amountField.clear();
                categoryField.clear();
                
                final String expenseStr = String.format("%.2f - %s (%s)", amount, payerName, category);
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        expenseListView.getItems().add(expenseStr);
                        int idx = expenseListView.getItems().size() - 1;
                        expenseListView.scrollTo(idx);
                        expenseListView.getSelectionModel().select(idx);
                    }
                }, 100);
            }
        } catch (NumberFormatException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Invalid amount");
            alert.showAndWait();
        }
    }

    private void deleteExpense() {
        int idx = expenseListView.getSelectionModel().getSelectedIndex();
        if (idx >= 0) {
            currentGroup.getExpenses().remove(idx);
            DataStore.saveData();
            expenseListView.getItems().remove(idx);
        }
    }

    private void showGroupsPanel() {
        mainContent.getChildren().clear();

        Label title = new Label("Groups");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        groupListView.getItems().clear();
        for (Group g : DataStore.groups) {
            groupListView.getItems().add(g.getName() + " (" + g.getMembers().size() + " members)");
        }
        groupListView.setPrefHeight(200);

        groupListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                String groupName = newVal.split(" \\(")[0];
                currentGroup = DataStore.groups.stream()
                        .filter(g -> g.getName().equals(groupName))
                        .findFirst().orElse(null);
            }
        });

        groupListView.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getClickCount() == 2 && currentGroup != null) {
                showMembersPanel();
            }
        });

        TextField newGroupField = new TextField();
        newGroupField.setPromptText("New group name");

        Button addGroupBtn = new Button("Add Group");
        Button deleteGroupBtn = new Button("Delete Group");

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
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        memberListView.getItems().clear();
        for (User u : currentGroup.getMembers()) {
            memberListView.getItems().add(u.getName());
        }
        memberListView.setPrefHeight(200);

        TextField newMemberField = new TextField();
        newMemberField.setPromptText("Member name");

        Button addMemberBtn = new Button("Add Member");
        Button deleteMemberBtn = new Button("Remove Member");
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
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        expenseListView.getItems().clear();
        for (Expense e : currentGroup.getExpenses()) {
            expenseListView.getItems().add(String.format("%.2f - %s (%s)", 
                    e.getAmount(), e.getPaidBy().getName(), e.getCategory()));
        }
        expenseListView.setPrefHeight(150);

        TextField amountField = new TextField();
        amountField.setPromptText("Amount");

        ComboBox<String> paidByCombo = new ComboBox<>();
        paidByCombo.getItems().addAll(currentGroup.getMembers().stream().map(User::getName).toList());
        paidByCombo.setPromptText("Paid by");

        TextField categoryField = new TextField();
        categoryField.setPromptText("Category (optional)");

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
        Button deleteExpenseBtn = new Button("Delete Expense");
        Button backBtn = new Button("Back to Groups");

        addExpenseBtn.setOnAction(e -> addExpense(amountField, paidByCombo, categoryField, participantsBox));
        amountField.setOnAction(e -> addExpense(amountField, paidByCombo, categoryField, participantsBox));
        categoryField.setOnAction(e -> addExpense(amountField, paidByCombo, categoryField, participantsBox));

        deleteExpenseBtn.setOnAction(e -> deleteExpense());
        deleteExpenseBtn.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) deleteExpense();
        });

        backBtn.setOnAction(e -> showGroupsPanel());
        backBtn.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) showGroupsPanel();
        });

        VBox form = new VBox(10, amountField, paidByCombo, categoryField, participantsBox, addExpenseBtn);

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
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        balanceTextArea.setEditable(false);
        balanceTextArea.setPrefHeight(200);

        Button calcBtn = new Button("Calculate Balances");
        Button simplifyBtn = new Button("Simplify Debts");
        Button backBtn = new Button("Back to Groups");

        calcBtn.setOnAction(e -> {
            Map<User, Double> balances = BillSplitterService.calculateBalances(currentGroup);
            StringBuilder sb = new StringBuilder("Individual Balances:\n\n");
            for (Map.Entry<User, Double> entry : balances.entrySet()) {
                sb.append(String.format("%s: %.2f NTD %s\n", 
                        entry.getKey().getName(),
                        entry.getValue(),
                        entry.getValue() >= 0 ? "(is owed)" : "(owes)"));
            }
            double total = BillSplitterService.getTotalGroupSpent(currentGroup);
            sb.append(String.format("\nTotal Group Spending: %.2f NTD", total));
            balanceTextArea.setText(sb.toString());
        });
        calcBtn.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                calcBtn.fire();
            }
        });

        simplifyBtn.setOnAction(e -> {
            Map<User, Double> balances = BillSplitterService.calculateBalances(currentGroup);
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
        });
        simplifyBtn.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                simplifyBtn.fire();
            }
        });

        backBtn.setOnAction(e -> showGroupsPanel());
        backBtn.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) showGroupsPanel();
        });

        mainContent.getChildren().addAll(title, balanceTextArea, calcBtn, simplifyBtn, backBtn);
    }
}