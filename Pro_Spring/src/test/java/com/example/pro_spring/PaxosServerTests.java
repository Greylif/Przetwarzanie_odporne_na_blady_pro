package com.example.pro_spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.example.pro_spring.model.Promise;
import com.example.pro_spring.service.PaxosServer;
import com.example.pro_spring.util.HttpUtil;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@DisplayName("Testy PaxosServer")
class PaxosServerTests {

  private PaxosServer server;
  private ThreadPoolTaskExecutor executor;
  private ConfigurableApplicationContext ctx;

  @BeforeEach
  void setup() {
    executor = mock(ThreadPoolTaskExecutor.class);
    ctx = mock(ConfigurableApplicationContext.class);

    server = new PaxosServer(
        8000, 1, 8000, executor, ctx
    );
  }

  @Nested
  @DisplayName("Inject - wstrzykiwanie danych")
  class InjectTests {

    @Test
    @DisplayName("injectPromised ustawia promisedProposal")
    void injectPromised() {
      server.injectPromised(10);
      assertThat(server.state()).contains("10");
    }

    @Test
    @DisplayName("injectAcceptedProposal ustawia acceptedProposal")
    void injectAcceptedProposal() {
      server.injectAcceptedProposal(5);
      assertThat(server.state()).contains(",5,");
    }

    @Test
    @DisplayName("injectAcceptedValue ustawia acceptedValue")
    void injectAcceptedValue() {
      server.injectAcceptedValue(42);
      assertThat(server.state()).endsWith(",42");
    }
  }

  @Nested
  @DisplayName("prepare() – faza PREPARE Paxosa")
  class PrepareTests {

    @Test
    @DisplayName("Serwer nieaktywny - prepare zwraca null")
    void prepareServerNotRunning() {
      server.crash();
      assertThat(server.prepare(1L)).isNull();
    }

    @Test
    @DisplayName("proposalId > promised - odpowiedz PROMISE,NONE")
    void prepareNoAccepted() {
      String resp = server.prepare(5L);
      assertThat(resp).isEqualTo("PROMISE,NONE");
    }

    @Test
    @DisplayName("proposalId > promised + accepted istnieje - odpowiedz PROMISE,proposal,value")
    void prepareReturnsPromise() {
      server.injectAcceptedProposal(3);
      server.injectAcceptedValue(99);

      String resp = server.prepare(10L);

      assertThat(resp).isEqualTo("PROMISE,3,99");
    }

    @Test
    @DisplayName("proposalId <= promised - odpowiedz REJECT")
    void prepareReject() {
      server.injectPromised(10);
      assertThat(server.prepare(5L)).isEqualTo("REJECT");
    }
  }

  @Nested
  @DisplayName("accept() – faza ACCEPT Paxosa")
  class AcceptTests {

    @Test
    @DisplayName("Serwer nieaktywny - accept zwraca null")
    void acceptServerNotRunning() {
      server.crash();
      assertThat(server.accept(1L, 10)).isNull();
    }

    @Test
    @DisplayName("proposalId >= promised - odpowiedz ACCEPTED + zapis stanu")
    void acceptAccepted() {
      String resp = server.accept(5L, 123);

      assertThat(resp).startsWith("ACCEPTED");
      assertThat(server.state()).contains(",5,123");
    }

    @Test
    @DisplayName("proposalId < promised - odpowiedz REJECT")
    void acceptReject() {
      server.injectPromised(10);
      assertThat(server.accept(5L, 77)).startsWith("REJECT");
    }
  }

    @Test
    @DisplayName("rollback przywraca poprzedni zaakceptowany stan")
    void rollbackRestore() {
      server.prepare(5L);
      server.accept(5L, 10);

      server.prepare(8L);
      server.accept(8L, 99);

      server.rollback();

      assertThat(server.state()).contains(",5,10");
    }

    @Test
    @DisplayName("clear resetuje caly stan serwera")
    void clearReset() {
      server.injectPromised(10);
      server.injectAcceptedProposal(5);
      server.injectAcceptedValue(50);

      server.clear();

      assertThat(server.state()).isEqualTo("STATE,-1,-1,-1");
    }

  @Nested
  @DisplayName("Obsluga stanu bledu stuck")
  class StuckTests {

    @Test
    @DisplayName("stuck ustawia flage i komunikat")
    void stuckSet() {
      server.stuck("ERROR");

      assertThat(server.isStuck()).isTrue();
      assertThat(server.getStuckMessage()).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("unstuck czysci flage i komunikat")
    void unstuckClear() {
      server.stuck("ERROR");
      server.unstuck();

      assertThat(server.isStuck()).isFalse();
      assertThat(server.getStuckMessage()).isNull();
    }
  }

  @Nested
  @DisplayName("startPaxos() – pelny przebieg protokolu Paxos")
  class StartPaxosTests {

    @Test
    @DisplayName("startPaxos zleca zadanie do executora")
    void startPaxos() {
      server.startPaxos(42);
      verify(executor).submit(any(Runnable.class));
    }

    @Test
    @DisplayName("Pelny poprawny przebieg PREPARE + ACCEPT")
    void paxosHappyPath() {

      doAnswer(inv -> {
        Runnable r = inv.getArgument(0);
        r.run();
        return null;
      }).when(executor).submit(any(Runnable.class));

      try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {

        http.when(() -> HttpUtil.postParams(anyString()))
            .thenAnswer(inv -> {
              String url = inv.getArgument(0);

              if (url.contains("/accepted_state")) {
                return "STATE,-1,-1,-1";
              }

              if (url.contains("/prepare")) {
                return "PROMISE,NONE";
              }

              if (url.contains("/accept")) {
                return "ACCEPTED,1,10";
              }

              return null;
            });

        server.startPaxos(10);

        http.verify(
            () -> HttpUtil.postParams(contains("/prepare")),
            atLeast(5)
        );
        http.verify(
            () -> HttpUtil.postParams(contains("/accept")),
            atLeast(5)
        );
      }
    }

