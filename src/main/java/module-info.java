module expense.splitter {
    requires javafx.controls;
    requires javafx.graphics;
    exports org;
    opens org.model to javafx.base;
}
