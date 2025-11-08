package paxos;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class LiderApp {
  private static final List<String> servers = List.of(
      "http://localhost:8000",
      "http://localhost:8001",
      "http://localhost:8002",
      "http://localhost:8003",
      "http://localhost:8004",
      "http://localhost:8005",
      "http://localhost:8006",
      "http://localhost:8007"
  );

  public static void main(String[] args) throws Exception {
    int leaderId = 0;
    if (args.length >= 1) leaderId = Integer.parseInt(args[0]);

    int majority = servers.size() / 2 + 1;
    int counter = 0;
    Random rand = new Random();

    while (true) {
      counter++;
      int proposalId = leaderId * 1_000_000 + counter;
      System.out.println("Rozpoczynam rundę proposalId=" + proposalId);

      Map<Integer, Integer> acceptedValuesCount = new HashMap<>();
      int promiseCount = 0;
      int highestAcceptedId = -1;
      Integer valueFromHighestAccepted = null;

      for (String s : servers) {
        try {
          String resp = post(s + "/prepare", String.valueOf(proposalId), 500);
          if (resp == null) continue;
          resp = resp.trim();

          if (resp.startsWith("REJECT")) {
            System.out.println("[" + s + "] Odrzucono prepare.");
            continue;
          }

          if (resp.startsWith("PROMISE,NONE,")) {
            int val = Integer.parseInt(resp.split(",")[2]);
            promiseCount++;
            acceptedValuesCount.put(val, acceptedValuesCount.getOrDefault(val, 0) + 1);
            System.out.println("[" + s + "] Obietnica bez wczesniejszej wartosci, wstepna=" + val);

          } else if (resp.startsWith("PROMISE,ACCEPTED,")) {
            String[] parts = resp.split(",");
            int acceptedId = Integer.parseInt(parts[2]);
            int val = Integer.parseInt(parts[3]);
            promiseCount++;
            acceptedValuesCount.put(val, acceptedValuesCount.getOrDefault(val, 0) + 1);

            if (acceptedId > highestAcceptedId) {
              highestAcceptedId = acceptedId;
              valueFromHighestAccepted = val;
            }
            System.out.println("[" + s + "] Obietnica z wczesniejsza wartoscia=" + val + " (id=" + acceptedId + ")");
          }

        } catch (Exception e) {
          System.out.println("[" + s + "] Blad prepare: " + e.getMessage());
        }
      }

      if (promiseCount < majority) {
        System.out.println("Brak wiekszosci w prepare (" + promiseCount + "/" + servers.size() + "). Ponawiam...");
        TimeUnit.MILLISECONDS.sleep(rand.nextInt(200) + 100);
        continue;
      }

      int valueToPropose;
      if (valueFromHighestAccepted != null) {
        valueToPropose = valueFromHighestAccepted;
      } else {
        valueToPropose = acceptedValuesCount.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(rand.nextInt(10) + 1);
      }

      System.out.println("Wybrana wartosc do akceptacji: " + valueToPropose);

      int acceptCount = 0;
      for (String s : servers) {
        try {
          String resp = post(s + "/accept", proposalId + "," + valueToPropose, 500);
          if (resp != null && resp.startsWith("ACCEPTED")) {
            acceptCount++;
            System.out.println("[" + s + "] Zaakceptowano wartosc " + valueToPropose);
          } else {
            System.out.println("[" + s + "] Odrzucono accept.");
          }
        } catch (Exception e) {
          System.out.println("[" + s + "] Blad accept: " + e.getMessage());
        }
      }

      if (acceptCount >= majority) {
        System.out.println("\n Wartosc finalna: " + valueToPropose +
            " zaakceptowana przez " + acceptCount + "/" + servers.size() + " serwerów.");

        for (String s : servers) {
          try {
            String state = post(s + "/accepted", "", 500);
            if (state != null)
              System.out.println("[" + s + "] Stan koncowy: " + state.trim());
          } catch (Exception e) {
            System.out.println("[" + s + "] Blad pobierania stanu: " + e.getMessage());
          }
        }
        break;
      } else {
        System.out.println("Brak wiekszosci w fazie accept (" + acceptCount + "/" + servers.size() + "). Ponawiam...");
        TimeUnit.MILLISECONDS.sleep(rand.nextInt(200) + 100);
      }
    }
  }

  private static String post(String urlStr, String body, int timeoutMs) throws IOException {
    URL url = new URL(urlStr);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setConnectTimeout(timeoutMs);
    conn.setReadTimeout(timeoutMs);
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);

    if (body != null && !body.isEmpty()) {
      conn.getOutputStream().write(body.getBytes());
    }

    int respCode = conn.getResponseCode();
    if (respCode != 200) return null;
    InputStream is = conn.getInputStream();
    String resp = new String(is.readAllBytes());
    conn.disconnect();
    return resp;
  }
}
