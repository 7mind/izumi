package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.{BuildId, BuildTargetId, CatalogueId, CatalogueIdentity}

import java.nio.file.Paths
import java.util.concurrent.{Executors, TimeUnit}
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration

object StandaloneLauncher {
  private final val ShutdownSeconds = 30L
  private final val FirstSuiteArgument = 5

  def main(arguments: Array[String]): Unit = {
    require(arguments.length > FirstSuiteArgument, "Usage: StandaloneLauncher <build-id> <target-id> <catalogue-id> <commands.jsonl> <output.jsonl> <suite-class>...")
    require(arguments.forall(_.nonEmpty), "Launcher arguments must not be empty")
    val suites = arguments.drop(FirstSuiteArgument).toVector
    require(suites.distinct == suites, "Launcher suite catalogue contains duplicates")
    val identity = CatalogueIdentity(BuildId(arguments(0)), BuildTargetId(arguments(1)), CatalogueId(arguments(2)))
    val loader = getClass.getClassLoader
    val factories = suites.map(name => () => JvmSuiteLoader.load(name, loader))
    val source = FileProtocolFrameSource.open(Paths.get(arguments(3)))
    val result = try {
      val sink = FileProtocolFrameSink.createNew(Paths.get(arguments(4)))
      try {
        val executor = Executors.newWorkStealingPool()
        val context = ExecutionContext.fromExecutorService(executor)
        try Await.result(ApplicationLauncher.run(identity, factories, context, source, new FramedProtocolOutput(sink)), Duration.Inf)
        finally {
          context.shutdown()
          require(context.awaitTermination(ShutdownSeconds, TimeUnit.SECONDS), "Launcher execution context did not terminate")
        }
      } finally sink.close()
    } finally source.close()
    if (!result.successful) sys.exit(1)
  }
}
