package spike;
import sbt.testing.*;
import java.util.*;
public final class ApplicationFramework extends DiscoveryFramework {
  public Runner runner(final String[] args, final String[] remoteArgs, final ClassLoader loader) {
    return new Runner() {
      public String[] args() { return args; }
      public String[] remoteArgs() { return remoteArgs; }
      public String done() { return "target application framework done"; }
      public Task[] tasks(final TaskDef[] defs) {
        System.out.println("[spike] FORK_WHOLE_TASKS " + defs.length);
        class Invocation {
          private Map<String,List<String>> completed;
          synchronized Map<String,List<String>> results() throws Exception {
            if (completed == null) {
              String[] names = new String[defs.length];
              for (int i=0; i<defs.length; i++) names[i] = defs[i].fullyQualifiedName();
              Arrays.sort(names);
              completed = Application.run(names, args, loader, "FORK_WHOLE");
            }
            return completed;
          }
        }
        final Invocation invocation = new Invocation();
        Task[] tasks = new Task[defs.length];
        for(int i=0;i<defs.length;i++) {
          final TaskDef def=defs[i];
          tasks[i] = new Task() {
            public TaskDef taskDef() { return def; }
            public String[] tags() { return new String[0]; }
            public Task[] execute(EventHandler handler, Logger[] loggers) {
              try {
                System.out.println("[spike] FORK_TASK_ENTER " + def.fullyQualifiedName());
                for (String test: invocation.results().get(def.fullyQualifiedName())) handler.handle(event(def,test));
                System.out.println("[spike] FORK_TASK_EXIT " + def.fullyQualifiedName());
                return new Task[0];
              } catch(Exception e) { throw new RuntimeException(e); }
            }
          };
        }
        return tasks;
      }
    };
  }
}
