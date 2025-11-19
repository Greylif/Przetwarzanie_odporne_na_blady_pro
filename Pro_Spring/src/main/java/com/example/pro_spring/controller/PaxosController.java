package com.example.pro_spring.controller;

import org.springframework.web.bind.annotation.*;
import com.example.pro_spring.service.PaxosServer;
import com.example.pro_spring.util.HttpUtil;

import java.util.*;

@RestController
public class PaxosController {

  private final PaxosServer server;

  public PaxosController(PaxosServer server) {
    this.server = server;
  }


  @PostMapping("/client_propose")
  public String propose(@RequestParam Integer value) {

    if (PaxosServer.getLeaderPort() != server.getPort())
      return "NOT_LEADER," + PaxosServer.getLeaderPort();

    server.startPaxos(value);
    return "OK: proposal started by leader on port " + server.getPort();
  }


  @PostMapping("/prepare")
  public String prepare(@RequestParam long proposalId) {
    return server.prepare(proposalId);
  }

  @PostMapping("/accept")
  public String accept(@RequestParam long proposalId,
      @RequestParam int value) {

    return server.accept(proposalId, value);
  }

  @PostMapping("/accepted_state")
  public String state() {
    return server.state();
  }

  @PostMapping("/election")
  public String election() {
    return String.valueOf(server.getPort());
  }


  @PostMapping("/crash")
  public String crash() {
    server.crash();
    return "SERVER_CRASHED";
  }

  @PostMapping("/clear")
  public String clear() {
    server.clear();
    return "STATE_CLEARED";
  }

  @PostMapping("/clearall")
  public String clearAll() {
    if (PaxosServer.getLeaderPort() != server.getPort())
      return "NOT_LEADER," + PaxosServer.getLeaderPort();

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
      if (resp != null) count++;
    }

    return "CLEARED: " + count + "\n" + sb;
  }


  @PostMapping("/inject")
  public String inject(
      @RequestParam(required = false) Integer promised,
      @RequestParam(required = false) Integer acceptedProposal,
      @RequestParam(required = false) Integer acceptedValue
  ) {

    if (promised != null) server.injectPromised(promised);
    if (acceptedProposal != null) server.injectAcceptedProposal(acceptedProposal);
    if (acceptedValue != null) server.injectAcceptedValue(acceptedValue);

    return "INJECT_OK";
  }
}
