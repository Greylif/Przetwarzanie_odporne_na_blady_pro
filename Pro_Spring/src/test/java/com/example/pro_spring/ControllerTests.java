package com.example.pro_spring;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.pro_spring.controller.PaxosController;
import com.example.pro_spring.service.PaxosServer;
import com.example.pro_spring.util.HttpUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = PaxosController.class,
    excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@DisplayName("Testy PaxosController")
class ControllerTests {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private PaxosServer server;

  @Nested
  @DisplayName("/client_propose")
  class ClientProposeTests {

    @Test
    @DisplayName("Serwer nie jest liderem")
    void notLeader() throws Exception {
      when(server.getPort()).thenReturn(8001);
        PaxosServer.setLeaderPort(8000);

      mockMvc.perform(post("/client_propose")
              .param("value", "10"))
          .andExpect(status().isOk())
          .andExpect(content().string("NOT_LEADER,8000"));

      verify(server, never()).startPaxos(anyInt());
    }

    @Test
    @DisplayName("Serwer jest liderem")
    void isLeader() throws Exception {
      when(server.getPort()).thenReturn(8000);
        PaxosServer.setLeaderPort(8000);

      mockMvc.perform(post("/client_propose")
              .param("value", "42"))
          .andExpect(status().isOk())
          .andExpect(content().string("OK: proposal started by leader on port 8000"));

      verify(server).startPaxos(42);
    }
  }

  @Test
  @DisplayName("/prepare – wywoluje server.prepare")
  void prepare() throws Exception {
    when(server.prepare(100L)).thenReturn("PROMISE");

    mockMvc.perform(post("/prepare")
            .param("proposalId", "100"))
        .andExpect(status().isOk())
        .andExpect(content().string("PROMISE"));
  }

  @Test
  @DisplayName("/accept – wywoluje server.accept")
  void accept() throws Exception {
    when(server.accept(200L, 55)).thenReturn("ACCEPTED");

    mockMvc.perform(post("/accept")
            .param("proposalId", "200")
            .param("value", "55"))
        .andExpect(status().isOk())
        .andExpect(content().string("ACCEPTED"));
  }

  @Test
  @DisplayName("/accepted_state – zwraca stan")
  void state() throws Exception {
    when(server.state()).thenReturn("STATE,1,1,10");

    mockMvc.perform(post("/accepted_state"))
        .andExpect(status().isOk())
        .andExpect(content().string("STATE,1,1,10"));
  }

  @Test
  @DisplayName("/election – zwraca port")
  void election() throws Exception {
    when(server.getPort()).thenReturn(8003);

    mockMvc.perform(post("/election"))
        .andExpect(status().isOk())
        .andExpect(content().string("8003"));
  }

  @Test
  @DisplayName("/crash – crashuje serwer")
  void crash() throws Exception {
    doNothing().when(server).crash();

    mockMvc.perform(post("/crash"))
        .andExpect(status().isOk())
        .andExpect(content().string("SERVER_CRASHED"));

    verify(server).crash();
  }

  @Test
  @DisplayName("/clear – czysci stan")
  void clear() throws Exception {
    doNothing().when(server).clear();

    mockMvc.perform(post("/clear"))
        .andExpect(status().isOk())
        .andExpect(content().string("STATE_CLEARED"));

    verify(server).clear();
  }

  @Nested
  @DisplayName("/clearall")
  class ClearAllTests {

    @Test
    @DisplayName("Serwer nie jest liderem")
    void clearAllNotLeader() throws Exception {
      when(server.getPort()).thenReturn(8001);
        PaxosServer.setLeaderPort(8000);

      mockMvc.perform(post("/clearall"))
          .andExpect(status().isOk())
          .andExpect(content().string("NOT_LEADER,8000"));
    }

