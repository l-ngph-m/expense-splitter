package org.service;
import org.model.*;

import java.util.*;
import java.util.stream.Collectors;

public class BillSplitterService {
    public static int getTotalGroupSpent(Group group) {
        return group.getExpenses().stream()
                .filter(e -> !"Settlement".equals(e.getCategory()))
                .mapToInt(Expense::getAmount).sum();
    }

    public static Map<String, Double> getCategoryTotal(Group group) {
        return group.getExpenses().stream().collect(
                Collectors.groupingBy(
                        Expense::getCategory,
                        Collectors.summingDouble(Expense::getAmount)
                )
        );
    }

    public static Map<User, Integer> calculateBalances(Group group) {
        Map<User, Integer> balance = new HashMap<>();

        for (User user : group.getMembers()) {
            balance.put(user, 0);
        }

        for (Expense e : group.getExpenses()) {
            int share = e.getParticipants().isEmpty() ? 0 : e.getAmount() / e.getParticipants().size();

            for (User user : e.getParticipants()) {
                if (balance.containsKey(user)) {
                    balance.put(user, balance.get(user) - share);
                }
            }

            for (Map.Entry<User, Integer> payer : e.getPaidByAmounts().entrySet()) {
                if (balance.containsKey(payer.getKey())) {
                    balance.put(payer.getKey(), balance.get(payer.getKey()) + payer.getValue());
                }
            }
        }

        return balance;
    }

    public static List<String> simplifyDebts(Map<User, Integer> balance) {
        List<String> result = new ArrayList<>();

        List<Map.Entry<User, Integer>> debtors = new ArrayList<>();
        List<Map.Entry<User, Integer>> creditors = new ArrayList<>();

        for (Map.Entry<User, Integer> entry : balance.entrySet()) {
            if (entry.getValue() < -1)
                debtors.add(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
            else if (entry.getValue() >= 1)
                creditors.add(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
        }

        int debtorIdx = 0;
        int creditorIdx = 0;

        while (debtorIdx < debtors.size() && creditorIdx < creditors.size()) {
            Map.Entry<User, Integer> d = debtors.get(debtorIdx);
            Map.Entry<User, Integer> c = creditors.get(creditorIdx);

            int amount = Math.min(-d.getValue(), c.getValue());

            if (amount > 0) {
                result.add(String.format("%s pays %s %d NTD",
                        d.getKey().getName(), c.getKey().getName(), amount));

                d.setValue(d.getValue() + amount);
                c.setValue(c.getValue() - amount);
            }

            if (Math.abs(d.getValue()) < 1) debtorIdx++;
            if (Math.abs(c.getValue()) < 1) creditorIdx++;
        }

        return result;
    }

    public static int getUserBalance(Group group, User user) {
        int balance = 0;
        for (Expense e : group.getExpenses()) {
            if (e.getParticipants().isEmpty()) continue;
            int share = e.getAmount() / e.getParticipants().size();

            if (e.getParticipants().contains(user)) {
                balance -= share;
            }

            Integer paid = e.getPaidByAmounts().get(user);
            if (paid != null) {
                balance += paid;
            }
        }
        return balance;
    }

}
