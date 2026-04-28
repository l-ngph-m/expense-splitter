# AGENTS.md

## Project Overview
Java expense splitter with JavaFX CLI application (400x650 window).

## Build Commands
- `mvn compile` - Compile the project
- `mvn javafx:run` - Run the JavaFX application (Recommended)
- `java -cp target/classes --module-path $HOME/.m2/repository/org/openjfx/javafx-controls/25/javafx-controls-25.jar:$HOME/.m2/repository/org/openjfx/javafx-graphics/25/javafx-graphics-25.jar:$HOME/.m2/repository/org/openjfx/javafx-base/25/javafx-base-25.jar org.example.Main` - Manual run

## Project Structure
- `src/main/java/org/model/` - Domain models: User, Expense, Group
- `src/main/java/org/service/` - Business logic: BillSplitterService, DataStore (serializes to `data.dat`)
- `src/main/java/org/example/Main.java` - JavaFX UI entry point

## Important Notes
- Uses Java 25 for JavaFX compatibility
- JavaFX 25 (javafx-controls, javafx-graphics)
- No tests configured
- DataStore uses Java serialization to `data.dat` file
- Group constructor ignores `settled`, `members`, `expenses` params (bug)