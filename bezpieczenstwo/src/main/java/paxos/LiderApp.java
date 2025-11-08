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
          } else if (resp.startsWith("NONE,")) {
            promiseCount++;
            int pv = Integer.parseInt(resp.split(",")[1]);
            acceptedValuesCount.put(pv, acceptedValuesCount.getOrDefault(pv, 0) + 1);
          } else if (resp.startsWith("ACCEPTED,")) {
            String[] parts = resp.split(",");
            int acceptedId = Integer.parseInt(parts[1]);
            int val = Integer.parseInt(parts[2]);
            promiseCount++;
            acceptedValuesCount.put(val, acceptedValuesCount.getOrDefault(val, 0) + 1);

            if (acceptedId > highestAcceptedId) {
              highestAcceptedId = acceptedId;
              valueFromHighestAccepted = val;
            }
          }
        } catch (Exception e) {
        }
      }

      if (promiseCount < majority) {
        System.out.println("Brak większości w prepare (" + promiseCount + "/" + servers.size() + "). Ponawiam...");
        TimeUnit.MILLISECONDS.sleep(rand.nextInt(200) + 100);
        continue;
      }

      int valueToPropose;
      if (valueFromHighestAccepted != null) {
        valueToPropose = valueFromHighestAccepted;
      } else {
        valueToPropose = acceptedValuesCount.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .get()
            .getKey();
      }

      System.out.println("Wybrana wartość do accept: " + valueToPropose);

      int acceptCount = 0;
      for (String s : servers) {
        try {
          String resp = post(s + "/accept", proposalId + "," + valueToPropose, 500);
          if (resp != null && resp.trim().equals("OK")) {
            acceptCount++;
          }
        } catch (Exception e) {
        }
      }

      if (acceptCount >= majority) {
        System.out.println("Konsensus osiągnięty! Wartość: " + valueToPropose + " zaakceptowana przez " + acceptCount + " serwerów.");
        break;
      } else {
        System.out.println("Brak większości w fazie accept (" + acceptCount + "/" + servers.size() + "). Ponawiam...");
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
    conn.getOutputStream().write(body.getBytes());
    int respCode = conn.getResponseCode();
    if (respCode != 200) return null;
    InputStream is = conn.getInputStream();
    String resp = new String(is.readAllBytes());
    conn.disconnect();
    return resp;
  }
}
