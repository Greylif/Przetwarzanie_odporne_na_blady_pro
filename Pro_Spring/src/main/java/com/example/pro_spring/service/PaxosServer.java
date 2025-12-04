package com.example.pro_spring.service;

import com.example.pro_spring.model.Promise;
import com.example.pro_spring.util.HttpUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class PaxosServer {

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

  private static final double FAIL_CHANCE = 0.00;
  
  private static volatile int leaderPort;

  @Getter
  private final int id;
  @Getter
  private final int port;
  private final ThreadPoolTaskExecutor executor;
  private final ConfigurableApplicationContext ctx;
  private volatile boolean running = true;

  private int promisedProposal = -1;
  private int acceptedProposal = -1;
  private int acceptedValue = -1;

  private int prevPromisedProposal = -1;
  private int prevAcceptedProposal = -1;
  private int prevAcceptedValue = -1;

  public PaxosServer(
      @Value("${server.port}") int port,
      @Value("${paxos.id}") int id,
      @Value("${paxos.leaderPort}") int leader,
      ThreadPoolTaskExecutor executor,
      ConfigurableApplicationContext ctx
  ) {
    this.port = port;
    this.id = id;
    this.executor = executor;
    this.ctx = ctx;
    setLeaderPort(leader);

    System.out.printf(" SERVER %d Wlaczony na porcie %d (leader=%d) %n", id, port, leaderPort);
  }

  public static synchronized int getLeaderPort() {
    return leaderPort;
  }

  public static synchronized void setLeaderPort(int p) {
    leaderPort = p;
  }

  private static boolean isAlive(String url) {
    try {
      String resp = HttpUtil.postParams(url + "/accepted_state");
      return resp != null && resp.startsWith("STATE");
    } catch (Exception e) {
      return false;
    }
  }

  public synchronized void injectPromised(int x) {
    promisedProposal = x;
    System.out.printf("[SERVER %d] INJECT promised=%d%n", port, x);
  }

  public synchronized void injectAcceptedProposal(int x) {
    acceptedProposal = x;
    System.out.printf("[SERVER %d] INJECT acceptedProposal=%d%n", port, x);
  }

  public synchronized void injectAcceptedValue(int x) {
    acceptedValue = x;
    System.out.printf("[SERVER %d] INJECT acceptedValue=%d%n", port, x);
  }

  @Scheduled(fixedDelay = 3000)
  public void watcher() {
    if (!running) {
      return;
    }

    if (getLeaderPort() == port) {
      return;
    }

    if (!isAlive("http://localhost:" + getLeaderPort())) {
      System.out.printf("[SERVER %d] Leader %d jest nieosiagalny - poczatek elekcji %n", port,
          getLeaderPort());
      electNewLeader();
    }
  }

  private void electNewLeader() {
    List<Integer> ports = new ArrayList<>();
    ports.add(port);

    for (String s : SERVERS) {
      try {
        String resp = HttpUtil.postParams(s + "/election");
        if (resp != null) {
          ports.add(Integer.parseInt(resp.trim()));
        }
      } catch (Exception ignored) {
      }
    }

    int winner = ports.stream().min(Integer::compare).orElse(port);
    setLeaderPort(winner);

    System.out.printf("[SERVER %d] Nowy leader wybrany = %d%n", port, winner);
  }

  public void crash() {
    running = false;
    System.out.printf("[SERVER %d] Crash za 300ms%n", port);

    new Thread(() -> {
      try {
        Thread.sleep(300);
      } catch (Exception ignored) {
      }
      try {
        ctx.close();
      } catch (Exception ignored) {
      }
    }).start();
  }

  public void startPaxos(int value) {
    executor.submit(() -> runPaxosRound(value));
  }

  private synchronized void savePrevState() {
    prevPromisedProposal = promisedProposal;
    prevAcceptedProposal = acceptedProposal;
    prevAcceptedValue = acceptedValue;
  }

  public synchronized void rollback() {
    promisedProposal = prevPromisedProposal;
    acceptedProposal = prevAcceptedProposal;
    acceptedValue = prevAcceptedValue;
    System.out.printf("[SERVER %d] ROLLBACK -> (%d,%d,%d)%n",
        port, promisedProposal, acceptedProposal, acceptedValue);
  }

  private void rollbackAll(List<String> servers) {
    for (String s : servers) {
      executor.submit(() -> {
        try {
          String resp = HttpUtil.postParams(s + "/rollback");
          System.out.printf("[LIDER %d] -> ROLLBACK wyslany do %s => %s%n", port, s, resp);
        } catch (Exception ignored) {}
      });
    }
  }

  private void runPaxosRound(int clientValue) {

    long proposalId = (System.currentTimeMillis() & 0xFFFFFFF) + id;

    System.out.printf("%n[LIDER %d] Poczatek rundy paxosa %n", port);
    System.out.printf("[LIDER %d] proposalId=%d, clientValue=%d%n", port, proposalId, clientValue);

    List<String> alive = collectAlive();

    System.out.printf("[LIDER %d] Dzialajace serwery (%d): %s%n",
        port, alive.size(), alive);

    if (alive.isEmpty()) {
      System.out.printf("[LIDER %d] Brak zywych serwerow - koniec%n", port);
      return;
    }

    int majority = alive.size() / 2 + 1;

    // PHASE 1 — PREPARE
    List<Promise> promises = Collections.synchronizedList(new ArrayList<>());
    CountDownLatch latch1 = new CountDownLatch(alive.size());

    for (String s : alive) {
      if (Math.random() < FAIL_CHANCE) {
        System.out.printf("[LIDER %d] Brak wyslania - symulacjia awarii komunikacji do %s w PREPARE %n", port, s);
        latch1.countDown();
        continue;
      }

      executor.submit(() -> {
        try {
          System.out.printf("[LIDER %d] -> PREPARE dla %s%n", port, s);
          String resp = HttpUtil.postParams(s + "/prepare?proposalId=" + proposalId);

          if (resp != null) {
            System.out.printf("[LIDER %d] <- %s RESPONSE: %s%n", port, s, resp);
            if (resp.startsWith("PROMISE")) {
              String[] p = resp.split(",");
              if (p.length == 3 && !"NONE".equals(p[1])) {
                promises.add(new Promise(true, Integer.parseInt(p[1]), Integer.parseInt(p[2])));
              } else {
                promises.add(new Promise(true, -1, -1));
              }
            } else if (resp.startsWith("REJECT")) {
            }
          } else {
            System.out.printf("[LIDER %d] <- %s Brak odpowiedzi w PREPARE (treated as down)%n", port, s);
          }
        } catch (Exception ignored) {
        } finally {
          latch1.countDown();
        }
      });
    }

    try {
      latch1.await();
    } catch (Exception ignored) {
    }

    System.out.printf("[LIDER %d] Otrzymane PROMISE: %s%n", port, promises.stream()
        .map(p -> "(" + p.acceptedProposal + "," + p.acceptedValue + ")")
        .toList());

    if (promises.size() < majority) {
      System.out.printf("[LIDER %d] Brak wiekszosci w PREPARE (%d/%d) — ROLLBACK%n", port, promises.size(), majority);
      rollbackAll(alive);
      return;
    }

    Map<Integer, Integer> counts = new HashMap<>();
    for (Promise p : promises) {
      int val = p.acceptedProposal >= 0 ? p.acceptedValue : -1;
      counts.put(val, counts.getOrDefault(val, 0) + 1);
    }

    boolean hasMaj = false;
    int chosenValue = clientValue;
    for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
      if (e.getValue() >= majority) {
        hasMaj = true;
        if (e.getKey() == -1) {
          chosenValue = clientValue;
        } else {
          chosenValue = e.getKey();
        }
        break;
      }
    }

    if (!hasMaj) {
      System.out.printf("[LIDER %d] Brak większości na żadną wartość w PROMISES — ROLLBACK%n", port);
      rollbackAll(alive);
      return;
    }

    System.out.printf("[LIDER %d] Ustalona wartosc (na podstawie wiekszosci) = %d%n", port, chosenValue);

    // PHASE 2 — ACCEPT
    CountDownLatch latch2 = new CountDownLatch(alive.size());
    List<Boolean> accepts = Collections.synchronizedList(new ArrayList<>());

    for (String s : alive) {
      if (Math.random() < FAIL_CHANCE) {
        System.out.printf("[LIDER %d] Brak wyslania - symulacjia awarii komunikacji do %s w ACCEPT %n", port, s);
        latch2.countDown();
        continue;
      }

      int finalChosenValue = chosenValue;
      executor.submit(() -> {
        try {
          System.out.printf("[LIDER %d] -> ACCEPT do %s, %d %n", port, s, finalChosenValue);
          String resp = HttpUtil.postParams(
              s + "/accept?proposalId=" + proposalId + "&value=" + finalChosenValue
          );

          if (resp != null) {
            System.out.printf("[LIDER %d] <- %s RESPONSE: %s%n", port, s, resp);
            if (resp.startsWith("ACCEPTED")) {
              accepts.add(true);
            }
          } else {
            System.out.printf("[LIDER %d] <- %s Brak odpowiedzi dla ACCEPT (treated as down)%n", port, s);
          }

        } catch (Exception ignored) {
        } finally {
          latch2.countDown();
        }
      });
    }

    try {
      latch2.await();
    } catch (Exception ignored) {
    }

    System.out.printf("[LIDER %d] Ilosc otrzymanych ACCEPT = %d (majority=%d)%n", port,
        accepts.size(), majority);

    if (accepts.size() >= majority) {
      System.out.printf("[LIDER %d] Finalna, ustalona wartosc = %d%n", port, chosenValue);
    } else {
      System.out.printf("[LIDER %d] Brak wiekszosci w ACCEPT — ROLLBACK%n", port);
      rollbackAll(alive);
    }
  }

  public synchronized String prepare(long proposalId) {
    if (!running) {
      return null;
    }

    System.out.printf("[SERVER %d] <- PREPARE proposalId=%d%n", port, proposalId);

    if (Math.random() < FAIL_CHANCE) {
      System.out.printf("[SERVER %d] Brak odpowiedzi - symulacja awarii komunikacji w PREPARE %n", port);
      return null;
    }

    if (proposalId > promisedProposal) {
      savePrevState();

      System.out.printf("[SERVER %d] -> PROMISE (accepted=(%d,%d))%n", port, acceptedProposal,
          acceptedValue);

      promisedProposal = (int) proposalId;

      if (acceptedProposal != -1) {
        return "PROMISE," + acceptedProposal + "," + acceptedValue;
      } else {
        return "PROMISE,NONE";
      }
    }

    System.out.printf("[SERVER %d] -> REJECT (promised=%d)%n", port, promisedProposal);

    return "REJECT";
  }

  public synchronized String accept(long proposalId, int value) {
    if (!running) {
      return null;
    }

    if (Math.random() < FAIL_CHANCE) {
      System.out.printf("[SERVER %d] Brak odpowiedzi - symulacja awarii komunikacji w ACCEPT %n", port);
      return null;
    }

    System.out.printf("[SERVER %d] <- ACCEPT proposalId=%d value=%d%n", port, proposalId, value);

    if (proposalId >= promisedProposal) {
      savePrevState();

      promisedProposal = (int) proposalId;
      acceptedProposal = (int) proposalId;
      acceptedValue = value;

      System.out.printf("[SERVER %d] -> ACCEPTED (%d,%d)%n", port, acceptedProposal, acceptedValue);

      return "ACCEPTED," + proposalId + "," + value;
    }

    System.out.printf("[SERVER %d] -> REJECT (promised=%d)%n", port, promisedProposal);

    return "REJECT," + proposalId + "," + value;
  }

  public synchronized String state() {
    return "STATE," + promisedProposal + "," + acceptedProposal + "," + acceptedValue;
  }

  public synchronized void clear() {
    promisedProposal = -1;
    acceptedProposal = -1;
    acceptedValue = -1;

    prevPromisedProposal = -1;
    prevAcceptedProposal = -1;
    prevAcceptedValue = -1;

    System.out.printf("[SERVER %d] Wyczyszczono dane %n", port);
  }

  private List<String> collectAlive() {
    List<String> list = new ArrayList<>();
    for (String s : SERVERS) {
      if (isAlive(s)) {
        list.add(s);
      }
    }
    return list;
  }
}