    @Test
    @DisplayName("Serwer jest liderem - wywoluje clearall")
    void clearAllLeader() throws Exception {

      PaxosServer.setLeaderPort(8000);
      when(server.getPort()).thenReturn(8000);

      try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {

        http.when(() -> HttpUtil.postParams(contains("/clear")))
            .thenReturn("CLEARED");

        mockMvc.perform(post("/clearall"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("CLEARED")));
      }
    }


    @Test
    @DisplayName("/clearall – resp != null zwieksza licznik")
    void clearAllCountsNonNullResponses() throws Exception {
      when(server.isStuck()).thenReturn(false);
      when(server.getPort()).thenReturn(8000);
      PaxosServer.setLeaderPort(8000);

      try (MockedStatic<HttpUtil> httpMock = mockStatic(HttpUtil.class)) {

        httpMock.when(() -> HttpUtil.postParams(anyString()))
            .thenReturn("OK");

        mockMvc.perform(post("/clearall"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("CLEARED: 8")));
      }
    }


    @Test
    @DisplayName("/clearall – resp == null NIE zwieksza licznika")
    void clearAllCountsNullResponses() throws Exception {
      when(server.isStuck()).thenReturn(false);
      when(server.getPort()).thenReturn(8000);
      PaxosServer.setLeaderPort(8000);

      try (MockedStatic<HttpUtil> httpMock = mockStatic(HttpUtil.class)) {

        httpMock.when(() -> HttpUtil.postParams(anyString()))
            .thenReturn(null);

        mockMvc.perform(post("/clearall"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("CLEARED: 0")));
      }
    }

  }

  @Nested
  @DisplayName("/inject")
  class InjectTests {

    @Test
    @DisplayName("Brak parametrow")
    void injectNothing() throws Exception {
      mockMvc.perform(post("/inject"))
          .andExpect(status().isOk())
          .andExpect(content().string("INJECT_OK"));

      verify(server, never()).injectPromised(anyInt());
      verify(server, never()).injectAcceptedProposal(anyInt());
      verify(server, never()).injectAcceptedValue(anyInt());

    }

    @Test
    @DisplayName("Wszystkie parametry")
    void injectAll() throws Exception {
      mockMvc.perform(post("/inject")
              .param("promised", "1")
              .param("acceptedProposal", "10")
              .param("acceptedValue", "20"))
          .andExpect(status().isOk());

      verify(server).injectPromised(1);
      verify(server).injectAcceptedProposal(10);
      verify(server).injectAcceptedValue(20);
    }
  }


  @Test
  @DisplayName("/rollback – rollback stanu")
  void rollback() throws Exception {
    doNothing().when(server).rollback();

    mockMvc.perform(post("/rollback"))
        .andExpect(status().isOk())
        .andExpect(content().string("ROLLED_BACK"));

    verify(server).rollback();
  }

  @Test
  @DisplayName("/stuck – ustawia serwer w stan STUCK")
  void stuckEndpoint() throws Exception {
    doNothing().when(server).stuck("ERROR");

    mockMvc.perform(post("/stuck")
            .param("msg", "ERROR"))
        .andExpect(status().isOk())
        .andExpect(content().string("Zablokowany z wiadomoscia:ERROR"));

    verify(server).stuck("ERROR");
  }

  @Test
  @DisplayName("/unstuck – odblokowuje serwer")
  void unstuckEndpoint() throws Exception {
    doNothing().when(server).unstuck();

    mockMvc.perform(post("/unstuck"))
        .andExpect(status().isOk())
        .andExpect(content().string("Odblokowany"));

    verify(server).unstuck();
  }


  @Test
  @DisplayName("SERVER STUCK – client_propose")
  void clientProposeStuck() throws Exception {
    when(server.isStuck()).thenReturn(true);
    when(server.getStuckMessage()).thenReturn("STUCK");

    mockMvc.perform(post("/client_propose")
            .param("value", "10"))
        .andExpect(status().isOk())
        .andExpect(content().string("STUCK"));

    verify(server, never()).startPaxos(anyInt());
  }


