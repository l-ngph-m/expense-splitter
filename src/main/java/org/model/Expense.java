package org.model;

import java.io.Serializable;
import java.util.List;

public class Expense implements Serializable {
private int amount;
private User paidBy;
private List <User> participants;
private String category;
    // TODO: Constructor
public Expense(int amount, User paidBy, List<User> participants, String category){
    this.amount = amount;
    this.paidBy = paidBy;
    this.participants = participants;
    this.category = category;
}
}

    // TODO: Getter functions
public int getAmount(){
    return amount;
}
public User getPaidBy(){
    return paidBy;
}
public List <User> getParticipants(){
    return participants;
}
public String getCategory(){
    return category;
}
