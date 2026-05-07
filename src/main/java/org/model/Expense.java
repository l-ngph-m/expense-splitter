package org.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class Expense implements Serializable {
    private int amount;
    private User paidBy;
    private List<User> participants;
    private String category;
    private String description;
    private LocalDateTime dateTime;

    public Expense(int amount, User paidBy, List<User> participants, String category, String description) {
        this.amount = amount;
        this.paidBy = paidBy;
        this.participants = participants;
        this.category = category;
        this.description = description != null ? description : "";
        this.dateTime = LocalDateTime.now();
    }

    public int getAmount() {
        return amount;
    }
    public User getPaidBy() {
        return paidBy;
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
    public LocalDateTime getDateTime() {
        return dateTime;
    }
    public String getFormattedDateTime() {
        return dateTime.format(DateTimeFormatter.ofPattern("MM/dd HH:mm"));
    }
}
