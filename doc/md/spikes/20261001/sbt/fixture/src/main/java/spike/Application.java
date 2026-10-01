package spike;
import java.nio.file.*;
import java.util.*;
public final class Application {
  public static synchronized void audit(String value) {
    try { Files.createDirectories(Paths.get("target")); Files.write(Paths.get("target/audit.log"), (value + "\n").getBytes("UTF-8"), StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
    catch (Exception e) { throw new RuntimeException(e); }
    System.out.println("[spike] " + value);
  }
  public static Map<String,List<String>> run(String[] names, String[] args, ClassLoader loader, String seam) throws Exception {
    audit("APP " + seam + " names=" + String.join(",", names) + " args=" + String.join(",", args));
    audit("ACQUIRE " + seam);
    Map<String,List<String>> out = new LinkedHashMap<>();
    try {
      audit("DI " + new String(Files.readAllBytes(Paths.get("di-only.txt")), "UTF-8").trim());
      audit("WIRING " + Class.forName("spike.WiringOnly", true, loader).getMethod("value").invoke(null));
      boolean partial = Arrays.asList(args).contains("--one");
      for (String name : names) {
        Object suite = Class.forName(name, true, loader).getDeclaredConstructor().newInstance();
        List<String> tests = new ArrayList<>();
        for (int i=1; i <= (partial ? 1 : 3); i++) {
          suite.getClass().getMethod("body", int.class).invoke(suite, i);
          tests.add("test" + i);
        }
        out.put(name, tests);
      }
      return out;
    } finally { audit("RELEASE " + seam); }
  }
}
