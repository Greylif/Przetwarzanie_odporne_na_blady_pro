package paxos;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class SerwerInstance {
  private final int id;
  private final int port;
  private volatile boolean running = true;

  private int promisedProposal = -1;
  private int acceptedProposal = -1;
  private int acceptedValue = -1;

  private HttpServer http;
  private final ExecutorService executor = Executors.newCachedThreadPool();

  private static final List<String> SERVERS = List.of(
      "http://localhost:8000",
      "http://localhost:8001",
      "http://localhost:8002",
      "http://localhost:8003",
      "http://localhost:8004",
      "http://localhost:8005",
      "http://localhost:8006",
      "http://localhost:8007"
  );

  private static volatile int currentLeaderPort = 8000;
  private static synchronized int getLeaderPort() { return currentLeaderPort; }
  private static synchronized void setLeaderPort(int p) { currentLeaderPort = p; }

  private volatile Thread leaderThread = null;
  private volatile boolean leaderThreadRunning = false;

  public SerwerInstance(int id, int port) throws IOException {
    this.id = id;
    this.port = port;

    http = HttpServer.create(new InetSocketAddress("localhost", port), 0);

    http.createContext("/client_propose", exchange -> {
      if (!running) { exchange.sendResponseHeaders(500, -1); return; }
      if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { exchange.sendResponseHeaders(405, -1); return; }

      int value = -1;
      String query = exchange.getRequestURI().getQuery();
      if (query != null && query.contains("value=")) {
        try {
          value = Integer.parseInt(query.split("value=")[1].split("&")[0]);
        } catch (Exception ignored) {}
      }

      if (value == -1) {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
        try {
          if (body.startsWith("{")) {
            body = body.replaceAll("[{}\"]", "");
            Map<String, String> map = new HashMap<>();
            for (String pair : body.split(",")) {
              String[] kv = pair.split(":");
              map.put(kv[0].trim(), kv[1].trim());
            }
            value = Integer.parseInt(map.get("value"));
          } else {
            value = Integer.parseInt(body);
          }
        } catch (Exception e) {
          sendResponse(exchange, "ERROR: invalid value, expect integer");
          return;
        }
      }

      if (value == -1) {
        sendResponse(exchange, "ERROR: missing 'value'");
        return;
      }

      if (getLeaderPort() != port) {
        sendResponse(exchange, "NOT_LEADER," + getLeaderPort());
        return;
      }

      int finalValue = value;
      executor.submit(() -> runPaxosRound(finalValue));
      sendResponse(exchange, "OK: proposal started by leader on port " + port + " (value=" + value + ")");
    });


    http.createContext("/prepare", exchange -> {
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
      long proposalId = Long.parseLong(body);
      String resp;
      synchronized (this) {
        if (proposalId > promisedProposal) {
          promisedProposal = (int) proposalId;
          if (acceptedProposal != -1) resp = "PROMISE," + acceptedProposal + "," + acceptedValue;
          else resp = "PROMISE,NONE";
        } else resp = "REJECT";
      }
      System.out.printf("[SERVER %d] /prepare from proposalId=%d => %s\n", port, proposalId, resp);
      sendResponse(exchange, resp);
    });

    http.createContext("/accept", exchange -> {
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
      String[] parts = body.split(",");
      long proposalId = Long.parseLong(parts[0]);
      int value = Integer.parseInt(parts[1]);
      String resp;
      synchronized (this) {
        if (proposalId >= promisedProposal) {
          promisedProposal = (int) proposalId;
          acceptedProposal = (int) proposalId;
          acceptedValue = value;
          resp = "ACCEPTED," + proposalId + "," + value;
        } else {
          resp = "REJECT," + proposalId + "," + value;
        }
      }
      System.out.printf("[SERVER %d] /accept from proposalId=%d, value=%d => %s\n", port, proposalId, value, resp);
      sendResponse(exchange, resp);
    });

    http.createContext("/accepted_state", exchange -> {
      String resp;
      synchronized (this) {
        resp = String.format("STATE,%d,%d,%d", promisedProposal, acceptedProposal, acceptedValue);
      }
      sendResponse(exchange, resp);
    });

    http.createContext("/election", exchange -> sendResponse(exchange, String.valueOf(port)));

    http.createContext("/crash", exchange -> {
      System.out.println("[SERVER " + port + "] Otrzymał CRASH – wyłączanie!");
      running = false;
      if (leaderThread != null) leaderThread.interrupt();
      new Thread(() -> {
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        try { http.stop(0); } catch (Exception ignored) {}
      }).start();
      sendResponse(exchange, "SERVER_CRASHED");
    });

    http.createContext("/clear", exchange -> {
      synchronized (this) {
        promisedProposal = -1;
        acceptedProposal = -1;
        acceptedValue = -1;
      }
      System.out.printf("[SERVER %d] Stan acceptora wyczyszczony!\n", port);
      sendResponse(exchange, "STATE_CLEARED");
    });


    http.createContext("/clearall", exchange -> {
      if (!running) { exchange.sendResponseHeaders(500, -1); return; }
      if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { exchange.sendResponseHeaders(405, -1); return; }

      if (getLeaderPort() != port) {
        sendResponse(exchange, "NOT_LEADER," + getLeaderPort());
        return;
      }

      List<String> alive = collectAliveServers();
      CountDownLatch latch = new CountDownLatch(alive.size());
      List<String> results = Collections.synchronizedList(new ArrayList<>());

      for (String s : alive) {
        executor.submit(() -> {
          try {
            String resp = post(s + "/clear", "", 800);
            results.add(s + " => " + resp);
          } catch (Exception e) {
            results.add(s + " => ERROR");
          }
          latch.countDown();
        });
      }

      try { latch.await(1500, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) {}
      System.out.printf("[LIDER %d] Wysłano CLEAR do %d serwerów\n", port, results.size());
      sendResponse(exchange, "CLEARED_ALL: " + results.size() + " serwerów\n" + String.join("\n", results));
    });

    http.createContext("/inject", exchange -> {
      if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { exchange.sendResponseHeaders(405, -1); return; }

      Map<String, Integer> params = new HashMap<>();

      String query = exchange.getRequestURI().getQuery();
      if (query != null) {
        for (String pair : query.split("&")) {
          String[] kv = pair.split("=");
          if (kv.length == 2) {
            try { params.put(kv[0], Integer.parseInt(kv[1])); } catch (Exception ignored) {}
          }
        }
      }

      if (params.isEmpty()) {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
        try {
          if (body.startsWith("{")) {
            body = body.replaceAll("[{}\"]", "");
            for (String pair : body.split(",")) {
              String[] kv = pair.split(":");
              params.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
            }
          } else {
            for (String pair : body.split(",")) {
              String[] kv = pair.split("=");
              params.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
            }
          }
        } catch (Exception e) {
          sendResponse(exchange, "ERROR: invalid format, use JSON or key=value pairs");
          return;
        }
      }

      synchronized (this) {
        if (params.containsKey("promised")) promisedProposal = params.get("promised");
        if (params.containsKey("acceptedProposal")) acceptedProposal = params.get("acceptedProposal");
        if (params.containsKey("acceptedValue")) acceptedValue = params.get("acceptedValue");
      }

      System.out.printf("[SERVER %d] Wstrzyknięto stan: promised=%d, acceptedProposal=%d, acceptedValue=%d\n",
          port, promisedProposal, acceptedProposal, acceptedValue);

      sendResponse(exchange, String.format(
          "INJECT_OK: promised=%d, acceptedProposal=%d, acceptedValue=%d",
          promisedProposal, acceptedProposal, acceptedValue));
    });


    http.setExecutor(Executors.newCachedThreadPool());
  }



  private void sendResponse(HttpExchange exchange, String resp) throws IOException {
    byte[] out = resp.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
    exchange.sendResponseHeaders(200, out.length);
    exchange.getResponseBody().write(out);
    exchange.close();
  }

  public void start() {
    http.start();
    System.out.printf("Serwer %d działa na porcie %d (lider: %d)\n", id, port, getLeaderPort());

    new Thread(this::leaderWatcher, "Watcher-" + id).start();
    if (getLeaderPort() == port) startLeaderLoop();
  }

  private synchronized void startLeaderLoop() {
    if (leaderThreadRunning) return;
    leaderThreadRunning = true;
    leaderThread = new Thread(this::leaderLoop, "LeaderThread-" + port);
    leaderThread.start();
  }

  private void leaderLoop() {
    try {
      while (running && getLeaderPort() == port) {
        try { Thread.sleep(8000); } catch (InterruptedException ie) { break; }
      }
    } finally {
      leaderThreadRunning = false;
      leaderThread = null;
    }
  }

  private void runPaxosRound(int clientValue) {
    long proposalId = (System.currentTimeMillis() & 0xFFFFFFF) * 100 + id;
    System.out.printf("[LIDER %d] Paxos round start: proposalId=%d, value=%d\n", port, proposalId, clientValue);

    List<String> alive = collectAliveServers();
    int majority = alive.size()/2 + 1;

    List<Promise> promises = Collections.synchronizedList(new ArrayList<>());
    CountDownLatch latch = new CountDownLatch(alive.size());

    for (String s : alive) {
      executor.submit(() -> {
        try {
          String resp = post(s + "/prepare", String.valueOf(proposalId), 1200);
          if (resp != null && resp.startsWith("PROMISE")) {
            String[] parts = resp.split(",");
            if (parts.length == 3 && !"NONE".equals(parts[1])) {
              promises.add(new Promise(true, Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
            } else promises.add(new Promise(true, -1, -1));
            System.out.printf("[LIDER %d] Otrzymano %s od %s\n", port, resp, s);
          } else if (resp != null) {
            System.out.printf("[LIDER %d] Odrzucono PREPARE od %s => %s\n", port, s, resp);
          }
        } catch (Exception ignored) {}
        latch.countDown();
      });
    }

    try { latch.await(1500, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) {}
    long promiseCount = promises.stream().filter(p -> p.promised).count();
    if (promiseCount < majority) {
      System.out.printf("[LIDER %d] Brak większości PROMISE (%d/8)\n", port, promiseCount);
      return;
    }

    int valueToPropose;
    Optional<Promise> highest = promises.stream()
        .filter(p -> p.acceptedProposal >= 0)
        .max(Comparator.comparingInt(p -> p.acceptedProposal));
    if (highest.isPresent()) valueToPropose = highest.get().acceptedValue;
    else valueToPropose = clientValue;

    CountDownLatch latch2 = new CountDownLatch(alive.size());
    List<Boolean> accepts = Collections.synchronizedList(new ArrayList<>());
    for (String s : alive) {
      executor.submit(() -> {
        try {
          String resp = post(s + "/accept", proposalId + "," + valueToPropose, 1200);
          if (resp != null && resp.startsWith("ACCEPTED")) accepts.add(true);
          System.out.printf("[LIDER %d] Otrzymano %s od %s\n", port, resp, s);
        } catch (Exception ignored) {}
        latch2.countDown();
      });
    }

    try { latch2.await(1500, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) {}
    int acceptCount = accepts.size();
    if (acceptCount >= majority)
      System.out.printf("[LIDER %d] Wartość %d zaakceptowana (większość %d/%d)\n", port, valueToPropose, acceptCount, majority);
    else
      System.out.printf("[LIDER %d] Brak większości w ACCEPT (%d/%d)\n", port, acceptCount, majority);
  }

  private List<String> collectAliveServers() {
    List<String> alive = new ArrayList<>();
    for (String s : SERVERS) if (isAlive(s)) alive.add(s);
    return alive;
  }

  private void leaderWatcher() {
    while (running) {
      try {
        Thread.sleep(4000);
        if (getLeaderPort() == port) continue;

        String leaderUrl = "http://localhost:" + getLeaderPort();
        if (!isAlive(leaderUrl)) electNewLeader();
      } catch (InterruptedException ignored) {}
    }
  }

  private void electNewLeader() {
    List<Integer> ports = new ArrayList<>();
    ports.add(port);

    for (String s : SERVERS) {
      int p = extractPort(s);
      if (p == port) continue;
      try {
        if (!isAlive(s)) continue;
        String resp = post(s + "/election", "", 300);
        if (resp != null) ports.add(Integer.parseInt(resp.trim()));
      } catch (Exception ignored) {}
    }

    int min = ports.stream().min(Integer::compare).orElse(port);
    setLeaderPort(min);
    if (min == port) startLeaderLoop();
    System.out.printf("[SERVER %d] Nowy lider: %d\n", port, min);
  }

  private static boolean isAlive(String url) {
    try {
      String resp = post(url + "/accepted_state", "", 300);
      return resp != null;
    } catch (Exception e) { return false; }
  }

  private static String post(String urlStr, String body, int timeoutMs) throws IOException {
    URL url = new URL(urlStr);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setConnectTimeout(timeoutMs);
    conn.setReadTimeout(timeoutMs);
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    if (body != null && !body.isEmpty()) conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    int code = conn.getResponseCode();
    if (code != 200) return null;
    InputStream is = conn.getInputStream();
    String resp = new String(is.readAllBytes(), StandardCharsets.UTF_8);
    conn.disconnect();
    return resp;
  }

  private static int extractPort(String url) {
    try { return Integer.parseInt(url.substring(url.lastIndexOf(":") + 1)); }
    catch (Exception e) { return -1; }
  }

  private static class Promise {
    boolean promised;
    int acceptedProposal;
    int acceptedValue;
    Promise(boolean promised, int acceptedProposal, int acceptedValue) {
      this.promised = promised;
      this.acceptedProposal = acceptedProposal;
      this.acceptedValue = acceptedValue;
    }
  }

  public static void main(String[] args) throws Exception {
    List<SerwerInstance> list = new ArrayList<>();
    for (int i = 0; i < SERVERS.size(); i++) {
      int port = 8000 + i;
      SerwerInstance s = new SerwerInstance(i, port);
      s.start();
      list.add(s);
    }
    System.out.println("Wszystkie serwery uruchomione. POST -> http://localhost:8000/client_propose (body: number lub JSON {\"value\":42})");
    System.out.println("Dodatkowo POST -> http://localhost:8000/clear aby wyczyścić stan acceptora");
    Thread.currentThread().join();
  }
}
