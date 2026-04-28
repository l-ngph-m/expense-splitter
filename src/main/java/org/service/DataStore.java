package org.service;

import org.model.*;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class DataStore {
    public static List<Group> groups = new ArrayList<>();
    public static List<Group> history = new ArrayList<>();

    public static void saveData() {
        try {
            ObjectOutputStream output = new ObjectOutputStream(
                    new FileOutputStream("data.dat")
            );
            output.writeObject(groups);
            output.writeObject(history);
            output.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static void loadData() {
        try {
            ObjectInputStream input = new ObjectInputStream(
                    new FileInputStream("data.dat")
            );
            groups = (List<Group>) input.readObject();
            history = (List<Group>) input.readObject();
            input.close();

        } catch (Exception e) {
            // Ignore
        }
    }
}
