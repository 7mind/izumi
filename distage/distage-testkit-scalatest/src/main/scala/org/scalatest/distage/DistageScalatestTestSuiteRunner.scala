package org.scalatest.distage

import distage.TagK
import io.github.classgraph.ClassGraph
import izumi.distage.modules.DefaultModule
import izumi.distage.testkit.DebugProperties
import izumi.distage.testkit.model.{DistageTest, SuiteId}
import izumi.distage.testkit.runner.{DistageTestRunner, IndividualTestRunner, TestPlanner}
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.services.{ReporterBracket, TestConfigLoader, TestkitLogging}
import izumi.distage.testkit.services.scalatest.dstest.DistageTestsRegistrySingleton.SuiteReporter
import izumi.distage.testkit.services.scalatest.dstest.{DistageTestsRegistrySingleton, SafeTestReporter}
import izumi.distage.testkit.spec.AbstractDistageSpec
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.functional.Identity
import org.scalatest.*
import org.scalatest.exceptions.TestCanceledException

import java.util.concurrent.atomic.AtomicBoolean
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

    import scala.jdk.CollectionConverters.*

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
          lazy val debugLogger = TrivialLogger.make[ScalatestInitWorkaroundImpl.type](DebugProperties.`izumi.distage.testkit.debug`.name)
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

abstract class DistageScalatestTestSuiteRunner[F[_]](
  implicit override val tagMonoIO: TagK[F],
  override val defaultModulesIO: DefaultModule[F],
) extends TestSuite
  with AbstractDistageSpec[F] {

  /** Modify test discovery options for SBT test runner only.
    * Overriding this with [[withWhitelistJarsOnly]] will slightly boost test start-up speed,
    * but will disable the ability to discover tests that inherit [[izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec]]
    * indirectly through a different library JAR. (this does not affect local sbt modules)
    */
  protected def modifyClasspathScan: ClassGraph => ClassGraph = identity
  protected final def withWhitelistJarsOnly: ClassGraph => ClassGraph = _.acceptJars("distage-testkit-scalatest*")

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
    TreeSet[String](testsInThisTestClass.map(_.meta.test.id.name): _*)
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
        val logging = new TestkitLogging()
        val reporterBracket = new ReporterBracket[F](_.isInstanceOf[TestCanceledException])
        val individualTestRunner = new IndividualTestRunner.IndividualTestRunnerImpl[F](
          testReporter,
          logging,
          reporterBracket,
        )
        val runner = new DistageTestRunner[F](
          testReporter,
          logging,
          new TestPlanner[F](logging, new TestConfigLoader.TestConfigLoaderImpl()),
          individualTestRunner,
          reporterBracket,
        )
        runner.run(toRun)
      }
    } finally {
      DistageTestsRegistrySingleton.completeStatuses[F]()
    }
  }

  private[distage] def mkTestReporter(): TestReporter = {
    val scalatestReporter = new DistageScalatestReporter
    new SafeTestReporter(scalatestReporter)
  }

}
