# Expense splitter app

## Structure

```

.
├── Main.java
├── README.md
└── src
    └── main
        ├── model
        │   ├── Expense.java
        │   ├── Group.java
        │   └── User.java
        └── service
            ├── DataStore.java
            └── SplitService.java

```

### Components

#### User.java

```java

package main.model;

private String name;
private double totalPaid;

```

```java

// TODO: Constructor
public String getUserID();
public String getName();
public String getEmail();
public Map<User, Double> getBalanceMap();

public String toString() {
    return this.name;
}

```

#### Expense.java

```java

package main.model;

private double amount;
private User paidBy;
private List<User> participants;
private String category;
```

```java

// TODO: Constructor
public double getAmount();
public User getPaidBy();
public List<User> getParticipants();
public String getCategory();

```

#### Group.java

```java

package main.model;

imports java.io.Serializable;
// implements Serializable

private String name;
private String date;
private boolean settled = false;
private List<User> users = new ArrayList<>();
private List<Expense> expenses = new ArrayList<>();

// TODO: Constructor

public void addMember();
public void addExpense();
public String getName();
public String getDate();
public List<User> getUsers();
public List<Expense> getExpenses();
public boolean isSettled();
public void setSettled();

```
