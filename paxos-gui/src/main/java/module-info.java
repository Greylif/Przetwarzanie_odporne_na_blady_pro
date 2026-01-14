module com.example.paxosgui {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.junit.jupiter.api;


    opens com.example.paxosgui to javafx.fxml;
    exports com.example.paxosgui;
}