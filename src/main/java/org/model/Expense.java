package org.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Expense implements Serializable {
    private int amount;
    private Map<User, Integer> paidByAmounts;
    private List<User> participants;
    private String category;
    private String description;
    private LocalDate expenseDate;
    private LocalDateTime dateTime;

    public Expense(int amount, Map<User, Integer> paidByAmounts, List<User> participants, String category, String description, LocalDate expenseDate) {
        this.amount = amount;
        this.paidByAmounts = paidByAmounts != null ? new HashMap<>(paidByAmounts) : new HashMap<>();
        this.participants = participants;
        this.category = category;
        this.description = description != null ? description : "";
        this.expenseDate = expenseDate != null ? expenseDate : LocalDate.now();
        this.dateTime = LocalDateTime.now();
    }

    public int getAmount() {
        return amount;
    }
    public Map<User, Integer> getPaidByAmounts() {
        return paidByAmounts;
    }
    public String getPaidByNames() {
        return paidByAmounts.keySet().stream().map(User::getName).collect(Collectors.joining("+"));
    }
    public List<User> getParticipants() {
        return participants;
    }
    public String getCategory() {
        return category;
    }
    public String getDescription() {
        return description;
    }
    public LocalDate getExpenseDate() {
        return expenseDate;
    }
    public void setAmount(int amount) {
        this.amount = amount;
    }
    public void setPaidByAmounts(Map<User, Integer> paidByAmounts) {
        this.paidByAmounts = paidByAmounts != null ? new HashMap<>(paidByAmounts) : new HashMap<>();
    }
    public void setCategory(String category) {
        this.category = category;
    }
    public void setDescription(String description) {
        this.description = description != null ? description : "";
    }
    public void setExpenseDate(LocalDate expenseDate) {
        this.expenseDate = expenseDate != null ? expenseDate : LocalDate.now();
    }
    public LocalDateTime getDateTime() {
        return dateTime;
    }
    public String getFormattedDate() {
        return expenseDate.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
    }
    public String getFormattedDateTime() {
        return dateTime.format(DateTimeFormatter.ofPattern("MM/dd HH:mm"));
    }
}
