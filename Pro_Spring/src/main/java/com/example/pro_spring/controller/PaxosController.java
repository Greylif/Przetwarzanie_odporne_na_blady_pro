package com.example.pro_spring.controller;

import static com.example.pro_spring.service.PaxosServer.getLeaderPort;

import com.example.pro_spring.service.PaxosServer;
import com.example.pro_spring.util.HttpUtil;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


/**
 * Kontroler REST obslugujacy endpointy protokolu Paxos.
 *
 * Kontroler deleguje cala logike konsensusu do klasy PaxosServer.
 */
@RestController
public class PaxosController {

  private final PaxosServer server;

  /**
   * Tworzy kontroler Paxosa z wstrzyknietym serwerem.
   *
   * @param server instancja serwera Paxos
   */
  public PaxosController(PaxosServer server) {
    this.server = server;
  }

  /**
   * Endpoint kliencki do zglaszania propozycji wartosci.
   *
   * Propozycja moze zostac przyjeta wylacznie przez lidera.
   * Jesli serwer nie jest liderem, zwracany jest port aktualnego lidera.
   *
   * @param value wartosc proponowana przez klienta
   * @return informacja o przyjeciu propozycji lub o aktualnym liderze
   */
  @PostMapping("/client_propose")
  public String propose(@RequestParam Integer value) {
    if (server.isStuck()) {
      return server.getStuckMessage();
    }
    if (getLeaderPort() != server.getPort()) {
      return "NOT_LEADER," + getLeaderPort();
    }
    server.startPaxos(value);
    return "OK: proposal started by leader on port " + server.getPort();
  }

  /**
   * Obsluguje faze PREPARE protokolu Paxos.
   *
   * @param proposalId identyfikator propozycji
   * @return odpowiedz PROMISE lub REJECT
   */
  @PostMapping("/prepare")
  public String prepare(@RequestParam long proposalId) {
    if (server.isStuck()) {
      return server.getStuckMessage();
    }
    return server.prepare(proposalId);
  }


  /**
   * Obsluguje faze ACCEPT protokolu Paxos.
   *
   * @param proposalId identyfikator propozycji
   * @param value wartosc do zaakceptowania
   * @return odpowiedz ACCEPTED lub REJECTED
   */
  @PostMapping("/accept")
  public String accept(@RequestParam long proposalId,
      @RequestParam int value) {
    if (server.isStuck()) {
      return server.getStuckMessage();
    }
    return server.accept(proposalId, value);
  }

  /**
   * Zwraca aktualny stan zaakceptowanej propozycji.
   *
   * @return aktualny stan serwera
   */
  @PostMapping("/accepted_state")
  public String state() {
    if (server.isStuck()) {
      return server.getStuckMessage();
    }
    return server.state();
  }

  /**
   * Endpoint wykorzystywany podczas wyboru lidera.
   *
   * @return port aktualnego serwera
   */
  @PostMapping("/election")
  public String election() {
    if (server.isStuck()) {
      return server.getStuckMessage();
    }
    return String.valueOf(server.getPort());
  }


  /**
   * Symuluje awarie serwera.
   *
   * Po wywolaniu serwer przestaje odpowiadac na zadania Paxosa.
   *
   * @return komunikat o awarii
   */
  @PostMapping("/crash")
  public String crash() {
    if (server.isStuck()) {
      return server.getStuckMessage();
    }
    server.crash();
    return "SERVER_CRASHED";
  }

  /**
   * Czysci lokalny stan serwera Paxos.
   *
   * @return informacja o wyczyszczeniu stanu
   */
  @PostMapping("/clear")
  public String clear() {
    if (server.isStuck()) {
      return server.getStuckMessage();
    }
    server.clear();
    return "STATE_CLEARED";
  }

  /**
   * Czysci stan wszystkich serwerow w systemie.
   *
   * Operacja moze zostac wykonana wylacznie przez lidera.
   *
   * @return raport z czyszczenia serwerow
   */
  @PostMapping("/clearall")
  public String clearAll() {
    if (server.isStuck()) {
      return server.getStuckMessage();
    }
    if (getLeaderPort() != server.getPort()) {
      return "NOT_LEADER," + getLeaderPort();
    }

    List<String> servers = List.of(
        "http://localhost:8000",
        "http://localhost:8001",
        "http://localhost:8002",
        "http://localhost:8003",
        "http://localhost:8004",
        "http://localhost:8005",
        "http://localhost:8006",
        "http://localhost:8007"
    );

    int count = 0;
    StringBuilder sb = new StringBuilder();

    for (String s : servers) {
      String resp = HttpUtil.postParams(s + "/clear");
      sb.append(s).append(" => ").append(resp).append("\n");
      if (resp != null) {
        count++;
      }
    }

    return "CLEARED: " + count + "\n" + sb;
  }

  /**
   * Recznie wstrzykuje wartosci do stanu serwera.
   *
   * Wykorzystywane glownie w testach i symulacjach bledow.
   *
   * @param promised nowa wartosc promised
   * @param acceptedProposal nowy numer zaakceptowanej propozycji
   * @param acceptedValue nowa zaakceptowana wartosc
   * @return status operacji
   */
  @PostMapping("/inject")
  public String inject(
      @RequestParam(required = false) Integer promised,
      @RequestParam(required = false) Integer acceptedProposal,
      @RequestParam(required = false) Integer acceptedValue
  ) {
    if (server.isStuck()) {
      return server.getStuckMessage();
    }

    if (promised != null) {
      server.injectPromised(promised);
    }
    if (acceptedProposal != null) {
      server.injectAcceptedProposal(acceptedProposal);
    }
    if (acceptedValue != null) {
      server.injectAcceptedValue(acceptedValue);
    }

    return "INJECT_OK";
  }

  /**
   * Przywraca poprzedni stan serwera.
   *
   * @return informacja o wykonaniu rollbacku
   */
  @PostMapping("/rollback")
  public String rollback() {
    if (server.isStuck()) {
      return server.getStuckMessage();
    }
    server.rollback();
    return "ROLLED_BACK";
  }

  /**
   * Blokuje serwer i wymusza zwracanie stalej odpowiedzi.
   *
   * @param msg komunikat zwracany podczas blokady
   * @return potwierdzenie zablokowania
   */
  @PostMapping("/stuck")
  public String stuck(@RequestParam String msg) {
    server.stuck(msg);
    return "Zablokowany z wiadomoscia:" + msg;
  }


  /**
   * Odblokowuje serwer i przywraca normalne dzialanie.
   *
   * @return potwierdzenie odblokowania
   */
  @PostMapping("/unstuck")
  public String unstuck() {
    server.unstuck();
    return "Odblokowany";
  }

  /**
   * Pobranie portu lidera
   *
   * @return port lidera
   */
  @PostMapping("/leader")
  public String leader() {
    return String.valueOf(getLeaderPort());
  }



}
