package org.scalatest.distage

import izumi.distage.modules.DefaultModule
import izumi.distage.testkit.DebugProperties
import izumi.distage.testkit.model.DistageTest
import izumi.distage.testkit.runner.TestkitRunnerModule
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.services.scalatest.dstest.DistageTestsRegistrySingleton.RunningSuiteHandle
import izumi.distage.testkit.services.scalatest.dstest.{DistageTestsRegistrySingleton, SafeIntellijTestReporter, ScalatestInitWorkaround}
import izumi.distage.testkit.spec.AbstractDistageSpec
import izumi.functional.bio.Exit
import izumi.functional.bio.impl.MiniBIOAsync
import izumi.fundamentals.collections.nonempty.NEList
import izumi.fundamentals.platform.IzPlatform
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.functional.Identity
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
  override protected final def withFixture(test: NoArgTest): Outcome = throw new UnsupportedOperationException

  /**
    * Override to enable global memoization on Scala.js.
    * It will only work correctly if parallel execution is disabled, e.g.
    * with `Test / parallelExecution := false` key in SBT.
    * Because of that and because there are limited use cases for global
    * memoization on JS, it is disabled by default.
    */
  protected def scalaJsForceGlobalMemoization: Boolean = DebugProperties.`izumi.distage.testkit.js.force.global.memoization`.boolValue(false)

  // create status early, so that runner can set it to `true` even before this test's
  // `run` method is called by scalatest, because all the suite's tests could have
  // already been executed by another suite before this `run` was called
  private val singletonStatus: StatefulStatus = DistageTestsRegistrySingleton.registerInstantiatedSuite[F](suiteId, this)

  override def run(testName: Option[String], args: Args): Status = {
    val status = singletonStatus

    DistageTestsRegistrySingleton.registerSuiteHandle(suiteId)(RunningSuiteHandle(args.tracker, args.reporter, status))

    // Note: because https://github.com/scalatest/scalatest/pull/2410 has not been merged,
    // we're forced to keep a separate registration mechanism for non-sbt runners (e.g. Intellij)
    //
    // NON-sbt ScalatestRunner first instantiates ALL tests, THEN calls `.run` method,
    // so for non-sbt runs we KNOW that all tests have already been registered
    val isSbt = args.reporter.getClass.getName.contains("org.scalatest.tools.Framework")

    val sjsDisableGlobalMemoization = IzPlatform.isScalaJS && !scalaJsForceGlobalMemoization

    try {
      val testsToRun = if (!sjsDisableGlobalMemoization) {
        ScalatestInitWorkaround.collectAllTestkitTests(this, isSbt)
      } else {
        NEList.from(registeredTests())
      }

      testsToRun match {
        case Some(tests) =>
          doRun(tests.toList, testName, args, status, sjsDisableGlobalMemoization, isSbt)
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

  private[distage] def doRun[F0[_]](
    testsInThisRun: Seq[DistageTest[F0]],
    testName: Option[String],
    args: Args,
    status: StatefulStatus,
    sjsDisableGlobalMemoization: Boolean,
    isSbt: Boolean,
  ): Unit = {
    val debugLogger: TrivialLogger = TrivialLogger.make[DistageScalatestTestSuiteRunner[F]](DebugProperties.`izumi.distage.testkit.debug`.name)

    debugLogger.log(s"Scalatest Args: $args")
    debugLogger.log(s"""tagsToInclude: ${args.filter.tagsToInclude}
                       |tagsToExclude: ${args.filter.tagsToExclude}
                       |dynaTags: ${args.filter.dynaTags}
                       |excludeNestedSuites: ${args.filter.excludeNestedSuites}
                       |""".stripMargin)

    val toRun = applyScalatestDefaultFiltering(args, testsInThisRun, testName)

    try {
      debugLogger.err(s"GOING TO RUN TESTS in ${tagMonoIO.tag.repr} (in class ${getClass.getName}):${toRun.map(_.meta.test.id.toString).niceList()}")
      val testReporter = mkTestReporter(isSbt)

      val isJVM = !IzPlatform.isScalaJS
      if (isJVM) {
        val testResults = TestkitRunnerModule.run[Identity](testReporter, (t: Throwable) => t.isInstanceOf[TestCanceledException], toRun)
        debugLogger.log(s"Got for ${tagMonoIO.tag}: testResults=${testResults.niceList()}")
      } else {

        val globalEc = IzPlatform.platformGlobalExecutionContext

        TestkitRunnerModule
          .run[MiniBIOAsync[Throwable, _]](testReporter, (t: Throwable) => t.isInstanceOf[TestCanceledException], toRun)
          .runOnEC(globalEc)
          .onComplete {
            t =>
              val res = t.fold(Exit.Error.forThrowable, identity).toThrowableEither

              if (sjsDisableGlobalMemoization) {
                if (!status.isCompleted()) {
                  res.left.foreach(status.setFailedWith)
                  status.setCompleted()
                }
              } else {
                DistageTestsRegistrySingleton.completeStatuses()
              }

              res match {
                case Right(testResults) =>
                  debugLogger.log(s"Got for ${tagMonoIO.tag}: testResults=${testResults.niceList()}")
                case Left(t) =>
                  t.printStackTrace()
                  throw t
              }
          }(using globalEc)
      }
    } catch {
      case t: Throwable =>
        // Make sure status is completed even when errors occur
        if (sjsDisableGlobalMemoization) {
          if (!status.isCompleted()) {
            status.setFailedWith(t)
            status.setCompleted()
          }
        }
        t.printStackTrace()
        throw t
    } finally {
      if (!sjsDisableGlobalMemoization) {
        // precaution. shouldn't be necessary
        DistageTestsRegistrySingleton.completeStatuses()
      }
    }
  }

  private[distage] def mkTestReporter(isSbt: Boolean): TestReporter = {
    val suiteHandler = DistageTestsRegistrySingleton.mkSuiteHandlerById()
    val scalatestReporter = new DistageScalatestReporter(suiteHandler)
    if (isSbt) scalatestReporter else new SafeIntellijTestReporter(scalatestReporter)
  }

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

  private def applyScalatestDefaultFiltering[F0[_]](args: Args, testsInThisRuntime: Seq[DistageTest[F0]], testName: Option[String]): Seq[DistageTest[F0]] = {
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
