package org.model;
import java.io.Serializable;

public class User implements Serializable {
    /* @Parameters
        - String name
        - int totalPaid
    */
private String name;
private int totalPaid;

    // TODO: Constructor
public User(String name){
    this.name = name;
    totalPaid = 0;
}

// User obj = new User("Alice");
    // TODO: Getter functions
public String getName(){
    return name;
}
public int getTotalPaid(){
    return totalPaid;
}
    // TODO: addPayment()
public void addPayment(int amount){
    totalPaid += amount;
}
}

