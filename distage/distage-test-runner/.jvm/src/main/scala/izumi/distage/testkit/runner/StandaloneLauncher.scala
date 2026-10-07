package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.{BuildId, BuildTargetId, CatalogueId, CatalogueIdentity}

import java.nio.file.Paths
import java.util.concurrent.{Executors, TimeUnit}
import scala.collection.mutable.ListBuffer
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutorService, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Try}

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
        val operation = try {
          val active = ApplicationLauncher.start(identity, factories, context, source, new FramedProtocolOutput(sink))
          // Join shutdown outside the executor being released.
          active.copy(completion = active.completion.transform(result => releaseContext(context, result))(ExecutionContext.global))
        } catch { case cause: Throwable => throw releaseContext(context, Failure(cause)).failed.get }
        awaitCompletion(operation.completion, operation.cancel)
      } finally sink.close()
    } finally source.close()
    if (!result.successful) sys.exit(1)
  }

  private[runner] def releaseContext[A](context: ExecutionContextExecutorService, result: Try[A]): Try[A] = {
    try {
      context.shutdown()
      require(context.awaitTermination(ShutdownSeconds, TimeUnit.SECONDS), "Launcher execution context did not terminate")
      result
    } catch {
      case cause: Throwable => result.failed.toOption match {
        case None => Failure(cause)
        case Some(original) =>
          val combined = new RuntimeException("Application execution and launcher shutdown failed", original)
          combined.addSuppressed(cause)
          Failure(combined)
      }
    }
  }

  private[runner] def awaitCompletion[A](completion: Future[A], cancel: () => Unit): A = {
    val interruptions = ListBuffer.empty[Throwable]
    var result = Option.empty[Try[A]]
    try {
      while (result.isEmpty) {
        try {
          val _ = Await.ready(completion, Duration.Inf)
          result = completion.value
        } catch {
          case cause: InterruptedException =>
            interruptions += cause
            try cancel() catch { case failure: Throwable => interruptions += failure }
        }
      }
      val settled = result.get
      (settled.failed.toOption.toList ++ interruptions.toList) match {
        case Nil => settled.get
        case cause :: Nil => throw cause
        case primary :: additional =>
          val combined = new RuntimeException("Multiple failures while awaiting application completion", primary)
          additional.foreach(combined.addSuppressed)
          throw combined
      }
    } finally {
      if (interruptions.nonEmpty) Thread.currentThread().interrupt()
    }
  }
}
