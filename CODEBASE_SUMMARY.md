# Codebase Summary

Expense Splitter – a JavaFX desktop application for splitting group expenses.

---

## Project Structure

```
src/main/java/org/
├── Main.java                          # JavaFX entry point & UI
├── model/
│   ├── User.java                      # Group member
│   ├── Expense.java                   # Single expense record
│   └── Group.java                     # Expense group
└── service/
    ├── BillSplitterService.java       # Business logic
    └── DataStore.java                 # Serialization persistence
```

---

## Package: `org.model` – Domain Models

### `User`
| Field/Method | Description |
|---|---|
| `name` | Member name |
| `totalPaid` | Running total of payments (unused in current logic) |
| `getName()` | Returns name |
| `getTotalPaid()` | Returns total paid |
| `addPayment(amount)` | Adds to total paid |

### `Expense`
| Field/Method | Description |
|---|---|
| `amount` | Total cost (integer) |
| `paidBy` | Who paid (User reference) |
| `participants` | Who shares the cost |
| `category` | One of: Food, Utilities, Entertainment, Transportation, Other |
| `description` | Optional free-text description |
| `expenseDate` | User-chosen date (LocalDate) |
| `dateTime` | Auto-set creation timestamp (LocalDateTime) |
| `getFormattedDate()` | Returns `yyyy/MM/dd` |
| `getFormattedDateTime()` | Returns `MM/dd HH:mm` |
| Setters | `setAmount`, `setPaidBy`, `setCategory`, `setDescription`, `setExpenseDate` |

### `Group`
| Field/Method | Description |
|---|---|
| `name` | Group name |
| `date` | Creation date string |
| `settled` | Whether group is settled |
| `members` | List of User |
| `expenses` | List of Expense |
| `addMember(u)`, `addExpense(e)` | Append helpers |

---

## Package: `org.service` – Business Logic

### `BillSplitterService`

#### `getTotalGroupSpent(Group)` → `int`
Sums all expense amounts in the group using `mapToInt`.

#### `getCategoryTotal(Group)` → `Map<String, Double>`
Groups expenses by category and sums amounts (unused in UI).

#### `calculateBalances(Group)` → `Map<User, Integer>`
Core balancing algorithm:
1. Initializes every group member to balance `0`
2. For each expense: subtracts the per-person share from each participant, adds the full amount to the payer
3. Skips expenses where the payer or participants are no longer group members (null-safety)
4. Returns each member's net balance (positive = is owed, negative = owes)

#### `simplifyDebts(Map<User, Integer>)` → `List<String>`
Takes the output of `calculateBalances` and produces a minimal settlement plan:
- Partitions members into debtors (balance < -1) and creditors (balance >= 1)
- Greedily matches largest debts to largest credits
- Produces human-readable strings like `"Alice pays Bob 150 NTD"`

#### `getUserBalance(Group, User)` → `int`
Calculates a single member's net balance by scanning all expenses:
- Subtracts their share from each expense they're a participant in
- Adds full amount for each expense they paid

#### `getUserPairwiseDebts(Group, User)` → `List<String>`
Computes pairwise net balance between the given user and every other member:
- For each other member, scans all expenses involving either user
- Calculates the net flow between the two
- Returns strings like `"Bob pays Alice 50 NTD"` or `"Alice pays Bob 30 NTD"`

### `DataStore`

| Method | Description |
|---|---|
| `saveData()` | Serializes `groups` and `history` lists to `data.dat` via `ObjectOutputStream` |
| `loadData()` | Deserializes from `data.dat`, initializes empty lists on failure |
| `groups` | Static `List<Group>` – all active groups |
| `history` | Static `List<Group>` – deleted groups moved here |

---

## Package: `org` – UI (JavaFX)

### `Main` (entry point)

| Method | Description |
|---|---|
| `main(String[])` | Launches JavaFX |
| `start(Stage)` | Sets up 650x650 window, left nav bar (Groups, Members, Expenses, Balances), and `BorderPane` layout |
| `createNavButton(text)` | Creates a nav button (80px wide) |
| **Group Panel** | |
| `showGroupsPanel()` | ListView of groups, add/delete/select buttons, double-click to enter group |
| `addGroup(TextField)` | Creates group, validates duplicate names |
| `deleteGroup()` | Removes from groups, moves to history |
| **Member Panel** | |
| `showMembersPanel()` | ListView of members in current group, add/delete/back buttons, double-click opens member detail |
| `addMember(TextField)` | Creates member, validates duplicates |
| `deleteMember()` | Removes member from current group |
| **Expense Panel** | |
| `showExpensesPanel()` | Sorted expense list, form with: amount, paid-by combo, category combo, description, date picker, participant checkboxes |
| `addExpense(...)` | Parses form, creates Expense, persists, refreshes list |
| `refreshExpenseList()` | Clears and repopulates expense list sorted by date descending |
| `deleteExpense()` | Deletes selected expense using sorted index mapping |
| **Balance Panel** | |
| `showBalancesPanel()` | Shows balances automatically on load |
| `calculateBalances(simplify)` | If simplify=false: shows per-person balances + total. If simplify=true: shows simplified debt plan |
| **Expense Detail Window (Stage)** | |
| `showExpenseDetail(Expense)` | Popup window with editable fields (date, amount, paid-by, category, description), participant checkboxes, charge breakdown, Save/Delete/Close buttons. Refreshes main expense list after save |
| `showExpenseDetailForExpense(Expense, Runnable)` | Same as above but accepts a custom refresh callback (used from Member Detail to refresh both the member's filtered list and balance label) |
| **Member Detail Window (Stage)** | |
| `showMemberDetail(User)` | Popup window with filtered expense list (only where member is involved), total balance label (green/red), Simplify Debts button (shows pairwise settlement), double-click on expense opens edit window |
| `updateBalanceLabel(member, label, area, btn)` | Sets balance text + color, clears debts area |

---

## Key Design Decisions

- **Currency**: All amounts are integers (smallest denomination is 1 NTD)
- **Equal splitting**: Each expense is divided equally among participants (`amount / participants.size()`)
- **Expense sorting**: Sorted by user-chosen expense date (newest first), not creation time
- **Null safety**: `calculateBalances` skips expenses referencing members who were removed from the group
- **Delete mapping**: Since expenses are displayed sorted, delete operations map the displayed index back to the actual Expense object via sorted list lookup
- **Persistence**: Java serialization to a flat `data.dat` file in the project root
- **Refresh pattern**: `showExpenseDetailForExpense` accepts a `Runnable` callback so the caller decides what to refresh after save/delete (used by both the full expense list and the member-filtered list)

---

## Build & Run

```sh
mvn compile          # Compile
mvn javafx:run       # Run
```

No test framework configured. Java 25 + JavaFX 25.
