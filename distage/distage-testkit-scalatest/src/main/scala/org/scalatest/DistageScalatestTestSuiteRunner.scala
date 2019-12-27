package org.scalatest

import distage.TagK
import izumi.distage.framework.services.IntegrationChecker
import izumi.distage.testkit.SpecConfig
import izumi.distage.testkit.services.dstest.DistageTestRunner._
import izumi.distage.testkit.services.dstest.{AbstractDistageSpec, DistageTestRunner, SpecEnvironment}
import izumi.distage.testkit.services.scalatest.dstest.DistageTestsRegistrySingleton
import izumi.logstage.api.{IzLogger, Log}
import org.scalatest.exceptions.TestCanceledException

import scala.collection.immutable.TreeSet

trait DistageScalatestTestSuiteRunner[F[_]] extends Suite with AbstractDistageSpec[F] {
  implicit def tagMonoIO: TagK[F]

  private[this] lazy val specEnv: SpecEnvironment = makeSpecEnvironment()

  protected def specConfig: SpecConfig = SpecConfig()

  protected def makeSpecEnvironment(): SpecEnvironment = {
    val c = specConfig
    val clazz = this.getClass

    new SpecEnvironment.Impl[F](
      suiteClass = clazz,
      contextOptions = c.contextOptions,
      bootstrapOverrides = c.bootstrapOverrides,
      moduleOverrides = c.moduleOverrides,
      bootstrapLogLevel = c.bootstrapLogLevel,
    )
  }

  override protected final def runNestedSuites(args: Args): Status = throw new UnsupportedOperationException
  override protected final def runTests(testName: Option[String], args: Args): Status = throw new UnsupportedOperationException
  override protected final def runTest(testName: String, args: Args): Status = throw new UnsupportedOperationException

  override def testNames: Set[String] = {
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

  private[this] def testsInThisTestClass: Seq[DistageTest[F]] = {
    testsInThisMonad.filter(_.meta.id.suiteId == suiteId)
  }

  private[this] def testsInThisMonad: Seq[DistageTest[F]] = {
    DistageTestsRegistrySingleton.list[F]
  }

  private[this] val status = new StatefulStatus
  DistageTestsRegistrySingleton.registerStatus(suiteId, status)

  override def run(testName: Option[String], args: Args): Status = {
    DistageTestsRegistrySingleton.registerTracker(suiteId)(args.tracker)
    try {
      if (DistageTestsRegistrySingleton.ticketToProceed[F]()) {
        doRun(testName, args)
      }
    } catch {
      case t: Throwable =>
        status.setFailedWith(t)
        status.setCompleted()
    }
    status
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

  private def doRun(testName: Option[String], args: Args): Unit = {
    val testReporter = mkTestReporter(args)

    val toRun = testName match {
      case None =>
        val enabled = args.filter.dynaTags.testTags.toSeq
          .flatMap {
            case (suiteId, tests) =>
              tests
                .filter(_._2.contains(Suite.SELECTED_TAG))
                .keys
                .map {
                  testname =>
                    (suiteId, testname)
                }
          }
          .toSet

        if (enabled.isEmpty) {
          testsInThisMonad
        } else {
          testsInThisMonad.filter(t => enabled.contains((t.meta.id.suiteId, t.meta.id.name)))
        }
      case Some(testName) =>
        if (!testNames.contains(testName)) {
          throw new IllegalArgumentException(Resources.testNotFound(testName))
        } else {
          testsInThisMonad.filter(_.meta.id.name == testName)
        }
    }

    val runner = {
      val logger = IzLogger(Log.Level.Debug)("phase" -> "test")
      val checker = new IntegrationChecker.Impl(logger)
      new DistageTestRunner[F](testReporter, checker, specEnv, toRun, _.isInstanceOf[TestCanceledException], parallelTestsAlways)
    }

    try {
      runner.run()
    } finally {
      DistageTestsRegistrySingleton.completeStatuses(testsInThisMonad.map(_.meta.id.suiteId).toSet)
    }
  }

  // FIXME: read scalatest runner configuration???
  protected def parallelTestsAlways: Boolean = true

  private def mkTestReporter(args: Args): TestReporter = {
    val scalatestReporter = new ScalatestReporter(args.reporter)
    new SafeTestReporter(scalatestReporter)
  }

  override final val styleName: String = "DistageSuite"
}



