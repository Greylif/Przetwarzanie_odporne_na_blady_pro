package paxos;

public class Launcher {
  public static void main(String[] args) throws Exception {
    int numServers = 8;
    int basePort = 8000;

    System.out.println("Uruchamianie " + numServers + " serwerów Paxos (z liderem na porcie 8000)...");

    for (int i = 0; i < numServers; i++) {
      final int id = i;
      final int port = basePort + i;
      final boolean isLeader = (i == 0);
      new Thread(() -> {
        try {
          SerwerInstance s = new SerwerInstance(id, port);
          s.start();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }, "Server-" + i).start();
    }
  }
}
