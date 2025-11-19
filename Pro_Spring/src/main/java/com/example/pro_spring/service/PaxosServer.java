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
  private final ConfigurableApplicationContext ctx;

  private static volatile int leaderPort;
  public static synchronized int getLeaderPort() { return leaderPort; }
  public static synchronized void setLeaderPort(int p) { leaderPort = p; }

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
    leaderPort = leader;

    System.out.printf("== SERVER %d STARTED on port %d (leader=%d)==%n", id, port, leaderPort);
  }

  
  @Scheduled(fixedDelay = 3000)
  public void watcher() {
    if (!running) return;
    if (getLeaderPort() == port) return;

    if (!isAlive("http://localhost:" + getLeaderPort())) {
      System.out.printf("[SERVER %d] Leader %d unreachable — starting election%n", port, getLeaderPort());
      electNewLeader();
    }
  }

  private void electNewLeader() {
    List<Integer> ports = new ArrayList<>();
    ports.add(port);

    for (String s : SERVERS) {
      try {
        String resp = HttpUtil.post(s + "/election", "", 300);
        if (resp != null) ports.add(Integer.parseInt(resp));
      } catch (Exception ignored) {}
    }

    int winner = ports.stream().min(Integer::compare).orElse(port);
    setLeaderPort(winner);

    System.out.printf("[SERVER %d] NEW LEADER ELECTED = %d%n", port, winner);
  }

  public void crash() {
    running = false;
    System.out.printf("[SERVER %d] !! CRASHING !!%n", port);
    ctx.close();
  }

  public void startPaxos(int value) {
    executor.submit(() -> runPaxosRound(value));
  }

  private void runPaxosRound(int clientValue) {

    long proposalId = (System.currentTimeMillis() & 0xFFFFFFF) * 100 + id;

    System.out.printf("%n[LIDER %d] ===== PAXOS ROUND START =====%n", port);
    System.out.printf("[LIDER %d] proposalId=%d, clientValue=%d%n",
        port, proposalId, clientValue);

    List<String> alive = collectAlive();
    int majority = alive.size() / 2 + 1;

    System.out.printf("[LIDER %d] ALIVE SERVERS (%d): %s%n",
        port, alive.size(), alive);

    // PHASE 1 — PREPARE
    List<Promise> promises = Collections.synchronizedList(new ArrayList<>());
    CountDownLatch latch1 = new CountDownLatch(alive.size());

    for (String s : alive) {
      executor.submit(() -> {
        try {
          System.out.printf("[LIDER %d] -> PREPARE to %s%n", port, s);
          String resp = HttpUtil.post(s + "/prepare", String.valueOf(proposalId), 1200);

          if (resp != null) {
            System.out.printf("[LIDER %d] <- %s RESPONSE: %s%n", port, s, resp);
            if (resp.startsWith("PROMISE")) {
              String[] p = resp.split(",");
              if (p.length == 3 && !"NONE".equals(p[1])) {
                promises.add(new Promise(true,
                    Integer.parseInt(p[1]),
                    Integer.parseInt(p[2])));
              } else {
                promises.add(new Promise(true, -1, -1));
              }
            }
          }
        } catch (Exception ignored) {}
        latch1.countDown();
      });
    }

    try { latch1.await(); } catch (Exception ignored) {}

    System.out.printf("[LIDER %d] PROMISES RECEIVED: %s%n",
        port,
        promises.stream()
            .map(p -> "(" + p.acceptedProposal + "," + p.acceptedValue + ")")
            .toList());

    if (promises.size() < majority) {
      System.out.printf("[LIDER %d] NO MAJORITY in PREPARE%n", port);
      return;
    }

    int valueToPropose =
        promises.stream()
            .filter(p -> p.acceptedProposal >= 0)
            .max(Comparator.comparingInt(p -> p.acceptedProposal))
            .map(p -> p.acceptedValue)
            .orElse(clientValue);

    System.out.printf("[LIDER %d] FINAL VALUE = %d%n", port, valueToPropose);

    // PHASE 2 — ACCEPT
    CountDownLatch latch2 = new CountDownLatch(alive.size());
    List<Boolean> accepts = Collections.synchronizedList(new ArrayList<>());

    for (String s : alive) {
      executor.submit(() -> {
        try {
          System.out.printf("[LIDER %d] -> ACCEPT to %s%n", port, s);
          String resp = HttpUtil.post(
              s + "/accept",
              proposalId + "," + valueToPropose,
              1200
          );

          if (resp != null) {
            System.out.printf("[LIDER %d] <- %s RESPONSE: %s%n", port, s, resp);
            if (resp.startsWith("ACCEPTED")) accepts.add(true);
          }

        } catch (Exception ignored) {}
        latch2.countDown();
      });
    }

    try { latch2.await(); } catch (Exception ignored) {}

    System.out.printf("[LIDER %d] ACCEPT COUNT = %d (majority=%d)%n",
        port, accepts.size(), majority);

    if (accepts.size() >= majority)
      System.out.printf("[LIDER %d] VALUE DECIDED = %d%n", port, valueToPropose);
    else
      System.out.printf("[LIDER %d] NO MAJORITY in ACCEPT%n", port);
  }


  public synchronized String prepare(long proposalId) {
    if (!running) return null;

    System.out.printf("[SERVER %d] <- PREPARE proposalId=%d%n", port, proposalId);

    if (proposalId > promisedProposal) {
      System.out.printf("[SERVER %d] -> PROMISE (accepted=(%d,%d))%n",
          port, acceptedProposal, acceptedValue);

      promisedProposal = (int) proposalId;

      if (acceptedProposal != -1)
        return "PROMISE," + acceptedProposal + "," + acceptedValue;
      else
        return "PROMISE,NONE";
    }

    System.out.printf("[SERVER %d] -> REJECT (promised=%d)%n",
        port, promisedProposal);

    return "REJECT";
  }


  public synchronized String accept(long proposalId, int value) {
    if (!running) return null;

    System.out.printf("[SERVER %d] <- ACCEPT proposalId=%d value=%d%n",
        port, proposalId, value);

    if (proposalId >= promisedProposal) {
      promisedProposal = (int) proposalId;
      acceptedProposal = (int) proposalId;
      acceptedValue = value;

      System.out.printf("[SERVER %d] -> ACCEPTED (%d,%d)%n",
          port, acceptedProposal, acceptedValue);

      return "ACCEPTED," + proposalId + "," + value;
    }

    System.out.printf("[SERVER %d] -> REJECT (promised=%d)%n",
        port, promisedProposal);

    return "REJECT," + proposalId + "," + value;
  }


  public synchronized String state() {
    System.out.printf("[SERVER %d] STATE_REQ => (%d,%d,%d)%n",
        port, promisedProposal, acceptedProposal, acceptedValue);

    return "STATE," + promisedProposal + "," +
        acceptedProposal + "," +
        acceptedValue;
  }

  public synchronized void clear() {
    promisedProposal = -1;
    acceptedProposal = -1;
    acceptedValue = -1;

    System.out.printf("[SERVER %d] STATE CLEARED%n", port);
  }

  private static boolean isAlive(String url) {
    try {
      String resp = HttpUtil.post(url + "/accepted_state", "", 300);
      return resp != null && resp.startsWith("STATE");
    } catch (Exception e) {
      return false;
    }
  }

  private List<String> collectAlive() {
    List<String> list = new ArrayList<>();
    for (String s : SERVERS) if (isAlive(s)) list.add(s);
    return list;
  }
}
