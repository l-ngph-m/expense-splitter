package org.model;

import java.io.Serializable;

public class User implements Serializable {
    private String name;
    private double totalPaid;


    public User(String name, double totalPaid) {
        this.name = name;
        this.totalPaid = 0.0;
    }

    public String getName() {
        return name;
    }

    public double getTotalPaid() {
        return totalPaid;
    }

    public void addPayment(double amount) {
        totalPaid += amount;
    }
    public void setName(String name) { this.name = name; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return name.equals(user.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