    @Test
    @DisplayName("Brak wiekszosci w PREPARE - ROLLBACK")
    void paxosRollbackNoMajorityInPrepare() {

      doAnswer(inv -> {
        Runnable r = inv.getArgument(0);
        r.run();
        return null;
      }).when(executor).submit(any(Runnable.class));

      try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {

        AtomicInteger prepareCount = new AtomicInteger();

        http.when(() -> HttpUtil.postParams(anyString()))
            .thenAnswer(inv -> {
              String url = inv.getArgument(0);

              if (url.contains("/accepted_state")) {
                return "STATE,-1,-1,-1";
              }

              if (url.contains("/prepare")) {
                return prepareCount.incrementAndGet() <= 2
                    ? "PROMISE,NONE"
                    : "REJECT";
              }

              if (url.contains("/rollback")) {
                return "ROLLED_BACK";
              }

              return null;
            });

        server.startPaxos(10);

        http.verify(
            () -> HttpUtil.postParams(contains("/prepare")),
            atLeastOnce()
        );

        http.verify(
            () -> HttpUtil.postParams(argThat(
                url -> url.contains("/accept?")
            )),
            never()
        );

        http.verify(
            () -> HttpUtil.postParams(contains("/rollback")),
            atLeastOnce()
        );
      }
    }





  }

  @Nested
  @DisplayName("watcher() – wybor lidera")
  class WatcherTests {

    @Test
    void watcherElect() {
      PaxosServer.setLeaderPort(9000);

      try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {

        http.when(() ->
            HttpUtil.postParams("http://localhost:9000/accepted_state")
        ).thenReturn(null);

        http.when(() -> HttpUtil.postParams(contains("/election")))
            .thenAnswer(inv -> {
              String url = inv.getArgument(0);
              return url.replaceAll("\\D+", "");
            });

        server.watcher();

        assertThat(PaxosServer.getLeaderPort()).isEqualTo(8000);
      }
    }
  }


  @Test
  @DisplayName("chooseValueFromPromises – majority acceptedValue")
  void chooseValueFromPromisesReflection() throws Exception {

    Method method = PaxosServer.class.getDeclaredMethod(
        "chooseValueFromPromises", List.class, int.class
    );
    method.setAccessible(true);

    List<Promise> promises = List.of(
        new Promise(true, 1, 99),
        new Promise(true, 2, 99),
        new Promise(true, 3, 99),
        new Promise(true, 4, 99),
        new Promise(true, 5, 99),
        new Promise(true, 6, 11),
        new Promise(true, 7, 22),
        new Promise(true, 8, 33)
    );

    Integer result = (Integer) method.invoke(server, promises, 42);

    assertThat(result).isEqualTo(99);
  }






  @Test
  @DisplayName("isAlive – refleksja true")
  void isAliveTrueReflection() throws Exception {

    try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {

      http.when(() -> HttpUtil.postParams(anyString()))
          .thenReturn("STATE");

      Method method = PaxosServer.class.getDeclaredMethod(
          "isAlive", String.class
      );
      method.setAccessible(true);

      boolean alive = (boolean) method.invoke(
          server, "http://localhost:8000"
      );

      assertThat(alive).isTrue();
    }
  }


  @Test
  @DisplayName("isAlive – refleksja false")
  void isAliveFalseReflection() throws Exception {

    try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {

      http.when(() -> HttpUtil.postParams(anyString()))
          .thenReturn(null);

      Method method = PaxosServer.class.getDeclaredMethod(
          "isAlive", String.class
      );
      method.setAccessible(true);

      boolean alive = (boolean) method.invoke(
          server, "http://localhost:8000"
      );

      assertThat(alive).isFalse();
    }
  }


  @Test
  @DisplayName("collectAlive – refleksja")
  void collectAliveReflection() throws Exception {

    try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {

      http.when(() -> HttpUtil.postParams(contains("8000/accepted_state")))
          .thenReturn("STATE,-1,-1,-1");

      http.when(() -> HttpUtil.postParams(contains("8001/accepted_state")))
          .thenReturn(null);

      Method method = PaxosServer.class.getDeclaredMethod("collectAlive");
      method.setAccessible(true);

      @SuppressWarnings("unchecked")
      List<String> alive = (List<String>) method.invoke(server);

      assertThat(alive)
          .hasSize(1)
          .anyMatch(s -> s.contains("8000"));
    }
  }



  @Test
  @DisplayName("Brak acceptedValue – zwracana wartosc klienta")
  void chooseValueClientValue() throws Exception {
    List<Promise> promises = List.of(
        new Promise(true, -1, -1),
        new Promise(true, -1, -1),
        new Promise(true, -1, -1),
        new Promise(true, -1, -1),
        new Promise(true, -1, -1)
    );

    Method m = PaxosServer.class
        .getDeclaredMethod("chooseValueFromPromises", List.class, int.class);
    m.setAccessible(true);

    Integer result = (Integer) m.invoke(server, promises, 42);

    assertThat(result).isEqualTo(42);
  }

}
