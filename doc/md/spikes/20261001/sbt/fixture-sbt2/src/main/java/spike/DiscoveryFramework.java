package spike;
import sbt.testing.*;
public class DiscoveryFramework implements Framework {
  protected String superclass() { return "spike.Spec"; }
  public String name() { return "discovery-only"; }
  public Fingerprint[] fingerprints() { return new Fingerprint[]{new SubclassFingerprint() {
    public boolean isModule() { return false; }
    public String superclassName() { return superclass(); }
    public boolean requireNoArgConstructor() { return true; }
  }}; }
  public Runner runner(final String[] args, final String[] remoteArgs, final ClassLoader loader) {
    return new Runner() {
      public String[] args() { return args; }
      public String[] remoteArgs() { return remoteArgs; }
      public String done() { return "target framework done"; }
      public Task[] tasks(TaskDef[] defs) {
        System.out.println("[spike] TARGET_TASKS " + defs.length);
        Task[] tasks = new Task[defs.length];
        for(int i=0;i<defs.length;i++) {
          final TaskDef def = defs[i];
          tasks[i] = new Task() {
            public TaskDef taskDef() { return def; }
            public String[] tags() { return new String[0]; }
            public Task[] execute(EventHandler handler, Logger[] loggers) {
              try {
                for(String test: Application.run(new String[]{def.fullyQualifiedName()}, args, loader, "TARGET").get(def.fullyQualifiedName())) handler.handle(event(def,test));
                return new Task[0];
              } catch(Exception e) { throw new RuntimeException(e); }
            }
          };
        }
        return tasks;
      }
    };
  }
  public static Event event(final TaskDef def, final String test) { return new Event() {
    public String fullyQualifiedName() { return def.fullyQualifiedName(); }
    public Fingerprint fingerprint() { return def.fingerprint(); }
    public Selector selector() { return new TestSelector(test); }
    public Status status() { return Status.Success; }
    public OptionalThrowable throwable() { return new OptionalThrowable(); }
    public long duration() { return 1L; }
  }; }
}
