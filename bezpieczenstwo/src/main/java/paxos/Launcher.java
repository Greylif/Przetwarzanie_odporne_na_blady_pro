package paxos;

public class Launcher {
  public static void main(String[] args) throws Exception {
    int numServers = 8;
    int basePort = 8000;

    System.out.println("Uruchamianie " + numServers + " serwerów Paxos...");

    for (int i = 0; i < numServers; i++) {
      final int id = i;
      final int port = basePort + i;
      new Thread(() -> {
        try {
          SerwerInstance s = new SerwerInstance(id, port);
          s.start();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }, "Server-" + i).start();
    }

    System.out.println("Wszystkie serwery uruchomione.");
    System.out.println("Teraz mozesz uruchomic lidera osobno komenda:");
    System.out.println("java paxos.LiderApp 0");
    System.out.println("lub automatycznie za 2 sekundy...");

    Thread.sleep(2000);

    new Thread(() -> {
      try {
        LiderApp.main(new String[]{"0"});
      } catch (Exception e) {
        e.printStackTrace();
      }
    }, "Leader").start();
  }
}
