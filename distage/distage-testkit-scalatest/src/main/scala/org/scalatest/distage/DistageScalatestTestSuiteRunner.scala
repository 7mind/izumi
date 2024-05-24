package org.scalatest.distage

import _root_.distage.TagK
import izumi.distage.modules.DefaultModule
import izumi.distage.testkit.DebugProperties
import izumi.distage.testkit.model.{DistageTest, SuiteId}
import izumi.distage.testkit.runner.TestkitRunnerModule
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.services.scalatest.dstest.DistageTestsRegistrySingleton.SuiteReporter
import izumi.distage.testkit.services.scalatest.dstest.{DistageTestsRegistrySingleton, SafeTestReporter}
import izumi.distage.testkit.spec.AbstractDistageSpec
import izumi.fundamentals.platform.console.TrivialLogger
import org.scalatest.*
import org.scalatest.exceptions.{DuplicateTestNameException, TestCanceledException}
import org.scalatest.tools.Runner

import java.util.concurrent.atomic.AtomicBoolean

trait ScalatestInitWorkaround {
  def awaitTestsLoaded(): Unit
}

object ScalatestInitWorkaround {
  def scan[F[_]](runner: DistageScalatestTestSuiteRunner[F]): ScalatestInitWorkaround = {
    ScalatestInitWorkaroundImpl.doScan(runner)

    new ScalatestInitWorkaround {
      override def awaitTestsLoaded(): Unit = ScalatestInitWorkaroundImpl.awaitTestsLoaded()
    }
  }

  object ScalatestInitWorkaroundImpl {
    private val classpathScanned = new AtomicBoolean(false)
    private val latch = new java.util.concurrent.CountDownLatch(1)

    def awaitTestsLoaded(): Unit = {
      latch.await()
    }

    def doScan[F[_]](instance: DistageScalatestTestSuiteRunner[F]): Unit = {
      if (classpathScanned.compareAndSet(false, true)) {
        val classNames = Runner.discoveredSuites.getOrElse(Set.empty)
        val curName = instance.getClass.getName
        if (classNames.nonEmpty && classNames != Set(curName)) {
          classNames.foreach {
            Class.forName(_).getDeclaredConstructor().newInstance()
          }
        }

        DistageTestsRegistrySingleton.disableRegistration()
        latch.countDown()
      }
    }
  }

}

abstract class DistageScalatestTestSuiteRunner[F[_]](
  implicit override val tagMonoIO: TagK[F],
  override val defaultModulesIO: DefaultModule[F],
) extends TestSuite
  with AbstractDistageSpec[F] {

  // initialize status early, so that runner can set it to `true` even before this test is discovered
  // by scalatest, if it was already executed by that time
  private[this] val status: StatefulStatus = DistageTestsRegistrySingleton.registerStatus[F](suiteId)

  override protected final def runNestedSuites(args: Args): Status = throw new UnsupportedOperationException
  override protected final def runTests(testName: Option[String], args: Args): Status = throw new UnsupportedOperationException
  override protected final def runTest(testName: String, args: Args): Status = throw new UnsupportedOperationException
  override protected def withFixture(test: NoArgTest): Outcome = throw new UnsupportedOperationException

  override def run(testName: Option[String], args: Args): Status = {
    DistageTestsRegistrySingleton.registerSuiteReporter(suiteId)(SuiteReporter(args.tracker, args.reporter))

    ScalatestInitWorkaround.scan(this).awaitTestsLoaded()

    try {
      DistageTestsRegistrySingleton.proceedWithTests[F]() match {
        case Some(value) =>
          doRun(value, testName, args)
          if (!status.isCompleted()) {
            status.setCompleted()
          }
        case None =>
      }
    } catch {
      case t: Throwable =>
        if (!status.isCompleted()) {
          status.setFailedWith(t)
          status.setCompleted()
        }
    }
    status
  }

  override def testNames: Set[String] = {
    val testsInThisTestClass = DistageTestsRegistrySingleton.registeredTests[F].filter(_.meta.test.id.suite == SuiteId(suiteId))
    val testsByName = testsInThisTestClass.groupBy(_.meta.test.id.name)
    testsByName.foreach {
      case (testName, tests) =>
        if (tests.size > 1) {
          throw new DuplicateTestNameException(testName, 0)
        }
    }
    testsByName.keys.toSet
  }

  override def tags: Map[String, Set[String]] = Map.empty

//  override def expectedTestCount(filter: Filter): Int = {
//    if (filter.tagsToInclude.isDefined) {
//      0
//    } else {
//      testNames.size - tags.size
//    }
//  }

  override def testDataFor(testName: String, theConfigMap: ConfigMap): TestData = {
    val suiteTags = for {
      a <- this.getClass.getAnnotations
      annotationClass = a.annotationType
      if annotationClass.isAnnotationPresent(classOf[TagAnnotation])
    } yield {
      annotationClass.getName
    }

    val testTags: Set[String] = Set.empty

    new TestData {
      override val configMap: ConfigMap = theConfigMap
      override val name: String = testName
      override val scopes: Vector[Nothing] = Vector.empty
      override val text: String = testName
      override val tags: Set[String] = Set.empty ++ suiteTags ++ testTags
      override val pos: None.type = None
    }
  }

  private[distage] def doRun(testsInThisRuntime: Seq[DistageTest[F]], testName: Option[String], args: Args): Unit = {
    val debugLogger: TrivialLogger = TrivialLogger.make[DistageScalatestTestSuiteRunner[F]](DebugProperties.`izumi.distage.testkit.debug`.name)
    debugLogger.log(s"Scalatest Args: $args")
    debugLogger.log(s"""tagsToInclude: ${args.filter.tagsToInclude}
                       |tagsToExclude: ${args.filter.tagsToExclude}
                       |dynaTags: ${args.filter.dynaTags}
                       |excludeNestedSuites: ${args.filter.excludeNestedSuites}
                       |""".stripMargin)
    val testReporter = mkTestReporter()

    val toRun = testName match {
      case None =>
        testsInThisRuntime.filter {
          test =>
            val tags: Map[String, Set[String]] = Map.empty
            val (filterTest, ignoreTest) = args.filter.apply(test.meta.test.id.name, tags, test.meta.test.id.suite.suiteId)
            val isTestOk = !filterTest && !ignoreTest
            isTestOk
        }

      case Some(testName) =>
        if (!testNames.contains(testName)) {
          throw new IllegalArgumentException(Resources.testNotFound(testName))
        } else {
          testsInThisRuntime.filter(_.meta.test.id.name == testName)
        }
    }

    try {
      if (toRun.nonEmpty) {
        debugLogger.log(s"GOING TO RUN TESTS in ${tagMonoIO.tag}: ${toRun.map(_.meta.test.id.name)}")
        TestkitRunnerModule.run[F](testReporter, (t: Throwable) => t.isInstanceOf[TestCanceledException], toRun)
        ()
      }
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        throw t
    } finally {
      DistageTestsRegistrySingleton.completeStatuses[F]()
    }
  }

  private[distage] def mkTestReporter(): TestReporter = {
    val scalatestReporter = new DistageScalatestReporter
    new SafeTestReporter(scalatestReporter)
  }

}
