package com.example.pro_spring.service;

import com.example.pro_spring.model.Promise;
import com.example.pro_spring.util.HttpUtil;
import java.util.ArrayList;
import java.util.Collections;
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
  
  private static int MAJORITY = 5;

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
        String resp = HttpUtil.postParams(s + "/election");
        if (resp != null) {
          ports.add(Integer.parseInt(resp.trim()));
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
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
        ctx.close();
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
          String resp = HttpUtil.postParams(s + "/rollback");
          System.out.printf("[LIDER %d] -> ROLLBACK wyslany do %s => %s%n", port, s, resp);
      });
    }
  }


  private List<Promise> preparePhase(List<String> alive, long proposalId) {

    List<Promise> promises = Collections.synchronizedList(new ArrayList<>());
    CountDownLatch latch = new CountDownLatch(alive.size());

    for (String s : alive) {
      executor.submit(() -> {
        try {
          System.out.printf("[LIDER %d] -> PREPARE do %s%n", port, s);
          String resp = HttpUtil.postParams(
              s + "/prepare?proposalId=" + proposalId);

          if (resp != null && resp.startsWith("PROMISE")) {
            String[] p = resp.split(",");
            if (p.length == 3 && !"NONE".equals(p[1])) {
              promises.add(new Promise(true,
                  Integer.parseInt(p[1]),
                  Integer.parseInt(p[2])));
            } else {
              promises.add(new Promise(true, -1, -1));
            }
          }
        } finally {
          latch.countDown();
        }
      });
    }

    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }


    System.out.printf("[LIDER %d] Otrzymane PROMISE: %s%n",
        port,
        promises.stream()
            .map(p -> "(" + p.acceptedProposal() + "," + p.acceptedValue() + ")")
            .toList());

    return promises;
  }


  private Integer chooseValueFromPromises(
      List<Promise> promises,
      int clientValue
  ) {

    Map<Integer, Integer> counts = new HashMap<>();

    for (Promise p : promises) {
      int val = p.acceptedProposal() >= 0 ? p.acceptedValue() : -1;
      counts.put(val, counts.getOrDefault(val, 0) + 1);
    }

    for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
      if (e.getValue() >= MAJORITY) {
        return e.getKey() == -1 ? clientValue : e.getKey();
      }
    }

    return null;
  }

  private int acceptPhase(
      List<String> alive,
      long proposalId,
      int value
  ) {

    CountDownLatch latch = new CountDownLatch(alive.size());
    List<Boolean> accepts = Collections.synchronizedList(new ArrayList<>());

    for (String s : alive) {
      executor.submit(() -> {
        try {
          System.out.printf("[LIDER %d] -> ACCEPT do %s, %d%n", port, s, value);
          String resp = HttpUtil.postParams(
              s + "/accept?proposalId=" + proposalId + "&value=" + value);

          if (resp != null && resp.startsWith("ACCEPTED")) {
            accepts.add(true);
          }
        } finally {
          latch.countDown();
        }
      });
    }

    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }


    System.out.printf("[LIDER %d] Ilosc ACCEPT = %d%n",
        port, accepts.size());

    return accepts.size();
  }



  private void runPaxosRound(int clientValue) {

    long proposalId = (System.currentTimeMillis() & 0xFFFFFFF) + id;

    System.out.printf("%n[LIDER %d] Poczatek rundy paxosa%n", port);
    System.out.printf("[LIDER %d] proposalId=%d, clientValue=%d%n",
        port, proposalId, clientValue);

    List<String> alive = collectAlive();
    System.out.printf("[LIDER %d] Dzialajace serwery (%d): %s%n",
        port, alive.size(), alive);

    if (alive.isEmpty()) {
      System.out.printf("[LIDER %d] Brak zywych serwerow - koniec%n", port);
      return;
    }
    

    // Faza 1 PREPARE
    List<Promise> promises = preparePhase(alive, proposalId);

    if (promises.size() < MAJORITY) {
      System.out.printf("[LIDER %d] Brak wiekszosci w PREPARE (%d/%d) — ROLLBACK%n",
          port, promises.size(), MAJORITY);
      rollbackAll(alive);
      return;
    }

    Integer chosenValue = chooseValueFromPromises(promises, clientValue);
    if (chosenValue == null) {
      System.out.printf("[LIDER %d] Brak większości na żadną wartość — ROLLBACK%n", port);
      rollbackAll(alive);
      return;
    }

    System.out.printf("[LIDER %d] Ustalona wartosc = %d%n", port, chosenValue);

    // Faza 2 ACCEPT
    int acceptedCount = acceptPhase(alive, proposalId, chosenValue);

    if (acceptedCount >= MAJORITY) {
      System.out.printf("[LIDER %d] Finalna, ustalona wartosc = %d%n",
          port, chosenValue);
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
