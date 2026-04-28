package org.service;
import org.model.*;

import java.lang.reflect.Member;
import java.util.*;
import java.util.stream.Collectors;

public class BillSplitterService {
    public static double getTotalGroupSpent(Group group) {
        return group.getExpenses().stream().mapToDouble(Expense::getAmount).sum();
    }

    public static Map<String, Double> getCategoryTotal(Group group) {
        return group.getExpenses().stream().collect(
                Collectors.groupingBy(
                        Expense::getCategory,
                        Collectors.summingDouble(Expense::getAmount)
                )
        );
    }

    public static Map<User, Double> calculateBalances(Group group) {
        Map<User, Double> balance = new HashMap<>();

        for (User user : group.getMembers()) {
            balance.put(user, 0.0);
        }

        for (Expense e : group.getExpenses()) {
            double share = e.getAmount() / e.getParticipants().size();

            for (User user : e.getParticipants()) {
                balance.put(user, balance.get(user) - share);
            }

            User payer = e.getPaidBy();
            balance.put(payer, balance.get(payer) + e.getAmount());
        }

        return balance;
    }

    public static List<String> simplifyDebts(Map<User, Double> balance) {
        List<String> result = new ArrayList<>();

        List<Map.Entry<User, Double>> debtors = new ArrayList<>();
        List<Map.Entry<User, Double>> creditors = new ArrayList<>();

        for (Map.Entry<User, Double> entry : balance.entrySet()) {
            if (entry.getValue() < -1)
                debtors.add(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
            else if (entry.getValue() >= 1)
                creditors.add(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
        }

        int debtorIdx = 0;
        int creditorIdx = 0;

        while (debtorIdx < debtors.size() && creditorIdx < creditors.size()) {
            Map.Entry<User, Double> d = debtors.get(debtorIdx);
            Map.Entry<User, Double> c = creditors.get(creditorIdx);

            double amount = Math.min(-d.getValue(), c.getValue());

            if (amount > 0) {
                result.add(String.format("%s pays %s %f NTD",
                        d.getKey().getName(), c.getKey().getName(), amount));

                d.setValue(d.getValue() + amount);
                c.setValue(c.getValue() - amount);
            }

            if (Math.abs(d.getValue()) < 1) debtorIdx++;
            if (Math.abs(c.getValue()) < 1) creditorIdx++;
        }

        return result;
    }
}
