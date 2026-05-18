package org.scalatest.distage

import izumi.distage.modules.DefaultModule
import izumi.distage.testkit.DebugProperties
import izumi.distage.testkit.model.DistageTest
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.services.scalatest.dstest.DistageTestsRegistry.RunningSuiteHandle
import izumi.distage.testkit.services.scalatest.dstest.TestRunnerRuntime.AsyncGlobalSuitesControlHandle
import izumi.distage.testkit.services.scalatest.dstest.{DistageTestsRegistry, DistageTestsRegistrySingleton, ScalatestLinearizedTestReporter, TestRunnerRuntime}
import izumi.distage.testkit.spec.AbstractDistageSpec
import izumi.fundamentals.platform.IzPlatform
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.strings.IzString.toRichIterable
import izumi.reflect.TagK
import org.scalatest.distage.__AnnotationPlatformSpecific.EnableReflectiveInstantiation
import org.scalatest.exceptions.{DuplicateTestNameException, TestCanceledException}
import org.scalatest.{Args, ConfigMap, Outcome, StatefulStatus, Status, TagAnnotation, TestData, TestSuite}

@EnableReflectiveInstantiation
abstract class DistageScalatestTestSuiteRunner[F[_]](
  implicit override val tagMonoIO: TagK[F],
  override val defaultModulesIO: DefaultModule[F],
) extends TestSuite
  with AbstractDistageSpec[F] {

  override protected final def runNestedSuites(args: Args): Status = throw new UnsupportedOperationException
  override protected final def runTests(testName: Option[String], args: Args): Status = throw new UnsupportedOperationException
  override protected final def runTest(testName: String, args: Args): Status = throw new UnsupportedOperationException
  override protected def withFixture(test: NoArgTest): Outcome = throw new UnsupportedOperationException

  /**
    * Override to force enable global memoization on Scala.js.
    * It will only work correctly if parallel execution is disabled, e.g. via `Test / parallelExecution := false` key in SBT.
    * Because of that and because there are limited use cases for global memoization on JS, it is disabled by default.
    */
  protected def scalaJsForceGlobalMemoization: Boolean = DebugProperties.`izumi.distage.testkit.js.force.global.memoization`.boolValue(false)

  /**
    * Override to customize the effect type that the outermost test launcher runs on.
    * Testkit can run on any async effect type, such as ZIO and cats-effect IO,
    * although by default it runs [[izumi.functional.bio.impl.MiniBIOAsync MiniBIOAsync]]
    *
    * @note Overriding default top level test runtime is NOT recommended and will NOT speed up tests.
    *       This extension point is provided mostly just because we can.
    *
    * @example
    * {{{
    *   override def testRunnerRuntime() = TestRunnerRuntime.defaultAsyncRuntimeFor[zio.Task]
    * }}}
    *
    * @see [[TestRunnerRuntime]]
    * @see [[TestRunnerRuntime.defaultAsyncRuntimeFor]]
    */
  protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultPlatformRuntime

  // create status early, so that runner can set it to `true` even before this test's
  // `run` method is called by scalatest, because all the suite's tests could have
  // already been executed by another suite before this `run` was called
  private val singletonStatus: StatefulStatus = _distageTestsRegistry.registerInstantiatedSuite[F](suiteId, this)

  override def run(testName: Option[String], args: Args): Status = {
    val status = singletonStatus

    _distageTestsRegistry.registerSuiteHandle(suiteId)(RunningSuiteHandle(args.tracker, args.reporter))

    // Note: because https://github.com/scalatest/scalatest/pull/2410 has not been merged,
    // we're forced to keep a separate registration mechanism for non-sbt org.scalatest.tools.Runner (used by e.g. Intellij)
    //
    // NON-sbt ScalatestRunner first instantiates ALL tests, THEN calls `.run` method,
    // so for non-sbt runs we KNOW that all tests have already been registered already
    val isSbt = args.reporter.getClass.getName.contains("org.scalatest.tools.Framework")

    val isJVM = !IzPlatform.isScalaJS
    val globalMode = isJVM || scalaJsForceGlobalMemoization

    try {
      val testsToRun = if (globalMode) {
        _distageTestsRegistry.collectAllTestkitTests(this, isSbt)
      } else {
        Some(registeredTests())
      }

      testsToRun match {
        case Some(tests) =>
          _doPrepareRunTests(tests, testName, args, status, globalMode)
        case None =>
        // In global memoization mode: Not the first runner - status will be completed by the actual runner
        // In per-instance mode: This shouldn't happen
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

  protected def _doPrepareRunTests[F0[_]](
    testsInThisRun: Seq[DistageTest[F0]],
    testName: Option[String],
    args: Args,
    status: StatefulStatus,
    globalMode: Boolean,
  ): Unit = {
    val debugLogger: TrivialLogger = TrivialLogger.make[DistageScalatestTestSuiteRunner[F]](DebugProperties.`izumi.distage.testkit.debug`.name)

    debugLogger.log(
      s"""Scalatest
         |  Args: $args
         |  tagsToInclude: ${args.filter.tagsToInclude}
         |  tagsToExclude: ${args.filter.tagsToExclude}
         |  dynaTags: ${args.filter.dynaTags}
         |  excludeNestedSuites: ${args.filter.excludeNestedSuites}""".stripMargin
    )

    val testsToRun = _applyScalatestDefaultFiltering(args, testsInThisRun, testName)

    debugLogger.log(s"GOING TO RUN TESTS in ${tagMonoIO.tag.repr} (from class ${getClass.getName}):${testsToRun.map(_.meta.test.id.toString).niceList()}")

    val asyncGlobalSuitesControl = new AsyncGlobalSuitesControlHandle {
      override def completeOuterSuite(mbFailure: Option[Throwable]): Unit = {
        status.synchronized {
          if (!status.isCompleted()) {
            mbFailure.foreach(status.setFailedWith)
            status.setCompleted()
          }
        }
      }
      override def completeAllSuitesIfGlobal(): Unit = {
        if (globalMode) {
          _distageTestsRegistry.completeAllStatuses()
        }
      }
    }

    val testReporter = _mkTestReporter()

    _doRunTests(debugLogger, asyncGlobalSuitesControl, testReporter, testsToRun)
  }

  protected def _doRunTests[F0[_]](
    debugLogger: TrivialLogger,
    asyncGlobalSuitesControl: AsyncGlobalSuitesControlHandle,
    testReporter: TestReporter,
    testsToRun: Seq[DistageTest[F0]],
  ): Unit = {

    val maybeSyncTestResults = {
      try {
        testRunnerRuntime().runTests(asyncGlobalSuitesControl, testReporter, _.isInstanceOf[TestCanceledException], testsToRun)
      } catch {
        case t: Throwable =>
          asyncGlobalSuitesControl.completeOuterSuite(Some(t))
          asyncGlobalSuitesControl.completeAllSuitesIfGlobal()
          throw t
      }
    }

    maybeSyncTestResults match {
      case Left(testResults) =>
        asyncGlobalSuitesControl.completeOuterSuite(None)
        asyncGlobalSuitesControl.completeAllSuitesIfGlobal()
        debugLogger.log(s"Got for ${tagMonoIO.tag}: testResults=${testResults.niceList()}")

      case Right(asyncResult) =>
        __DistageScalatestTestSuiteRunnerPlatformSpecific
          .handleAsyncTestRunnerPlatformSpecific(debugLogger, asyncGlobalSuitesControl, asyncResult, tagMonoIO)
    }
  }

  protected def _mkTestReporter(): TestReporter = {
    val suiteHandler = _distageTestsRegistry.mkSuiteHandlerById()
    val scalatestReporter = new DistageScalatestReporter(suiteHandler)
    // Wrap for BOTH the SBT and the Intellij paths. `ScalatestLinearizedTestReporter`
    // is required for downstream ScalaTest reporters that pair-walk per-suite events
    // (JUnitXmlReporter / XmlReporter / DashboardReporter — see the class scaladoc for
    // exact line numbers) and benefits the Intellij reporter as well. Without this
    // wrap, intra-suite parallelism (the testkit default,
    // `parallelTests = Parallelism.Unlimited`) produces silent JUnit XML undercount.
    new ScalatestLinearizedTestReporter(scalatestReporter)
  }

  /** Must return the same instance on every call. */
  protected def _distageTestsRegistry: DistageTestsRegistry = DistageTestsRegistrySingleton

  override def tags: Map[String, Set[String]] = {
    org.scalatest.Suite.autoTagClassAnnotations(Map.empty, this)
  }

  override def testNames: Set[String] = {
    val testsInThisSuite = registeredTests()

    testsInThisSuite.groupBy(_.meta.test.id.name).foreach {
      case (testName, tests) =>
        if (tests.size > 1) {
          throw new DuplicateTestNameException(testName, 0)
        }
    }

    org.scalatest.InsertionOrderSet(testsInThisSuite.map(_.meta.test.id.name))
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

  protected def _applyScalatestDefaultFiltering[F0[_]](args: Args, testsInThisRuntime: Seq[DistageTest[F0]], testName: Option[String]): Seq[DistageTest[F0]] = {
    testName match {
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
          throw new IllegalArgumentException(org.scalatest.Resources.testNotFound(testName))
        } else {
          testsInThisRuntime.filter(_.meta.test.id.name == testName)
        }
    }
  }

}
