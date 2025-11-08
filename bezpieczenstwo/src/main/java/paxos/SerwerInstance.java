package paxos;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.Executors;

public class SerwerInstance {
  private final int id;
  private int obiecanyId = -1;
  private int zaakceptowanyId = -1;
  private int zaakceptowanaWartosc = -1;
  private final int poczatkowaWartosc;
  private HttpServer http;

  public SerwerInstance(int id, int port) throws IOException {
    this.id = id;
    this.poczatkowaWartosc = new Random().nextInt(10) + 1;
    this.http = HttpServer.create(new InetSocketAddress("localhost", port), 0);
    
    http.createContext("/prepare", exchange -> {
      int proposalId = Integer.parseInt(new String(exchange.getRequestBody().readAllBytes()).trim());
      String response;

      synchronized (this) {
        if (proposalId > obiecanyId) {
          obiecanyId = proposalId;
          if (zaakceptowanaWartosc != -1)
            response = "PROMISE,ACCEPTED," + zaakceptowanyId + "," + zaakceptowanaWartosc;
          else
            response = "PROMISE,NONE," + poczatkowaWartosc;
        } else {
          response = "REJECT";
        }
      }

      exchange.sendResponseHeaders(200, response.length());
      exchange.getResponseBody().write(response.getBytes());
      exchange.close();
    });

    http.createContext("/promise", exchange -> {
      String resp = String.format("PROMISE_STATE,%d,%d,%d", obiecanyId, zaakceptowanyId, zaakceptowanaWartosc);
      exchange.sendResponseHeaders(200, resp.length());
      exchange.getResponseBody().write(resp.getBytes());
      exchange.close();
    });

    http.createContext("/accept", exchange -> {
      String[] parts = new String(exchange.getRequestBody().readAllBytes()).trim().split(",");
      int proposalId = Integer.parseInt(parts[0]);
      int value = Integer.parseInt(parts[1]);
      String response;

      synchronized (this) {
        if (proposalId >= obiecanyId) {
          obiecanyId = proposalId;
          zaakceptowanyId = proposalId;
          zaakceptowanaWartosc = value;
          response = "ACCEPTED," + id + "," + value;
        } else {
          response = "REJECT";
        }
      }

      exchange.sendResponseHeaders(200, response.length());
      exchange.getResponseBody().write(response.getBytes());
      exchange.close();
    });

    http.createContext("/accepted", exchange -> {
      String resp = String.format("ACCEPTED_STATE,%d,%d,%d", obiecanyId, zaakceptowanyId, zaakceptowanaWartosc);
      exchange.sendResponseHeaders(200, resp.length());
      exchange.getResponseBody().write(resp.getBytes());
      exchange.close();
    });

    http.setExecutor(Executors.newCachedThreadPool());
  }

  public void start() {
    http.start();
    System.out.printf("Serwer %d dziala na porcie %d (poczatkowa wartosc=%d)%n",
        id, ((InetSocketAddress) http.getAddress()).getPort(), poczatkowaWartosc);
  }
}
