package com.example.paxosgui;

import javafx.application.Application;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.net.URL;
import java.util.Scanner;

public class HelloApplication extends Application {

    @Override
    public void start(Stage primaryStage) {
        for (int port = 8000; port <= 8007; port++) {
            createNodeWindow(port);
        }
    }

    private void createNodeWindow(int port) {
        Stage stage = new Stage();

        Label title = new Label("Paxos Node " + port);

        Label stateLabel = new Label("STATE: ?");
        stateLabel.setStyle("-fx-font-weight: bold;");

        Label statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: blue;");

        TextField valueInput = new TextField();
        valueInput.setPromptText("client propose value");

        Button proposeBtn = new Button("PROPOSE");
        proposeBtn.setOnAction(e -> {
            String value = valueInput.getText();
            if (value != null && !value.isBlank()) {
                statusLabel.setText(
                        post("http://localhost:" + port + "/client_propose?value=" + value)
                );
            }
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
            String msg = stuckMsgInput.getText();
            if (msg == null || msg.isBlank()) {
                msg = "STUCK";
            }
            statusLabel.setText(
                    post("http://localhost:" + port + "/stuck?msg=" + msg)
            );
        });

        Button unstuckBtn = new Button("UNSTUCK");
        unstuckBtn.setOnAction(e ->
                statusLabel.setText(post("http://localhost:" + port + "/unstuck"))
        );

        TextField promisedInput = new TextField();
        promisedInput.setPromptText("promised");

        TextField acceptedProposalInput = new TextField();
        acceptedProposalInput.setPromptText("acceptedProposal");

        TextField acceptedValueInput = new TextField();
        acceptedValueInput.setPromptText("acceptedValue");

        Button injectBtn = new Button("INJECT");
        injectBtn.setOnAction(e -> {
            StringBuilder url = new StringBuilder("http://localhost:" + port + "/inject");

            boolean first = true;
            first = appendParam(url, "promised", promisedInput.getText(), first);
            first = appendParam(url, "acceptedProposal", acceptedProposalInput.getText(), first);
            appendParam(url, "acceptedValue", acceptedValueInput.getText(), first);

            statusLabel.setText(post(url.toString()));
        });


        VBox root = new VBox(10,
                title,
                stateLabel,

                valueInput,
                proposeBtn,

                new HBox(10, crashBtn, clearBtn, clearAllBtn),

                stuckMsgInput,
                new HBox(10, stuckBtn, unstuckBtn),

                promisedInput,
                acceptedProposalInput,
                acceptedValueInput,
                injectBtn,

                statusLabel
        );
        root.setStyle("-fx-padding: 20;");

        stage.setTitle("Paxos Node " + port);
        stage.setScene(new Scene(root, 420, 520));
        stage.show();

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
    }


    private boolean appendParam(StringBuilder url, String name, String value, boolean first) {
        if (value != null && !value.isBlank()) {
            if (!value.matches("\\d+")) return first;
            url.append(first ? "?" : "&").append(name).append("=").append(value);
            return false;
        }
        return first;
    }

    private boolean isLeader(int port) {
        String resp = post("http://localhost:" + port + "/leader");
        try {
            return Integer.parseInt(resp.trim()) == port;
        } catch (Exception e) {
            return false;
        }
    }

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
