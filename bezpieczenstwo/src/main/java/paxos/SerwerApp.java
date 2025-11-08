package paxos;

import com.sun.net.httpserver.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.Executors;

public class SerwerApp {
  private static int id;
  private static volatile int obiecanyId = -1;
  private static volatile int zaakceptowanyId = -1;
  private static volatile int zaakceptowanaWartosc = -1;
  private static int poczatkowaWartosc;

  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      System.err.println("Usage: java paxos.SerwerApp <id> <port>");
      System.exit(1);
    }
    id = Integer.parseInt(args[0]);
    int port = Integer.parseInt(args[1]);
    poczatkowaWartosc = new Random().nextInt(10) + 1;

    HttpServer http = HttpServer.create(new InetSocketAddress("localhost", port), 0);
    http.createContext("/prepare", exchange -> {
      String body = new String(exchange.getRequestBody().readAllBytes()).trim();
      int proposalId = Integer.parseInt(body);

      int responseValue;
      synchronized (SerwerApp.class) {
        if (proposalId > obiecanyId) {
          obiecanyId = proposalId;
          responseValue = (zaakceptowanaWartosc != -1) ? zaakceptowanaWartosc : Integer.MIN_VALUE;
        } else {
          responseValue = Integer.MAX_VALUE;
        }
      }

      String resp;
      if (responseValue == Integer.MAX_VALUE) {
        resp = "REJECT";
      } else if (responseValue == Integer.MIN_VALUE) {
        resp = "NONE," + poczatkowaWartosc;
      } else {
        resp = "ACCEPTED," + zaakceptowanyId + "," + responseValue;
      }

      byte[] out = resp.getBytes();
      exchange.sendResponseHeaders(200, out.length);
      exchange.getResponseBody().write(out);
      exchange.close();
    });

    http.createContext("/accept", exchange -> {
      String body = new String(exchange.getRequestBody().readAllBytes()).trim();
      String[] parts = body.split(",");
      int proposalId = Integer.parseInt(parts[0]);
      int value = Integer.parseInt(parts[1]);

      boolean accepted = false;
      synchronized (SerwerApp.class) {
        if (proposalId >= obiecanyId) {
          obiecanyId = proposalId;
          zaakceptowanyId = proposalId;
          zaakceptowanaWartosc = value;
          accepted = true;
        } else {
          accepted = false;
        }
      }

      String resp = accepted ? "OK" : "REJECT";
      byte[] out = resp.getBytes();
      exchange.sendResponseHeaders(200, out.length);
      exchange.getResponseBody().write(out);
      exchange.close();
    });

    http.setExecutor(Executors.newCachedThreadPool());
    http.start();

    System.out.println(String.format("Serwer %d uruchomiony na porcie %d (poczatkowa wartosc=%d)", id, port, poczatkowaWartosc));
  }
}
