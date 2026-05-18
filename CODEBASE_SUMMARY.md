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
| `equals(o)` / `hashCode()` | Name-based equality (needed for `Map<User, ...>` lookups after deserialization) |

### `Expense`
| Field/Method | Description |
|---|---|
| `amount` | Total cost (integer) |
| `paidByAmounts` | Map of who paid what (`Map<User, Integer>`) — supports multi-payer |
| `participants` | Who shares the cost |
| `category` | One of: Food, Utilities, Entertainment, Transportation, Settlement, Other |
| `description` | Optional free-text description |
| `expenseDate` | User-chosen date (LocalDate) |
| `dateTime` | Auto-set creation timestamp (LocalDateTime) |
| `getFormattedDate()` | Returns `yyyy/MM/dd` |
| `getFormattedDateTime()` | Returns `MM/dd HH:mm` |
| `getPaidByNames()` | Returns payer names joined with `+` |
| Setters | `setAmount`, `setPaidByAmounts`, `setCategory`, `setDescription`, `setExpenseDate` |

### `Group`
| Field/Method | Description |
|---|---|
| `name` | Group name |
| `date` | Creation date string |
| `settled` | Whether group is settled |
| `members` | List of User |
| `expenses` | List of Expense |
| `addMember(u)`, `addExpense(e)` | Append helpers |
| Constructor | `(name, date, settled, members, expenses)` — all params wired to fields |

---

## Package: `org.service` – Business Logic

### `BillSplitterService`

#### `getTotalGroupSpent(Group)` → `int`
Sums all expense amounts in the group using `mapToInt`. **Excludes** expenses with category `"Settlement"` so internal transfers don't inflate totals.

#### `getCategoryTotal(Group)` → `Map<String, Double>`
Groups expenses by category and sums amounts (unused in UI).

#### `calculateBalances(Group)` → `Map<User, Integer>`
Core balancing algorithm:
1. Initializes every group member to balance `0`
2. For each expense: subtracts the per-person share from each participant
3. For each payer in `paidByAmounts`: adds their individual paid amount (supports multi-payer)
4. Skips entries referencing deleted members via `containsKey` guards
5. Returns each member's net balance (positive = is owed, negative = owes)

#### `simplifyDebts(Map<User, Integer>)` → `List<String>`
Takes the output of `calculateBalances` and produces a minimal settlement plan:
- Partitions members into debtors (balance < -1) and creditors (balance >= 1)
- Greedily matches largest debts to largest credits
- Produces human-readable strings like `"Alice pays Bob 150 NTD"`

#### `getUserBalance(Group, User)` → `int`
Calculates a single member's net balance by scanning all expenses:
- Subtracts their share from each expense they're a participant in
- Adds full amount for each expense they paid

*(removed — pairwise debt calculation was removed to simplify the UI)*

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
| `start(Stage)` | Sets up resizable min-650x650 window, left nav bar (Groups, Members, Expenses, Balances), and `BorderPane` layout |
| `createNavButton(text)` | Creates a nav button (80px wide) |
| **Group Panel** | |
| `showGroupsPanel()` | Displays the group management panel with add, delete, and selection functionality |
| `addGroup(TextField)` | Creates group, validates duplicate names |
| `deleteGroup()` | Deletes the selected group and stores it in group history |
| **Member Panel** | |
| `showMembersPanel()` | ListView of members in current group, add/delete/back buttons, double-click opens member detail |
| `addMember(TextField)` | Creates member, validates duplicates |
| `deleteMember()` | Removes member from current group |
| **Expense Panel** | |
| `showExpensesPanel()` | Sorted expense list (height 250), form with: amount, payer checkboxes+textfields, Auto Split button, category combo, description, date picker, participant checkboxes. Payer amounts auto-distribute on checkbox toggle |
| `addExpense(...)` | Parses form, validates payer amounts via `validatePayerMap`, creates Expense, persists, refreshes list |
| `refreshExpenseList()` | Clears and repopulates expense list sorted by date descending |
| `deleteExpense()` | Deletes selected expense using sorted index mapping |
| `buildPayerMap(VBox)` | Builds `Map<User, Integer>` from payer CheckBox+TextField rows |
| `validatePayerMap(map, total)` | Ensures payer amounts sum to total expense; shows warning if not |
| `resetPayerBox(VBox)` | Unchecks all payer checkboxes and resets amounts to `"0"` |
| `autoDistribute(VBox, TextField)` | Evenly splits total amount among checked payers (with remainder distributed to first N) |
| **Balance Panel** | |
| `showBalancesPanel()` | Displays current balances and settlement controls |
| `calculateBalances(simplify)` | If simplify=false: shows per-person balances + total. If simplify=true: shows simplified debt plan |
| `showSettleDialog()` | Opens popup with payer/receiver combos + amount field |
| `confirmSettlement(...)` | Creates a Settlement-category expense with payer→receiver, persists, refreshes |
| **Expense Detail Window (Stage)** | |
| `showExpenseDetail(Expense)` | Popup window with editable fields (date, amount, paid-by, category, description), participant checkboxes, charge breakdown, Save/Delete/Close buttons. Refreshes main expense list after save |
| `showExpenseDetailForExpense(Expense, Runnable)` | Same as above but accepts a custom refresh callback (used from Member Detail to refresh both the member's filtered list and balance label) |
| **Member Detail Window (Stage)** | |
| `showMemberDetail(User)` | Popup window with filtered expense list (only where member is involved), total balance label (green/red), double-click on expense opens edit window |
| `updateBalanceLabel(member, label)` | Sets balance text + color |

---

## Key Design Decisions

- **Currency**: All amounts are integers (smallest denomination is 1 NTD)
- **Multi-payer**: `Map<User, Integer> paidByAmounts` instead of a single payer — naturally associates each user with their paid amount, HashMap is serializable
- **Equal splitting**: Each expense is divided equally among participants (`amount / participants.size()`)
- **Expense sorting**: Sorted by user-chosen expense date (newest first), not creation time
- **Payer validation**: Sum of payer amounts must equal total expense amount; enforced in all three save handlers
- **Auto-distribute**: Evenly splits total among checked payers on checkbox toggle (not on amount keystroke — preserves manual edits). "Auto Split" button re-triggers
- **Settlement as expense**: Settlement is modeled as an expense where payer pays receiver as sole participant — `calculateBalances` handles it naturally; `getTotalGroupSpent` filters `"Settlement"` so transfers don't inflate group totals
- **Null safety**: `calculateBalances` skips expenses referencing members who were removed from the group via `containsKey` guards
- **Delete mapping**: Since expenses are displayed sorted, delete operations map the displayed index back to the actual Expense object via sorted list lookup
- **User equality**: `User` overrides `equals`/`hashCode` by name, ensuring `Map<User, ...>` lookups work after Java serialization deserialization
- **Persistence**: Java serialization to a flat `data.dat` file in the project root
- **Refresh pattern**: `showExpenseDetailForExpense` accepts a `Runnable` callback so the caller decides what to refresh after save/delete (used by both the full expense list and the member-filtered list)
- **Window**: Resizable (min 650×650) with a fixed-width left nav bar

---

## Build & Run

```sh
mvn compile          # Compile
mvn javafx:run       # Run
```

No test framework configured. Java 25 + JavaFX 25.
