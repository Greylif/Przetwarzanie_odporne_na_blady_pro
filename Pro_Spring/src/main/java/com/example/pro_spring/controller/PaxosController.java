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
  public String propose(
      @RequestParam(required = false) Integer value,
      @RequestBody(required = false) String body
  ) {

    if (value == null && body != null) {
      try {
        if (body.startsWith("{")) {
          body = body.replaceAll("[{}\"]", "");
          Map<String, String> map = new HashMap<>();
          for (String p : body.split(",")) {
            String[] kv = p.split(":");
            map.put(kv[0].trim(), kv[1].trim());
          }
          value = Integer.parseInt(map.get("value"));
        } else {
          value = Integer.parseInt(body.trim());
        }
      } catch (Exception e) {
        return "ERROR: invalid value";
      }
    }

    if (value == null) return "ERROR: missing value";

    if (PaxosServer.getLeaderPort() != server.getPort())
      return "NOT_LEADER," + PaxosServer.getLeaderPort();

    server.startPaxos(value);
    return "OK: proposal started by leader on port " + server.getPort();
  }


  @PostMapping("/prepare")
  public String prepare(@RequestBody String body) {
    long proposalId = Long.parseLong(body);
    return server.prepare(proposalId);
  }


  @PostMapping("/accept")
  public String accept(@RequestBody String body) {
    String[] p = body.split(",");
    return server.accept(Long.parseLong(p[0]), Integer.parseInt(p[1]));
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
      String resp = HttpUtil.post(s + "/clear", "", 700);
      sb.append(s).append(" => ").append(resp).append("\n");
      if (resp != null) count++;
    }

    return "CLEARED: " + count + "\n" + sb;
  }



  @PostMapping("/inject")
  public String inject(@RequestBody String body,
      @RequestParam Map<String,String> query) {

    Map<String,Integer> map = new HashMap<>();

    if (!query.isEmpty()) {
      query.forEach((k,v)->{
        try { map.put(k, Integer.parseInt(v)); } catch(Exception ignored){}
      });
    }

    if (map.isEmpty() && body != null) {
      try {
        body = body.replaceAll("[{}\"]","");
        for (String p : body.split(",")) {
          String[] kv = p.split(":|=");
          map.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
        }
      } catch(Exception e) {
        return "ERROR: invalid body";
      }
    }

    if (map.containsKey("promised"))
      server.injectPromised(map.get("promised"));
    if (map.containsKey("acceptedProposal"))
      server.injectAcceptedProposal(map.get("acceptedProposal"));
    if (map.containsKey("acceptedValue"))
      server.injectAcceptedValue(map.get("acceptedValue"));

    return "INJECT_OK";
  }
}
