package com.example.pro_spring.service;

import com.example.pro_spring.exception.ServerException;
import com.example.pro_spring.model.Promise;
import com.example.pro_spring.util.HttpUtil;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

/**
 * Serwis implementujacy Paxos. Kazda instancja reprezentuje pojedynczy wezel w klastrze Paxosa.
 * Komunikacja pomiedzy wezlami odbywa sie przez wywolania HTTP.
 */
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
  private static final int MAJORITY = 5;
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
  @Getter
  private volatile boolean stuck = false;
  @Getter
  private volatile String stuckMessage = "STUCK";

  private static final Logger log = LoggerFactory.getLogger(PaxosServer.class);

  private static final String LOCALHOST = "http://localhost:";

  /**
   * Tworzy instancje serwera Paxos.
   *
   * @param port     port HTTP serwera
   * @param id       identyfikator
   * @param leader   port poczatkowego lidera
   * @param executor executor watkow
   * @param ctx      kontekst Springa
   */
  public PaxosServer(@Value("${server.port}") int port, @Value("${paxos.id}") int id,
      @Value("${paxos.leaderPort}") int leader, ThreadPoolTaskExecutor executor,
      ConfigurableApplicationContext ctx
  ) {
    this.port = port;
    this.id = id;
    this.executor = executor;
    this.ctx = ctx;
    setLeaderPort(leader);

    log.info(" SERVER {} Wlaczony na porcie {} (leader={}) %n", id, port, leaderPort);
  }

  /**
   * Zwraca port aktualnego lidera.
   */
  public static synchronized int getLeaderPort() {
    return leaderPort;
  }

  /**
   * Ustawia nowy port lidera.
   */
  public static synchronized void setLeaderPort(int p) {
    if (leaderPort == p) {
      return;
    }

    log.info("LEADER CHANGE: {} -> {}%n", leaderPort, p);
    leaderPort = p;
  }

  /**
   * Sprawdza, czy serwer pod wskazanym adresem odpowiada na zapytania.
   *
   * @param url adres serwera
   * @return true jesli serwer odpowiada poprawnym stanem, false w przeciwnym razie
   */
  private static boolean isAlive(String url) {
    try {
      String resp = HttpUtil.postParams(url + "/accepted_state");
      return resp != null && resp.startsWith("STATE");
    } catch (Exception e) {
      return false;
    }
  }


  private Integer discoverLeaderFromCluster() {

    Integer bestLeader = null;

    for (String s : SERVERS) {

      try {
        String resp = HttpUtil.postParams(s + "/leader");

        if (resp == null) {
          continue;
        }

        int leader = Integer.parseInt(resp.trim());

        boolean alive = isAlive(LOCALHOST + leader);
        boolean betterThanCurrent =
            bestLeader == null || leader < bestLeader;

        if (alive && betterThanCurrent) {
          bestLeader = leader;
        }

      } catch (ServerException e) {
        log.info("[SERVER {}] {} niedostepny%n", port, s);
      }
    }

    return bestLeader;
  }

  /**
   * Przy starcie programu wykrywa czy juz zostal ustalony lider, jezeli nie rozpoczona elekcje.
   */
  @PostConstruct
  public void discoverLeaderOnStartup() {
    executor.submit(() -> {

      Integer discovered = discoverLeaderFromCluster();

      if (discovered != null) {
        setLeaderPort(discovered);
        log.info(
            "[SERVER {}] Odkryto istniejacego lidera: {}%n",
            port, discovered
        );
        return;
      }

      electNewLeader();
    });
  }

  /**
   * Recznie ustawia promisedProposal.
   */
  public synchronized void injectPromised(int x) {
    promisedProposal = x;
    log.info("[SERVER {}] INJECT promised={}%n", port, x);
  }

  /**
   * Recznie ustawia acceptedProposal.
   */
  public synchronized void injectAcceptedProposal(int x) {
    acceptedProposal = x;
    log.info("[SERVER {}] INJECT acceptedProposal={}%n", port, x);
  }

  /**
   * Recznie ustawia acceptedValue.
   */
  public synchronized void injectAcceptedValue(int x) {
    acceptedValue = x;
    log.info("[SERVER {}] INJECT acceptedValue={}%n", port, x);
  }

  /**
   * Okresowo sprawdza dostepnosc lidera i inicjuje elekcje w razie awarii.
   */
  @Scheduled(fixedDelay = 3000)
  public void watcher() {

    if (!running || stuck) {
      return;
    }

    int leader = getLeaderPort();

    if (leader == -1) {
      log.info("[SERVER {}] Brak lidera – odkrywanie%n", port);
      discoverLeaderOnRecovery();
      return;
    }

    if (leader == port) {
      return;
    }

    if (!isAlive(LOCALHOST + leader)) {
      log.info(
          "[SERVER {}] Leader {} nie zyje – start elekcji%n",
          port, leader
      );
    }
    electNewLeader();
  }

  /**
   * Ustalenie portu lidera po powrocie z stanu stuck.
   */
  private void discoverLeaderOnRecovery() {
    Integer discovered = discoverLeaderFromCluster();

    if (discovered != null) {
      setLeaderPort(discovered);
    } else {
      electNewLeader();
    }
  }

  /**
   * Przeprowadza wybor nowego lidera na podstawie najnizszego portu.
   */
  private void electNewLeader() {

    int currentLeader = getLeaderPort();
    if (currentLeader != port
        && isAlive(LOCALHOST + currentLeader)) {
      return;
    }

    List<Integer> ports = new ArrayList<>();

    if (!stuck) {
      ports.add(port);
    }

    for (String s : SERVERS) {
      try {
        String resp = HttpUtil.postParams(s + "/election");

        if (resp == null) {
          continue;
        }

        int p = Integer.parseInt(resp.trim());
        ports.add(p);

      } catch (NumberFormatException e) {
        log.info("[SERVER {}] {} jest STUCK ({})%n", port, s, e.getMessage());
      }
    }

    if (ports.isEmpty()) {
      log.info("[SERVER {}] Brak kandydatow na lidera%n", port);
      return;
    }

    int winner = ports.stream().min(Integer::compare).orElse(port);
    setLeaderPort(winner);

    log.info("[SERVER {}] Nowy leader wybrany = {}%n", port, winner);
  }


  /**
   * Symuluje awarie serwera i zamyka kontekst aplikacji.
   */
  public void crash() {
    running = false;
    log.info("[SERVER {}] Crash za 300ms%n", port);

    new Thread(() -> {
      try {
        Thread.sleep(300);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      ctx.close();
    }).start();
  }

  /**
   * Inicjuje nowa runde Paxosa.
   */
  public void startPaxos(int value) {
    executor.submit(() -> runPaxosRound(value));
  }

  /**
   * Zapisuje poprzedni stan do rollbacku.
   */
  private synchronized void savePrevState() {
    prevPromisedProposal = promisedProposal;
    prevAcceptedProposal = acceptedProposal;
    prevAcceptedValue = acceptedValue;
  }

  /**
   * Przywraca poprzedni stan serwera.
   */
  public synchronized void rollback() {
    promisedProposal = prevPromisedProposal;
    acceptedProposal = prevAcceptedProposal;
    acceptedValue = prevAcceptedValue;
    log.info("[SERVER {}] ROLLBACK -> ({},{},{})%n", port, promisedProposal,
        acceptedProposal, acceptedValue);
  }

  /**
   * Wysyla polecenie rollback do wszystkich wskazanych serwerow.
   *
   * @param servers lista adresow serwerow
   */
  private void rollbackAll(List<String> servers) {
    for (String s : servers) {
      executor.submit(() -> {
        String resp = HttpUtil.postParams(s + "/rollback");
        log.info("[LIDER {}] -> ROLLBACK wyslany do {} => {}%n", port, s, resp);
      });
    }
  }

  /**
   * Blokuje serwer i wymusza zwracanie stalego komunikatu.
   *
   * @param message komunikat zwracany zamiast normalnych odpowiedzi
   */
  public synchronized void stuck(String message) {
    this.stuck = true;
    this.stuckMessage = message;
    log.info("[SERVER {}] Serwer zaciety z wiadomoscia: {}%n", port, message);
  }


  /**
   * Odblokowuje serwer.
   */
  public synchronized void unstuck() {
    this.stuck = false;
    this.stuckMessage = null;
    setLeaderPort(-1);
    executor.submit(this::discoverLeaderOnRecovery);
    log.info("[SERVER {}] Serwer wraca do normalnego dzialania: %n", port);
  }


  /**
   * Realizuje faze PREPARE protokolu Paxos.
   *
   * @param alive      lista aktywnych serwerow
   * @param proposalId identyfikator propozycji
   * @return lista otrzymanych obietnic (PROMISE)
   */
  private List<Promise> preparePhase(List<String> alive, long proposalId) {

    List<Promise> promises = Collections.synchronizedList(new ArrayList<>());
    CountDownLatch latch = new CountDownLatch(alive.size());

    for (String s : alive) {
      executor.submit(() -> {
        try {
          log.info("[LIDER {}] -> PREPARE do {}%n", port, s);
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

    log.info("[LIDER {}] Otrzymane PROMISE: {}%n",
        port,
        promises.stream()
            .map(p -> "(" + p.acceptedProposal() + "," + p.acceptedValue() + ")")
            .toList());

    return promises;
  }


  /**
   * Wybiera wartosc do zaakceptowania na podstawie otrzymanych PROMISE.
   *
   * @param promises    lista obietnic od acceptorow
   * @param clientValue wartosc zaproponowana przez klienta
   * @return wybrana wartosc lub null jesli brak wiekszosci
   */
  private Integer chooseValueFromPromises(List<Promise> promises, int clientValue) {

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

  /**
   * Realizuje faze ACCEPT protokolu Paxos.
   *
   * @param alive      lista aktywnych serwerow
   * @param proposalId identyfikator propozycji
   * @param value      wartosc do zaakceptowania
   * @return liczba serwerow, ktore zaakceptowaly wartosc
   */
  private int acceptPhase(List<String> alive, long proposalId, int value) {

    CountDownLatch latch = new CountDownLatch(alive.size());
    List<Boolean> accepts = Collections.synchronizedList(new ArrayList<>());

    for (String s : alive) {
      executor.submit(() -> {
        try {
          log.info("[LIDER {}] -> ACCEPT do {}, {}%n", port, s, value);
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

    log.info("[LIDER {}] Ilosc ACCEPT = {}%n",
        port, accepts.size());

    return accepts.size();
  }


  /**
   * Wykonuje pelna runde Paxosa: PREPARE, PREPARED, ACCEPT ACCEPTED.
   *
   * @param clientValue wartosc zaproponowana przez klienta
   */
  private void runPaxosRound(int clientValue) {

    long proposalId = (System.currentTimeMillis() & 0xFFFFFFF) + id;

    log.info("%n[LIDER {}] Poczatek rundy paxosa%n", port);
    log.info("[LIDER {}] proposalId={}, clientValue={}%n",
        port, proposalId, clientValue);

    List<String> alive = collectAlive();
    log.info("[LIDER {}] Dzialajace serwery ({}): {}%n",
        port, alive.size(), alive);

    if (alive.isEmpty()) {
      log.info("[LIDER {}] Brak zywych serwerow - koniec%n", port);
      return;
    }

    List<Promise> promises = preparePhase(alive, proposalId);

    if (promises.size() < MAJORITY) {
      log.info("[LIDER {}] Brak wiekszosci w PREPARE ({}/{}) — ROLLBACK%n",
          port, promises.size(), MAJORITY);
      rollbackAll(alive);
      return;
    }

    Integer chosenValue = chooseValueFromPromises(promises, clientValue);
    if (chosenValue == null) {
      log.info("[LIDER {}] Brak wiekszosci na zadna wartosc — ROLLBACK%n", port);
      rollbackAll(alive);
      return;
    }

    log.info("[LIDER {}] Ustalona wartosc = {}%n", port, chosenValue);

    int acceptedCount = acceptPhase(alive, proposalId, chosenValue);

    if (acceptedCount >= MAJORITY) {
      log.info("[LIDER {}] Finalna, ustalona wartosc = {}%n", port, chosenValue);
    } else {
      log.info("[LIDER {}] Brak wiekszosci w ACCEPT — ROLLBACK%n", port);
      rollbackAll(alive);
    }
  }


  /**
   * Obsluguje zadanie PREPARE jako acceptor.
   *
   * @param proposalId identyfikator propozycji
   * @return odpowiedz PROMISE, REJECT lub komunikat blokady
   */
  public synchronized String prepare(long proposalId) {
    if (stuck) {
      return stuckMessage;
    }
    if (!running) {
      return null;
    }

    log.info("[SERVER {}] <- PREPARE proposalId={}%n", port, proposalId);

    if (Math.random() < FAIL_CHANCE) {
      log.info("[SERVER {}] Brak odpowiedzi - symulacja awarii komunikacji w PREPARE %n",
          port);
      return null;
    }

    if (proposalId > promisedProposal) {
      savePrevState();

      log.info("[SERVER {}] -> PROMISE (accepted=({},{}))%n", port, acceptedProposal,
          acceptedValue);

      promisedProposal = (int) proposalId;

      if (acceptedProposal != -1) {
        return "PROMISE," + acceptedProposal + "," + acceptedValue;
      } else {
        return "PROMISE,NONE";
      }
    }

    log.info("[SERVER {}] -> REJECT (promised={})%n", port, promisedProposal);

    return "REJECT";
  }

  /**
   * Obsluguje zadanie ACCEPT jako acceptor.
   *
   * @param proposalId identyfikator propozycji
   * @param value      wartosc do zaakceptowania
   * @return odpowiedz ACCEPTED, REJECT lub komunikat blokady
   */
  public synchronized String accept(long proposalId, int value) {
    if (stuck) {
      return stuckMessage;
    }

    if (!running) {
      return null;
    }

    if (Math.random() < FAIL_CHANCE) {
      log.info("[SERVER {}] Brak odpowiedzi - symulacja awarii komunikacji w ACCEPT %n",
          port);
      return null;
    }

    log.info("[SERVER {}] <- ACCEPT proposalId={} value={}%n", port, proposalId, value);

    if (proposalId >= promisedProposal) {
      savePrevState();

      promisedProposal = (int) proposalId;
      acceptedProposal = (int) proposalId;
      acceptedValue = value;

      log.info("[SERVER {}] -> ACCEPTED ({},{})%n", port, acceptedProposal, acceptedValue);

      return "ACCEPTED," + proposalId + "," + value;
    }

    log.info("[SERVER {}] -> REJECT (promised={})%n", port, promisedProposal);

    return "REJECT," + proposalId + "," + value;
  }


  /**
   * Zwraca aktualny stan serwera.
   *
   * @return tekstowa reprezentacja stanu Paxosa
   */
  public synchronized String state() {
    if (stuck) {
      return stuckMessage;
    }
    return "STATE," + promisedProposal + "," + acceptedProposal + "," + acceptedValue;
  }


  /**
   * Czysci caly lokalny stan serwera.
   */
  public synchronized void clear() {
    promisedProposal = -1;
    acceptedProposal = -1;
    acceptedValue = -1;

    prevPromisedProposal = -1;
    prevAcceptedProposal = -1;
    prevAcceptedValue = -1;

    log.info("[SERVER {}] Wyczyszczono dane %n", port);

  }

  /**
   * Zbiera liste aktualnie dostepnych serwerow.
   *
   * @return lista adresow serwerow odpowiadajacych na zapytania
   */
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
