package izumi.distage.testkit.runner.bootstrap

import izumi.distage.testkit.protocol.FailurePhase
import izumi.distage.testkit.runner.{RegisteredSuite, RegistrationContext, TestSuite}
import izumi.distage.testkit.runner.spec.AnyWordSpec

import sbt.testing.{Event, EventHandler, Status, SuiteSelector, TaskDef}

import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.jdk.CollectionConverters.*

object RegistrationLinkageFixtures {
  private final val TimeoutSeconds = 15L
  private final val TimeoutExit = 23

  def main(arguments: Array[String]): Unit = {
    require(arguments.isEmpty, "Registration linkage fixture takes no arguments")
    val completed = new CountDownLatch(1)
    val watchdog = new Thread(new Runnable {
      override def run(): Unit = {
        if (!completed.await(TimeoutSeconds, TimeUnit.SECONDS)) {
          System.err.println("JVM_REGISTRATION_LINKAGE_TIMEOUT application=unresolved")
          Thread.getAllStackTraces.asScala.toVector.sortBy(_._1.getName).foreach { case (thread, stack) =>
            System.err.println(thread.getName + " " + thread.getState + "\n" + stack.mkString("\n"))
          }
          System.exit(TimeoutExit)
        }
      }
    }, "registration-linkage-watchdog")
    watchdog.setDaemon(true)
    watchdog.start()
    try {
      val previousThreads = Thread.getAllStackTraces.keySet().asScala.toSet
      val framework = new Framework
      val flags = Array("--build-id", "registration-linkage", "--target-id", "jvm", "--catalogue-id", "two-suites")
      val runner = framework.runner(flags, Array.empty, getClass.getClassLoader)
      def definition(name: String): TaskDef = new TaskDef(name, framework.fingerprints().head, false, Array(new SuiteSelector))
      val healthy = definition(classOf[RegistrationHealthySuite].getName)
      val selected = Array(definition(classOf[RegistrationLinkageSuite].getName), healthy)
      def collect(definitions: Array[TaskDef]): Vector[Vector[Event]] = runner.tasks(definitions).toVector.map { task =>
        var events = Vector.empty[Event]
        val handler = new EventHandler {
          override def handle(event: Event): Unit = { events :+= event }
        }
        require(task.execute(handler, Array.empty).isEmpty, "Registration fixture returned nested tasks")
        require(events.forall(_.fullyQualifiedName() == task.taskDef().fullyQualifiedName()), "Registration failure changed suite ownership")
        events
      }
      def verifyFailure(): Unit = {
        val reported = collect(selected)
        require(reported.size == selected.length && reported.forall(_.size == 1), "Registration failure must report every selected suite once")
        reported.flatten.foreach { event =>
          require(event.status() == Status.Error && event.selector().isInstanceOf[SuiteSelector] && event.throwable().isDefined, "Registration failure must remain a suite error")
          val failure = event.throwable().get()
          require(failure.getMessage.startsWith(FailurePhase.Discovery.toString + ":") && Option(failure.getCause).exists(cause => cause.getMessage.contains(classOf[NoClassDefFoundError].getName) && cause.getMessage.contains("fixture-registration-missing-dependency")), "Registration failure lost its discovery phase or linkage cause")
        }
        println("JVM_REGISTRATION_LINKAGE_GROUP_OK suites=2 errors=2 bodies=0 phase=discovery cause=retained")
      }
      verifyFailure()
      val recovery = collect(Array(healthy))
      require(recovery.size == 1 && recovery.head.size == 1 && recovery.head.head.status() == Status.Success, "Healthy group after registration failure did not recover")
      verifyFailure()
      require(runner.done().isEmpty, "Registration linkage runner did not finish")
      require(!Thread.getAllStackTraces.keySet().asScala.filterNot(previousThreads.contains).exists(thread => thread.isAlive && thread.getName.startsWith("ForkJoinPool-")), "Registration failure retained an application executor")
      println("JVM_REGISTRATION_LINKAGE_FIXTURES_OK groups=3 errors=4 recoveryBodies=1 executors=terminated")
    } finally {
      completed.countDown()
      watchdog.join(TimeUnit.SECONDS.toMillis(TimeoutSeconds))
      require(!watchdog.isAlive, "Registration fixture watchdog did not terminate")
    }
  }
}

final class RegistrationLinkageSuite extends TestSuite {
  override def register(context: RegistrationContext): RegisteredSuite = {
    val _ = context
    println("JVM_REGISTRATION_LINKAGE_ENTERED")
    throw new NoClassDefFoundError("fixture-registration-missing-dependency")
  }
}

final class RegistrationHealthySuite extends AnyWordSpec {
  "registration recovery" should {
    "execute once" in { println("JVM_REGISTRATION_RECOVERY_BODY") }
  }
}