  @Test
  @DisplayName("SERVER STUCK – prepare")
  void prepareStuck() throws Exception {
    when(server.isStuck()).thenReturn(true);
    when(server.getStuckMessage()).thenReturn("STUCK");

    mockMvc.perform(post("/prepare")
            .param("proposalId", "123"))
        .andExpect(status().isOk())
        .andExpect(content().string("STUCK"));

    verify(server, never()).prepare(anyLong());
  }

  @Test
  @DisplayName("SERVER STUCK – inject")
  void injectStuck() throws Exception {
    when(server.isStuck()).thenReturn(true);
    when(server.getStuckMessage()).thenReturn("STUCK");

    mockMvc.perform(post("/inject")
            .param("promised", "1"))
        .andExpect(status().isOk())
        .andExpect(content().string("STUCK"));

    verify(server, never()).injectPromised(anyInt());
  }

  @Test
  @DisplayName("SERVER STUCK – /accept")
  void acceptStuck() throws Exception {
    when(server.isStuck()).thenReturn(true);
    when(server.getStuckMessage()).thenReturn("STUCK");

    mockMvc.perform(post("/accept")
            .param("proposalId", "1")
            .param("value", "10"))
        .andExpect(status().isOk())
        .andExpect(content().string("STUCK"));

    verify(server, never()).accept(anyLong(), anyInt());
  }

  @Test
  @DisplayName("SERVER STUCK – /accepted_state")
  void stateStuck() throws Exception {
    when(server.isStuck()).thenReturn(true);
    when(server.getStuckMessage()).thenReturn("STUCK");

    mockMvc.perform(post("/accepted_state"))
        .andExpect(status().isOk())
        .andExpect(content().string("STUCK"));

    verify(server, never()).state();
  }

  @Test
  @DisplayName("SERVER STUCK – /election")
  void electionStuck() throws Exception {
    when(server.isStuck()).thenReturn(true);
    when(server.getStuckMessage()).thenReturn("STUCK");

    mockMvc.perform(post("/election"))
        .andExpect(status().isOk())
        .andExpect(content().string("STUCK"));

    verify(server, never()).getPort();
  }

  @Test
  @DisplayName("SERVER STUCK – /clear")
  void clearStuck() throws Exception {
    when(server.isStuck()).thenReturn(true);
    when(server.getStuckMessage()).thenReturn("STUCK");

    mockMvc.perform(post("/clear"))
        .andExpect(status().isOk())
        .andExpect(content().string("STUCK"));

    verify(server, never()).clear();
  }

  @Test
  @DisplayName("SERVER STUCK – /clearall")
  void clearAllStuck() throws Exception {
    when(server.isStuck()).thenReturn(true);
    when(server.getStuckMessage()).thenReturn("STUCK");

    mockMvc.perform(post("/clearall"))
        .andExpect(status().isOk())
        .andExpect(content().string("STUCK"));

    verify(server, never()).getPort();
  }

  @Test
  @DisplayName("SERVER STUCK – /rollback")
  void rollbackStuck() throws Exception {
    when(server.isStuck()).thenReturn(true);
    when(server.getStuckMessage()).thenReturn("STUCK");

    mockMvc.perform(post("/rollback"))
        .andExpect(status().isOk())
        .andExpect(content().string("STUCK"));

    verify(server, never()).rollback();
  }

  @Test
  @DisplayName("SERVER STUCK – /crash")
  void crashStuck() throws Exception {
    when(server.isStuck()).thenReturn(true);
    when(server.getStuckMessage()).thenReturn("STUCK");

    mockMvc.perform(post("/crash"))
        .andExpect(status().isOk())
        .andExpect(content().string("STUCK"));

    verify(server, never()).rollback();
  }

  @Test
  @DisplayName("/leader – zwraca port lidera")
  void leaderEndpoint() throws Exception {
    PaxosServer.setLeaderPort(8005);

    mockMvc.perform(post("/leader"))
        .andExpect(status().isOk())
        .andExpect(content().string("8005"));
  }



}
