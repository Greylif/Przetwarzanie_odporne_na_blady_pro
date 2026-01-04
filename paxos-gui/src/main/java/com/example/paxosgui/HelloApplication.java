package com.example.paxosgui;

import javafx.application.Application;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.net.URL;
import java.util.Scanner;

/**
 * Aplikacja do wizualizacji i sterowania Paxos.
 *
 * Każdy węzeł Paxos prezentowany jest jako osobny panel w jednym oknie.
 * Aplikacja komunikuje się z backendem poprzez żądania HTTP.
 */
public class HelloApplication extends Application {

    /**
     * Uruchamia aplikację JavaFX i tworzy jedno okno zawierające
     * panele dla wszystkich węzłów Paxos.
     *
     * @param primaryStage główne okno aplikacji
     */
    @Override
    public void start(Stage primaryStage) {

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        grid.setStyle("-fx-padding: 15;");

        int index = 0;
        for (int port = 8000; port <= 8007; port++) {
            VBox nodePane = createNodePane(port);

            int col = index % 4;
            int row = index / 4;
            grid.add(nodePane, col, row);

            index++;
        }

        Scene scene = new Scene(grid, 1800, 900);
        primaryStage.setTitle("Paxos Cluster");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * Tworzy panel graficzny reprezentujący pojedynczy węzeł Paxos.
     *
     * @param port HTTP węzła Paxos
     * @return VBox zawierający kontrolki i stan węzła
     */

    private VBox createNodePane(int port) {

        Label title = new Label("Paxos Node " + port);
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");

        Label stateLabel = new Label("STATE: ?");
        stateLabel.setStyle("-fx-font-weight: bold;");

        Label statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: blue;");

        TextField valueInput = new TextField();
        valueInput.setPromptText("client propose value (>=0)");
        onlyPositiveNumbers(valueInput);

        Button proposeBtn = new Button("PROPOSE");
        proposeBtn.setOnAction(e -> {
            if (valueInput.getText().isBlank()) {
                statusLabel.setText("Value required (>=0)");
                return;
            }
            statusLabel.setText(
                    post("http://localhost:" + port + "/client_propose?value=" + valueInput.getText())
            );
        });

        Button crashBtn = new Button("CRASH");
        crashBtn.setOnAction(e ->
                statusLabel.setText(post("http://localhost:" + port + "/crash"))
        );

        Button clearBtn = new Button("CLEAR");
        clearBtn.setOnAction(e ->
                statusLabel.setText(post("http://localhost:" + port + "/clear"))
        );

        Button clearAllBtn = new Button("CLEAR ALL");
        clearAllBtn.setOnAction(e ->
                statusLabel.setText(post("http://localhost:" + port + "/clearall"))
        );

        TextField stuckMsgInput = new TextField();
        stuckMsgInput.setPromptText("stuck message");

        Button stuckBtn = new Button("STUCK");
        stuckBtn.setOnAction(e -> {
            String msg = stuckMsgInput.getText().isBlank() ? "STUCK" : stuckMsgInput.getText();
            statusLabel.setText(post("http://localhost:" + port + "/stuck?msg=" + msg));
        });

        Button unstuckBtn = new Button("UNSTUCK");
        unstuckBtn.setOnAction(e ->
                statusLabel.setText(post("http://localhost:" + port + "/unstuck"))
        );

        TextField promisedInput = new TextField();
        promisedInput.setPromptText("promised (>=0)");
        onlyPositiveNumbers(promisedInput);

        TextField acceptedProposalInput = new TextField();
        acceptedProposalInput.setPromptText("acceptedProposal (>=0)");
        onlyPositiveNumbers(acceptedProposalInput);

        TextField acceptedValueInput = new TextField();
        acceptedValueInput.setPromptText("acceptedValue (>=0)");
        onlyPositiveNumbers(acceptedValueInput);

        Button injectBtn = new Button("INJECT");
        injectBtn.setOnAction(e -> {
            StringBuilder url = new StringBuilder("http://localhost:" + port + "/inject");

            boolean first = true;
            first = appendParam(url, "promised", promisedInput.getText(), first);
            first = appendParam(url, "acceptedProposal", acceptedProposalInput.getText(), first);
            appendParam(url, "acceptedValue", acceptedValueInput.getText(), first);

            statusLabel.setText(post(url.toString()));
        });

        VBox root = new VBox(8,
                title,
                stateLabel,
                valueInput,
                proposeBtn,
                new HBox(5, crashBtn, clearBtn, clearAllBtn),
                stuckMsgInput,
                new HBox(5, stuckBtn, unstuckBtn),
                promisedInput,
                acceptedProposalInput,
                acceptedValueInput,
                injectBtn,
                statusLabel
        );

        root.setStyle("""
                -fx-padding: 10;
                -fx-border-color: black;
                -fx-border-radius: 5;
                -fx-background-color: #f5f5f5;
        """);

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(1), e -> {
                    String state = post("http://localhost:" + port + "/accepted_state");
                    stateLabel.setText(state);

                    if (!state.startsWith("STATE")) {
                        stateLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    } else if (isLeader(port)) {
                        stateLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                    } else {
                        stateLabel.setStyle("-fx-text-fill: black; -fx-font-weight: bold;");
                    }
                })
        );
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();

        return root;
    }

    /**
     * Ogranicza pole tekstowe do wprowadzania wyłącznie
     * nieujemnych liczb całkowitych.
     *
     * @param field pole tekstowe do ograniczenia
     */
    private void onlyPositiveNumbers(TextField field) {
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*")) {
                field.setText(newVal.replaceAll("[^\\d]", ""));
            }
        });
    }

    /**
     * Dodaje parametr zapytania HTTP do budowanego adresu URL,
     * jeśli wartość parametru jest niepusta i poprawna (nieujemna liczba).
     *
     * Metoda automatycznie decyduje, czy użyć znaku '?' (pierwszy parametr)
     * czy '&' (kolejne parametry).
     *
     * @param url obiekt StringBuilder reprezentujący adres URL
     * @param name nazwa parametru
     * @param value wartość parametru (musi być liczbą >= 0)
     * @param first informacja, czy jest to pierwszy parametr w URL
     * @return false jeśli parametr został dodany, true jeśli nadal brak parametrów
     */
    private boolean appendParam(StringBuilder url, String name, String value, boolean first) {
        if (value != null && !value.isBlank()) {
            if (!value.matches("\\d+")) return first;
            url.append(first ? "?" : "&").append(name).append("=").append(value);
            return false;
        }
        return first;
    }
    /**
     * Sprawdza, czy dany węzeł jest aktualnym liderem.
     *
     * @param port port węzła
     * @return true jeśli węzeł jest liderem
     */
    private boolean isLeader(int port) {
        String resp = post("http://localhost:" + port + "/leader");
        try {
            return Integer.parseInt(resp.trim()) == port;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Wysyła żądanie HTTP POST pod wskazany adres.
     *
     * @param urlStr adres URL
     * @return treść odpowiedzi lub "OFFLINE" w przypadku błędu
     */
    private String post(String urlStr) {
        try {
            URL url = new URL(urlStr);
            var conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(500);
            conn.setReadTimeout(500);
            conn.setDoOutput(true);

            try (var in = new Scanner(conn.getInputStream())) {
                return in.useDelimiter("\\A").next();
            }
        } catch (Exception e) {
            return "OFFLINE";
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
