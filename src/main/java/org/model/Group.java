package org.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Group implements Serializable {
    private String name;
    private String date;
    private boolean settled = false;

    private List<User> members = new ArrayList<>();
    private List<Expense> expenses = new ArrayList<>();


    public Group(String name, String date, boolean settled, List<User> members, List<Expense> expenses) {
        this.name = name;
        this.date = date;
    }

    public void addMember(User user) { members.add(user); }
    public void addExpense(Expense e) { expenses.add(e); }

    public String getName() {
        return name;
    }
    public String getDate() {
        return date;
    }
    public List<User> getMembers() {
        return members;
    }
    public List<Expense> getExpenses() {
        return expenses;
    }
    public boolean isSettled() { return settled; }

    public void setSettled(boolean settled) { this.settled = settled; }
}
