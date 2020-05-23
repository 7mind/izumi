package org.scalatest.distage

import java.util.concurrent.atomic.AtomicBoolean

import distage.TagK
import io.github.classgraph.ClassGraph
import izumi.distage.testkit.DebugProperties
import izumi.distage.testkit.services.dstest.DistageTestRunner._
import izumi.distage.testkit.services.dstest.{AbstractDistageSpec, DistageTestRunner}
import izumi.distage.testkit.services.scalatest.dstest.{DistageTestsRegistrySingleton, SafeTestReporter}
import izumi.distage.testkit.services.scalatest.dstest.DistageTestsRegistrySingleton.SuiteReporter
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.functional.Identity
import org.scalatest._
import org.scalatest.exceptions.TestCanceledException

import scala.collection.immutable.TreeSet
import scala.util.Try

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

    import scala.jdk.CollectionConverters._

    def awaitTestsLoaded(): Unit = {
      latch.await()
    }

    def doScan[F[_]](instance: DistageScalatestTestSuiteRunner[F]): Unit = {
      if (classpathScanned.compareAndSet(false, true)) {
        val classLoader = instance.getClass.getClassLoader
        val scan = new ClassGraph()
          .enableClassInfo()
          .addClassLoader(classLoader)
          .scan()
        try {
          val suiteClassName = classOf[DistageScalatestTestSuiteRunner[Identity]].getName
          val testClasses = scan.getSubclasses(suiteClassName).asScala.filterNot(_.isAbstract)
          lazy val debugLogger = TrivialLogger.make[ScalatestInitWorkaroundImpl.type](DebugProperties.`izumi.distage.testkit.debug`)
          testClasses.foreach(
            classInfo =>
              Try {
                debugLogger.log(s"Added scanned class `${classInfo.getName}` to current test run")
                classInfo.loadClass().getDeclaredConstructor().newInstance()
              }
          )
          DistageTestsRegistrySingleton.disableRegistration()
          latch.countDown()
        } finally {
          scan.close()
        }
      }
    }
  }

}

abstract class DistageScalatestTestSuiteRunner[F[_]](implicit override val tagMonoIO: TagK[F]) extends TestSuite with AbstractDistageSpec[F] {

  // initialize status early, so that runner can set it to `true` even before this test is discovered
  // by scalatest, if it was already executed by that time
  private[this] val status: StatefulStatus = DistageTestsRegistrySingleton.registerStatus[F](suiteId)

  override protected final def runNestedSuites(args: Args): Status = throw new UnsupportedOperationException
  override protected final def runTests(testName: Option[String], args: Args): Status = throw new UnsupportedOperationException
  override protected final def runTest(testName: String, args: Args): Status = throw new UnsupportedOperationException
  override protected def withFixture(test: NoArgTest): Outcome = throw new UnsupportedOperationException

  override def run(testName: Option[String], args: Args): Status = {
    DistageTestsRegistrySingleton.registerSuiteReporter(suiteId)(SuiteReporter(args.tracker, args.reporter))
    // If, we're running under sbt, scan the classpath manually to add all tests
    // in the classloader before starting anything, because sbt runner
    // instantiates & runs tests at the same time, so when `run` is called
    // NOT all tests have been registered, so we must force all tests, otherwise
    // we can't be sure.
    //
    // NON-sbt ScalatestRunner first instantiates ALL tests, THEN calls `.run` method,
    // so for non-sbt runs we KNOW that all tests have already been registered, so we
    // don't have to scan the classpath ourselves.
    if (args.reporter.getClass.getName.contains("org.scalatest.tools.Framework")) {
      ScalatestInitWorkaround.scan(this).awaitTestsLoaded()
    }

    try {
      DistageTestsRegistrySingleton.proceedWithTests[F]() match {
        case Some(value) =>
          _doRun(value, testName, args)
          if (!status.isCompleted) {
            status.setCompleted()
          }
        case None =>
      }
    } catch {
      case t: Throwable =>
        if (!status.isCompleted) {
          status.setFailedWith(t)
          status.setCompleted()
        }
    }
    status
  }

  override def testNames: Set[String] = {
    val testsInThisTestClass = DistageTestsRegistrySingleton.registeredTests[F].filter(_.meta.id.suiteId == suiteId)
    TreeSet[String](testsInThisTestClass.map(_.meta.id.name): _*)
  }

  override def tags: Map[String, Set[String]] = Map.empty

  override def expectedTestCount(filter: Filter): Int = {
    if (filter.tagsToInclude.isDefined) {
      0
    } else {
      testNames.size - tags.size
    }
  }

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

  protected[this] def _doRun(testsInThisRuntime: Seq[DistageTest[F]], testName: Option[String], args: Args): Unit = {
    val debugLogger: TrivialLogger = TrivialLogger.make[DistageScalatestTestSuiteRunner[F]](DebugProperties.`izumi.distage.testkit.debug`)
    debugLogger.log(s"Scalatest Args: $args")
    debugLogger.log(s"""tagsToInclude: ${args.filter.tagsToInclude}
                       |tagsToExclude: ${args.filter.tagsToExclude}
                       |dynaTags: ${args.filter.dynaTags}
                       |excludeNestedSuites: ${args.filter.excludeNestedSuites}
                       |""".stripMargin)
    val testReporter = _mkTestReporter()

    val toRun = testName match {
      case None =>
        testsInThisRuntime.filter {
          test =>
            val tags: Map[String, Set[String]] = Map.empty
            val (filterTest, ignoreTest) = args.filter.apply(test.meta.id.name, tags, test.meta.id.suiteId)
            val isTestOk = !filterTest && !ignoreTest
            isTestOk
        }

      case Some(testName) =>
        if (!testNames.contains(testName)) {
          throw new IllegalArgumentException(Resources.testNotFound(testName))
        } else {
          testsInThisRuntime.filter(_.meta.id.name == testName)
        }
    }

    try {
      if (toRun.nonEmpty) {
        debugLogger.log(s"GOING TO RUN TESTS in ${tagMonoIO.tag}: ${toRun.map(_.meta.id.name)}")
        val runner = {
          new DistageTestRunner[F](testReporter, toRun, _.isInstanceOf[TestCanceledException])
        }
        runner.run()
      }
    } finally {
      DistageTestsRegistrySingleton.completeStatuses[F]()
    }
  }

  protected[this] def _mkTestReporter(): TestReporter = {
    val scalatestReporter = new DistageScalatestReporter
    new SafeTestReporter(scalatestReporter)
  }

  override final val styleName: String = "DistageSuite"
}
