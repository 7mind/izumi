package izumi.distage.testkit.services.scalatest.dstest

import izumi.distage.testkit.model.{DistageTest, SuiteId}
import izumi.fundamentals.collections.nonempty.NEList
import izumi.fundamentals.platform.language.Quirks.Discarder
import izumi.fundamentals.platform.language.types.HigherKindedAny.AnyF
import org.scalatest.distage.DistageScalatestTestSuiteRunner
import org.scalatest.events.{Event, Ordinal}
import org.scalatest.tools.Runner
import org.scalatest.{Reporter, StatefulStatus, Tracker}

import java.util.concurrent.atomic.AtomicBoolean
import scala.annotation.unchecked.uncheckedVariance
import scala.collection.mutable
import scala.util.chaining.scalaUtilChainingOps

object DistageTestsRegistrySingleton {
  final case class InstantiatedSuiteHandle[+F[_]](
    suite: DistageScalatestTestSuiteRunner[F @uncheckedVariance],
    status: StatefulStatus,
  )
  final case class RunningSuiteHandle(
    tracker: Tracker,
    reporter: Reporter,
    status: StatefulStatus,
  )

  private val instantiatedSuiteHandles = new mutable.HashMap[String, InstantiatedSuiteHandle[AnyF]]()
  private val runningSuiteHandles = new mutable.HashMap[String, Either[mutable.ArrayBuffer[RunningSuiteHandle => Unit], RunningSuiteHandle]]()
  private val firstRunnerStarted = new AtomicBoolean(false)
  private val runnerFinished = new AtomicBoolean(false)

  def collectAllTestkitTests[F[_]](instance: DistageScalatestTestSuiteRunner[F], isSbt: Boolean): Option[NEList[DistageTest[AnyF]]] = {
    if (DistageTestsRegistrySingleton.permittedToRun()) {
      println(s"Launching tests in from $instance")

      val instantiatedClassNames = DistageTestsRegistrySingleton.currentInstantiatedSuites().map(_.suite.getClass.getName)
      val discoveredClassNames: Set[String] = Runner.discoveredSuites.getOrElse {
        if (isSbt) {
          throw new RuntimeException(
            s"""Impossible: distage-testkit-scalatest attempted initialization before ScalaTest completed classpath discovery! in=$instance
               |
               |Please report this as a bug to https://github.com/7mind/izumi/issues""".stripMargin
          )
        } else {
          Set.empty[String]
        }
      }
      val suiteClass = classOf[DistageScalatestTestSuiteRunner[F]]

      (discoveredClassNames -- instantiatedClassNames).foreach {
        clsName =>
          val clazz = __ClassReflectionPlatformSpecific.clazzForName(clsName)
          if (__ClassReflectionPlatformSpecific.subclassOf(clazz, suiteClass)) {
            // instantiate tests to make them register themselves
            __ClassReflectionPlatformSpecific.newInstance(clazz)
          }
      }

      val allSuites = DistageTestsRegistrySingleton.currentInstantiatedSuites().map(_.suite)

      println(s"XXX INSTANTIATED NEW SUITES = ${allSuites.map(_.getClass.getName).toSet -- instantiatedClassNames}")

      import izumi.fundamentals.platform.strings.IzString.toRichIterable
      println(s"found Suites (in $instance): ${allSuites.niceList()}")

      // Gather tests from all suite instances for single-runner execution
      // All DistageScalatestTestSuiteRunner instances extend WithSingletonTestRegistration
      val allTests = allSuites.flatMap(_.registeredTests())

      println(s"Gathered ${allTests.size} tests from ${allSuites.size} suites (global memoization mode)")

      NEList.from(allTests)
    } else {
      None
    }
  }

  def permittedToRun(): Boolean = {
    firstRunnerStarted.compareAndSet(false, true)
  }

  def resetRegistry(): Unit = synchronized {
    instantiatedSuiteHandles.clear()
    runningSuiteHandles.clear()
    firstRunnerStarted.set(false)
    runnerFinished.set(false)
    ()
  }

  def registerInstantiatedSuite[F[_]](suiteId: String, instance: DistageScalatestTestSuiteRunner[F]): StatefulStatus = synchronized {
    if (runnerFinished.get()) {
      // return completed status if the runner has already finished all tests before this test was instantiated
      (new StatefulStatus).tap(_.setCompleted())
    } else {
      instantiatedSuiteHandles
        .getOrElseUpdate(
          suiteId, {
            println(s"NEWSTATUS for $suiteId")
            InstantiatedSuiteHandle(instance, new StatefulStatus)
          },
        ).status
    }
  }

  def currentInstantiatedSuites(): List[InstantiatedSuiteHandle[AnyF]] = synchronized {
    instantiatedSuiteHandles.valuesIterator.toList
  }

  def completeStatuses(): Unit = synchronized {
    instantiatedSuiteHandles.foreach {
      case (suiteName, suiteHandle) =>
        if (!suiteHandle.status.isCompleted()) {
          println(s"DISASTER? $suiteName didn't complete on its own!")
          suiteHandle.status.setCompleted()
        }
    }
    runnerFinished.set(true)
  }

  def registerSuiteHandle(suiteId: String)(suiteReporter: RunningSuiteHandle): Unit = synchronized {
    runningSuiteHandles.getOrElseUpdate(suiteId, Right(suiteReporter)) match {
      case Left(reports) =>
        runningSuiteHandles(suiteId) = Right(suiteReporter)
        println(s"REGISTERED_SUITEHANDLE for $suiteId")
        reports.foreach(_.apply(suiteReporter))
      case Right(_) =>
    }
  }

  def runReport(suiteId: String)(f: RunningSuiteHandle => Unit): Unit = synchronized {
    runningSuiteHandles.getOrElseUpdate(suiteId, Left(mutable.ArrayBuffer.empty)) match {
      case Left(reports) =>
        println(s"!!! suitehandle not found for $suiteId report delayed")
        (reports += f).discard()
      case Right(suiteReporter) =>
        println(s"!!! found suitehandle for $suiteId")
        f(suiteReporter)
    }
  }

  def mkSuiteHandlerById(): SuiteHandlerById = new SuiteHandlerById {
    override def doReportEvent(suiteId: SuiteId)(f: Ordinal => Event): Unit = {
      runReport(suiteId.suiteId)(s => s.reporter(f(s.tracker.nextOrdinal())))
    }
    override def doSetStatus(suiteId: SuiteId)(f: StatefulStatus => Unit): Unit = {
      runReport(suiteId.suiteId)(s => f(s.status))
    }
  }
}
