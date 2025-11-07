package org.scalatest.distage

import izumi.distage.modules.DefaultModule
import izumi.distage.testkit.DebugProperties
import izumi.distage.testkit.model.DistageTest
import izumi.distage.testkit.runner.TestkitRunnerModule
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.services.scalatest.dstest.DistageTestsRegistrySingleton.RunningSuiteHandle
import izumi.distage.testkit.services.scalatest.dstest.{DistageTestsRegistrySingleton, SafeIntellijTestReporter, ScalatestInitWorkaround}
import izumi.distage.testkit.spec.AbstractDistageSpec
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
  override protected def withFixture(test: NoArgTest): Outcome = throw new UnsupportedOperationException

  // create status early, so that runner can set it to `true` even before this test's
  // `run` method is called by scalatest, because all the suite's tests could have
  // already been executed by another suite before this `run` was called
  private val singletonStatus: StatefulStatus = DistageTestsRegistrySingleton.registerInstantiatedSuite[F](suiteId, this)

  override def run(testName: Option[String], args: Args): Status = {
    val status = singletonStatus

    DistageTestsRegistrySingleton.registerSuiteHandle(suiteId)(RunningSuiteHandle(args.tracker, args.reporter, status))

    // If, we're running under sbt, scan the classpath manually to add all tests
    // in the classloader before starting anything, because sbt runner
    // instantiates & runs tests at the same time, so when `run` is called
    // NOT all tests have been registered, so we must force all tests, otherwise
    // we can't be sure.
    //
    // NON-sbt ScalatestRunner first instantiates ALL tests, THEN calls `.run` method,
    // so for non-sbt runs we KNOW that all tests have already been registered, so we
    // don't have to scan the classpath ourselves.
    val isSbt = args.reporter.getClass.getName.contains("org.scalatest.tools.Framework")

    try {
      val testsToRun = if (ScalatestInitWorkaround.useGlobalMemoization) {
        ScalatestInitWorkaround.collectAllTestkitTests(this, isSbt)
      } else {
        // PER-INSTANCE MODE: Each suite runs only its own local tests
        // All DistageScalatestTestSuiteRunner extend WithSingletonTestRegistration
        println(s"Using per-instance test execution (deinverted mode - each suite runs its own tests)")
        NEList.from(registeredTests())
      }

      testsToRun match {
        case Some(tests) =>
          doRun(tests.toList, testName, args, status, ScalatestInitWorkaround.useGlobalMemoization, isSbt)
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
    useGlobalMemoization: Boolean,
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
      if (toRun.nonEmpty) {
        debugLogger.err(s"GOING TO RUN TESTS in ${tagMonoIO.tag.repr} (in class ${getClass.getName}):${toRun.map(_.meta.test.id.toString).niceList()}")
        val testReporter = mkTestReporter(isSbt)

        if (!IzPlatform.isScalaJS) {
          // id impl
          val testResults =
            try TestkitRunnerModule.run[Identity](testReporter, (t: Throwable) => t.isInstanceOf[TestCanceledException], toRun)
            finally {
              if (useGlobalMemoization) {
                // Global memoization mode: complete all statuses for F type
                DistageTestsRegistrySingleton.completeStatuses()
              } else {
                // Per-instance mode: complete only this suite's status
                if (!status.isCompleted()) {
                  status.setCompleted()
                }
              }
            }
          debugLogger.log(s"Got for ${tagMonoIO.tag}: testResults=${testResults.niceList()}")
        } else {
          //    import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global
          import scala.concurrent.ExecutionContext.Implicits.global
          val _ = global

          // MiniBIOAsync impl
          import izumi.functional.bio.Exit
          import izumi.functional.bio.impl.MiniBIOAsync

          TestkitRunnerModule
            .run[MiniBIOAsync[Throwable, _]](testReporter, (t: Throwable) => t.isInstanceOf[TestCanceledException], toRun)
            .runOnEC(implicitly)
            .onComplete {
              t =>
                val exit = t.fold(Exit.Error.forThrowable, identity)

                if (useGlobalMemoization) {
                  // Global memoization mode: complete all statuses for F type
                  DistageTestsRegistrySingleton.completeStatuses()
                } else {
                  // Per-instance mode: complete only this suite's status
                  if (!status.isCompleted()) {
                    exit match {
                      case Exit.Success(_) =>
                        status.setCompleted()
                      case Exit.Error(e, _) =>
                        status.setFailedWith(e)
                        status.setCompleted()
                      case Exit.Termination(t, _, _) =>
                        status.setFailedWith(t)
                        status.setCompleted()
                    }
                  }
                }

                exit match {
                  case Exit.Success(testResults) =>
                    debugLogger.log(s"Got for ${tagMonoIO.tag}: testResults=${testResults.niceList()}")
                  case Exit.Error(e, trace) =>
                    val tx = trace.unsafeAttachTraceOrReturnNewThrowable()
                    tx.printStackTrace()
                    throw e
                  case Exit.Termination(t, _, trace) =>
                    val tx = trace.unsafeAttachTraceOrReturnNewThrowable()
                    tx.printStackTrace()
                    throw t
                }
            }
        }

      } else {
        // No tests to run - still need to complete status in per-instance mode
        if (!useGlobalMemoization) {
          if (!status.isCompleted()) {
            status.setCompleted()
          }
        }
      }
    } catch {
      case t: Throwable =>
        // Make sure status is completed even when errors occur
        if (!useGlobalMemoization) {
          if (!status.isCompleted()) {
            status.setFailedWith(t)
            status.setCompleted()
          }
        }
        t.printStackTrace()
        throw t
    } finally {}
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
