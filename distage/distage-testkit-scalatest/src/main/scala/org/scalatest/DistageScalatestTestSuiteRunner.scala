package org.scalatest

import java.util.concurrent.atomic.AtomicBoolean

import distage.TagK
import io.github.classgraph.ClassGraph
import izumi.distage.framework.services.IntegrationChecker
import izumi.distage.testkit.SpecConfig
import izumi.distage.testkit.services.dstest.DistageTestRunner._
import izumi.distage.testkit.services.dstest.{AbstractDistageSpec, DistageTestRunner, SpecEnvironment}
import izumi.distage.testkit.services.scalatest.dstest.DistageTestsRegistrySingleton
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.api.{IzLogger, Log}
import org.scalatest.events._
import org.scalatest.exceptions.TestCanceledException

import scala.collection.immutable.TreeSet
import scala.util.Try

trait ScalatestInitWorkaround {
  def awaitTestsLoaded(): Unit
}

object ScalatestInitWorkaround {

  class ScalatestInitWorkaroundImpl[F[_]](runner: DistageScalatestTestSuiteRunner[F]) extends ScalatestInitWorkaround {
    ScalatestInitWorkaroundImpl.doScan(runner)

    override def awaitTestsLoaded(): Unit = ScalatestInitWorkaroundImpl.awaitTestsLoaded()
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
        val scan = new ClassGraph().disableJarScanning().enableClassInfo().addClassLoader(classLoader).scan()
        val specs = scan.getClassesImplementing(classOf[DistageScalatestTestSuiteRunner[Identity]].getCanonicalName).asScala.filterNot(_.isAbstract)
        specs.map(spec => Try(spec.loadClass().getDeclaredConstructor().newInstance()))
        DistageTestsRegistrySingleton.disableRegistration()
        latch.countDown()
      }
    }

  }

}

trait DistageScalatestTestSuiteRunner[F[_]] extends Suite with AbstractDistageSpec[F] {
  protected[scalatest] val init = new ScalatestInitWorkaround.ScalatestInitWorkaroundImpl[F](this)

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

  override def run(testName: Option[String], args: Args): Status = {
    val status = new StatefulStatus
    init.awaitTestsLoaded()

    try {
      DistageTestsRegistrySingleton.proceedWithTests[F]() match {
        case Some(value) =>
          doRun(value, testName, args)
        case None =>
          addStub(args, None)
      }
    } catch {
      case t: Throwable =>
        // IDEA reporter is insane
        //status.setFailedWith(t)
        addStub(args, Some(t))

    } finally {
      status.setCompleted()
    }

    status
  }

  override def testNames: Set[String] = {
    val testsInThisTestClass = DistageTestsRegistrySingleton.list[F].filter(_.meta.id.suiteId == suiteId)
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
      val configMap: ConfigMap = theConfigMap
      val name: String = testName
      val scopes: Vector[Nothing] = Vector.empty
      val text: String = testName
      val tags: Set[String] = Set.empty ++ suiteTags ++ testTags
      val pos: None.type = None
    }
  }

  private def doRun(candidatesForThisRuntime: Seq[DistageTest[F]], testName: Option[String], args: Args): Unit = {
    val dreporter = mkTestReporter(args)

    val toRun = testName match {
      case None =>
        val fakeSuiteId = suiteId
        candidatesForThisRuntime.filter {
          test =>
            val tags: Map[String, Set[String]] = Map.empty
            // for this check we need fool filter to think that all our tests belong to current suite
            val (filterTest, ignoreTest) = args.filter.apply(test.meta.id.name, tags, fakeSuiteId)
            val isOk = !filterTest && !ignoreTest
            isOk
        }

      case Some(testName) =>
        if (!testNames.contains(testName)) {
          throw new IllegalArgumentException(Resources.testNotFound(testName))
        } else {
          candidatesForThisRuntime.filter(_.meta.id.name == testName)
        }
    }

    val runner = {
      val logger = IzLogger(Log.Level.Debug)("phase" -> "test")
      val checker = new IntegrationChecker.Impl(logger)
      new DistageTestRunner[F](dreporter, checker, specEnv, toRun, _.isInstanceOf[TestCanceledException], parallelTestsAlways)
    }

    runner.run()
  }

  // FIXME: read scalatest runner configuration???
  protected def parallelTestsAlways: Boolean = true

  private def mkTestReporter(args: Args): TestReporter = {
    val scalatestReporter = new ScalatestReporter(args, suiteName, suiteId)
    new SafeTestReporter(scalatestReporter)
  }

  private def addStub(args: Args, failure: Option[Throwable]): Unit = {
    val tracker = args.tracker
    val FUCK_SCALATEST = "Scalatest and IDEA aren't so nice"
    val SUITE_FAILED = "Whole suite failed :/"

    failure match {
      case Some(_) =>
        args.reporter(TestStarting(
          tracker.nextOrdinal(),
          suiteName, suiteId, Some(suiteId),
          SUITE_FAILED,
          SUITE_FAILED,
        ))
        args.reporter(TestFailed(
          tracker.nextOrdinal(),
          s"suite failed",
          suiteName, suiteId, Some(suiteId),
          SUITE_FAILED,
          SUITE_FAILED,
          scala.collection.immutable.IndexedSeq.empty[RecordableEvent],
          throwable = failure
        ))
      case None =>
        args.reporter(TestCanceled(
          tracker.nextOrdinal(), FUCK_SCALATEST,
          suiteName, suiteId, Some(suiteId),
          FUCK_SCALATEST,
          FUCK_SCALATEST,
          Vector.empty,
        ))
    }

  }

  override final val styleName: String = "DistageSuite"
}



