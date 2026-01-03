module com.example.paxosgui {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.example.paxosgui to javafx.fxml;
    exports com.example.paxosgui;
}