package com.example.pro_spring.service;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.example.pro_spring.model.Promise;
import com.example.pro_spring.util.HttpUtil;

import java.util.*;
import java.util.concurrent.CountDownLatch;

@Service
public class PaxosServer {

  @Getter
  private final int id;

  @Getter
  private final int port;

  private volatile boolean running = true;

  private int promisedProposal = -1;
  private int acceptedProposal = -1;
  private int acceptedValue = -1;

  private final ThreadPoolTaskExecutor executor;

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

  private static volatile int leaderPort;

  public static synchronized int getLeaderPort() { return leaderPort; }
  public static synchronized void setLeaderPort(int p) { leaderPort = p; }

  private final ConfigurableApplicationContext ctx;

  public PaxosServer(
      @Value("${server.port}") int port,
      @Value("${paxos.id}") int id,
      @Value("${paxos.leaderPort}") int leader,
      ThreadPoolTaskExecutor executor,
      ConfigurableApplicationContext ctx
  ) {
    this.id = id;
    this.port = port;
    leaderPort = leader;
    this.executor = executor;
    this.ctx = ctx;

    System.out.println("Serwer " + id + " działa na porcie " + port + " (lider: " + leaderPort + ")");
  }

  public synchronized void injectPromised(int x) { promisedProposal = x; }
  public synchronized void injectAcceptedProposal(int x) { acceptedProposal = x; }
  public synchronized void injectAcceptedValue(int x) { acceptedValue = x; }



  private static boolean isAlive(String s) {
    try {
      String r = HttpUtil.post(s + "/accepted_state", "", 300);
      return r != null && r.startsWith("STATE");
    } catch (Exception e) {
      return false;
    }
  }

  private List<String> collectAlive() {
    List<String> alive = new ArrayList<>();
    for (String s : SERVERS) {
      if (isAlive(s)) alive.add(s);
    }
    return alive;
  }


  @Scheduled(fixedDelay = 3000)
  public void watcher() {
    if (!running) return;
    if (getLeaderPort() == port) return;

    if (!isAlive("http://localhost:" + getLeaderPort())) {
      electNewLeader();
    }
  }

  private void electNewLeader() {
    List<Integer> ports = new ArrayList<>();
    ports.add(port);

    for (String s : SERVERS) {
      try {
        String resp = HttpUtil.post(s + "/election", "", 300);
        if (resp != null)
          ports.add(Integer.parseInt(resp.trim()));
      } catch (Exception ignored) {}
    }

    int newLeader = ports.stream().min(Integer::compare).orElse(port);
    setLeaderPort(newLeader);

    System.out.printf("[SERVER %d] Nowy lider = %d%n", port, newLeader);
  }


  public void crash() {
    running = false;
    System.out.printf("[SERVER %d] crashing...%n", port);
    ctx.close();
  }

  public boolean isRunning() {
    return running;
  }


  public void startPaxos(int clientValue) {
    executor.submit(() -> runPaxosRound(clientValue));
  }

  private void runPaxosRound(int clientValue) {

    long proposalId = (System.currentTimeMillis() & 0xFFFFFFF) * 100 + id;

    System.out.printf("\n[LIDER %d] start Paxos: proposalId=%d value=%d%n",
        port, proposalId, clientValue);

    List<String> alive = collectAlive();
    int majority = alive.size() / 2 + 1;

    System.out.printf("[LIDER %d] ALIVE SERVERS (%d): %s%n",
        port, alive.size(), alive);

    // PHASE 1 — prepare
    List<Promise> promises = Collections.synchronizedList(new ArrayList<>());
    CountDownLatch latch1 = new CountDownLatch(alive.size());

    for (String s : alive) {
      executor.submit(() -> {
        try {
          String resp = HttpUtil.post(s + "/prepare", String.valueOf(proposalId), 1200);
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
        } catch (Exception ignored) {}
        latch1.countDown();
      });
    }

    try { latch1.await(); } catch (Exception ignored) {}

    System.out.printf("[LIDER %d] PROMISES: %s%n",
        port,
        promises.stream()
            .map(p -> "(" + p.acceptedProposal + "," + p.acceptedValue + ")")
            .toList());

    if (promises.size() < majority) {
      System.out.printf("[LIDER %d] brak większości PROMISE%n", port);
      return;
    }

    int valueToPropose =
        promises.stream()
            .filter(p -> p.acceptedProposal >= 0)
            .max(Comparator.comparingInt(p -> p.acceptedProposal))
            .map(p -> p.acceptedValue)
            .orElse(clientValue);

    System.out.printf("[LIDER %d] FINAL VALUE TO PROPOSE = %d%n",
        port, valueToPropose);

    // PHASE 2 — accept
    CountDownLatch latch2 = new CountDownLatch(alive.size());
    List<Boolean> accepts = Collections.synchronizedList(new ArrayList<>());

    for (String s : alive) {
      executor.submit(() -> {
        try {
          String resp = HttpUtil.post(
              s + "/accept",
              proposalId + "," + valueToPropose,
              1200
          );
          if (resp != null && resp.startsWith("ACCEPTED"))
            accepts.add(true);
        } catch (Exception ignored) {}
        latch2.countDown();
      });
    }

    try { latch2.await(); } catch (Exception ignored) {}

    System.out.printf("[LIDER %d] ACCEPT RESPONSES = %d (majority=%d)%n",
        port, accepts.size(), majority);

    if (accepts.size() >= majority)
      System.out.printf("[LIDER %d] WARTOŚĆ ZAAKCEPTOWANA = %d%n",
          port, valueToPropose);
    else
      System.out.printf("[LIDER %d] BRAK MAJORITY ACCEPT%n", port);
  }


  public synchronized String prepare(long proposalId) {
    if (!running) return null;

    if (proposalId > promisedProposal) {
      promisedProposal = (int) proposalId;
      if (acceptedProposal != -1)
        return "PROMISE," + acceptedProposal + "," + acceptedValue;
      else
        return "PROMISE,NONE";
    }
    return "REJECT";
  }

  public synchronized String accept(long proposalId, int value) {
    if (!running) return null;

    if (proposalId >= promisedProposal) {
      promisedProposal = (int) proposalId;
      acceptedProposal = (int) proposalId;
      acceptedValue = value;
      return "ACCEPTED," + proposalId + "," + value;
    }
    return "REJECT," + proposalId + "," + value;
  }

  public synchronized String state() {
    if (!running) return null;
    return "STATE," + promisedProposal + "," + acceptedProposal + "," + acceptedValue;
  }

  public synchronized void clear() {
    promisedProposal = -1;
    acceptedProposal = -1;
    acceptedValue = -1;
  }
}
