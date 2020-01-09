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
import org.scalatest.exceptions.TestCanceledException

import scala.collection.immutable.TreeSet
import scala.util.Try

trait ScalatestInitWorkaround {
  def awaitTestsLoaded(): Unit
}

object ScalatestInitWorkaround {
  def apply[F[_]](runner: DistageScalatestTestSuiteRunner[F]): ScalatestInitWorkaround = {
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
        val nearest2Packages = instance.getClass.getPackage.getName.split('.').toSeq.inits.take(2).map(_.mkString(".")).toSeq
        val scan = new ClassGraph()
          .whitelistJars("distage-testkit-scalatest*")
          .whitelistPackages(nearest2Packages: _*)
          .enableClassInfo()
          .addClassLoader(classLoader)
          .scan()
        try {
          val specs = scan.getClassesImplementing(classOf[DistageScalatestTestSuiteRunner[Identity]].getCanonicalName).asScala.filterNot(_.isAbstract)
          specs.foreach(spec => Try(spec.loadClass().getDeclaredConstructor().newInstance()))
          DistageTestsRegistrySingleton.disableRegistration()
          latch.countDown()
        } finally {
          scan.close()
        }
      }
    }

  }

}

trait DistageScalatestTestSuiteRunner[F[_]] extends Suite with AbstractDistageSpec[F] {
  protected[scalatest] val init = ScalatestInitWorkaround[F](this)

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
      configOverrides = c.configOverrides
    )
  }

  override protected final def runNestedSuites(args: Args): Status = throw new UnsupportedOperationException
  override protected final def runTests(testName: Option[String], args: Args): Status = throw new UnsupportedOperationException
  override protected final def runTest(testName: String, args: Args): Status = throw new UnsupportedOperationException

  // initialize status early, so that runner can set it to `true`
  private[this] val status: StatefulStatus = DistageTestsRegistrySingleton.registerStatus(suiteId)

  override def run(testName: Option[String], args: Args): Status = {
    DistageTestsRegistrySingleton.registerTracker(suiteId)(args.tracker)
    init.awaitTestsLoaded()

    try {
      DistageTestsRegistrySingleton.proceedWithTests[F]() match {
        case Some(value) =>
          doRun(value, testName, args)
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
      override val configMap: ConfigMap = theConfigMap
      override val name: String = testName
      override val scopes: Vector[Nothing] = Vector.empty
      override val text: String = testName
      override val tags: Set[String] = Set.empty ++ suiteTags ++ testTags
      override val pos: None.type = None
    }
  }

  private def doRun(candidatesForThisRuntime: Seq[DistageTest[F]], testName: Option[String], args: Args): Unit = {
    println(args)
    println(
      s"""tagsToInclude: ${args.filter.tagsToInclude}
         |tagsToExclude: ${args.filter.tagsToExclude}
         |dynaTags: ${args.filter.dynaTags}
         |excludeNestedSuites: ${args.filter.excludeNestedSuites}
         |""".stripMargin)
    val testReporter = mkTestReporter(args)

    val toRun = testName match {
      case None =>
        val tags: Map[String, Set[String]] = Map.empty
        candidatesForThisRuntime.filter {
          test =>
            val (filterTest, ignoreTest) = args.filter.apply(test.meta.id.name, tags, test.meta.id.suiteId)
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

    if (toRun.isEmpty) {
      return
    } else {
      println(s"TORUN: ${toRun.map(_.meta.id.name)}")
    }

    val runner = {
      val logger = IzLogger(Log.Level.Debug)("phase" -> "test")
      val checker = new IntegrationChecker.Impl(logger)
      new DistageTestRunner[F](testReporter, checker, specEnv, toRun, _.isInstanceOf[TestCanceledException], parallelTestsAlways)
    }

    try {
      runner.run()
    } finally {
      DistageTestsRegistrySingleton.completeStatuses(toRun.map(_.meta.id.suiteId).toSet)
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



