package org.scalatest

import com.github.pshirshov.izumi.distage.roles.services.IntegrationCheckerImpl
import com.github.pshirshov.izumi.distage.testkit.DistageTestRunner.{DistageTest, TestMeta, TestReporter, TestStatus}
import com.github.pshirshov.izumi.distage.testkit.{DistageTestEnvironmentImpl, DistageTestRunner, DistageTestsRegistrySingleton}
import com.github.pshirshov.izumi.logstage.api.{IzLogger, Log}
import distage.TagK
import org.scalatest.events._

import scala.collection.immutable.TreeSet

trait DistageScalatestTestSuite[F[_]] extends Suite {
  thisSuite =>
  implicit def tagMonoIO: TagK[F]

//  private val otherSuites = {
//
//    PluginLoaderDefaultImpl.load[AbstractDistageSpec[F]](classOf[AbstractDistageSpec[F]], Seq(classOf[AbstractDistageSpec[F]].getCanonicalName, classOf[DistageSpec[F]].getCanonicalName), Seq(this.getClass.getPackage.getName), Seq.empty, false)
//  }
//
//  private val filtered: Seq[AbstractDistageSpec[F]] = otherSuites.filter(s => SafeType.getK[F](s.tagMonoIO)== SafeType.getK[F](tagMonoIO))
//  registeredTests ++= filtered.flatMap(_.registeredTests)


  override final protected def runNestedSuites(args: Args): Status = {
    throw new UnsupportedOperationException
  }

  override protected final def runTests(testName: Option[String], args: Args): Status = {
    throw new UnsupportedOperationException
  }

  override protected final def runTest(testName: String, args: Args): Status = {
    throw new UnsupportedOperationException
  }

  override def testNames: Set[String] = {
    //    def isTestMethod(m: Method) = {
    //
    //      val isInstanceMethod = !Modifier.isStatic(m.getModifiers())
    //
    //      val paramTypes = m.getParameterTypes
    //      val hasNoParams = paramTypes.length == 0
    //      // val hasVoidReturnType = m.getReturnType == Void.TYPE
    ////      val hasTestAnnotation = m.getAnnotation(classOf[org.junit.Test]) != null
    //
    ////      isInstanceMethod && hasNoParams && hasTestAnnotation
    //      false
    //    }
    //
    //    val testNameArray =
    //      for (m <- getClass.getMethods; if isTestMethod(m))
    //        yield m.getName
    TreeSet[String]() ++ ownTests.map(_.meta.id.name) // registeredTests.map(_.meta.id.name)
  }

  private def ownTests: Seq[DistageTest[F]] = {
    monadTests.filter(_.meta.id.suiteId == suiteId)
  }

  private def monadTests: Seq[DistageTest[F]] = {
    DistageTestsRegistrySingleton.list[F]
  }

  override def expectedTestCount(filter: Filter): Int = {
    if (filter.tagsToInclude.isDefined) {
      0
    } else {
      testNames.size - tags.size
    }
  }


  override def tags: Map[String, Set[String]] = {
    //    val elements =
    //      for (testName <- testNames; if hasIgnoreTag(testName))
    //        yield testName -> Set("org.scalatest.Ignore")
    //    autoTagClassAnnotations(Map() ++ elements, this)

    Map.empty
  }

  //  private def getMethodForJUnitTestName(testName: String): Method = {
  //    getClass.getMethod(testName, new Array[Class[_]](0): _*)
  //  }
  //  private def hasIgnoreTag(testName: String) = {
  //    false
  //    //getMethodForJUnitTestName(testName).getAnnotation(classOf[org.junit.Ignore]) != null
  //  }

  override def testDataFor(testName: String, theConfigMap: ConfigMap = ConfigMap.empty): TestData = {
    val suiteTags = for {
      a <- this.getClass.getAnnotations
      annotationClass = a.annotationType
      if annotationClass.isAnnotationPresent(classOf[TagAnnotation])
    } yield {
      annotationClass.getName
    }

    val testTags: Set[String] = Set.empty
    //      try {
    //        if (hasIgnoreTag(testName))
    //          Set("org.scalatest.Ignore")
    //        else
    //          Set.empty[String]
    //      }
    //      catch {
    //        case e: IllegalArgumentException => Set.empty[String]
    //      }

    new TestData {
      val configMap: ConfigMap = theConfigMap
      val name: String = testName
      val scopes: Vector[Nothing] = Vector.empty
      val text: String = testName
      val tags: Set[String] = Set.empty ++ suiteTags ++ testTags
      val pos: None.type = None
    }
  }

  /**
    * Overrides to use JUnit 4 to run the test(s).
    *
    * @param testName an optional name of one test to run. If <code>None</code>, all relevant tests should be run.
    *                 I.e., <code>None</code> acts like a wildcard that means run all relevant tests in this <code>Suite</code>.
    * @param args     the <code>Args</code> for this run
    * @return a <code>Status</code> object that indicates when all tests and nested suites started by this method have completed, and whether or not a failure occurred.
    *
    */
  override def run(testName: Option[String], args: Args): Status = {
    val status = new StatefulStatus

    if (args.filter.tagsToInclude.isEmpty) {
      val logger = IzLogger.apply(Log.Level.Debug)("phase" -> "test")

      val checker = new IntegrationCheckerImpl(logger)
      val ruenv = new DistageTestEnvironmentImpl[F]

      val tracker = args.tracker

      val trackers = monadTests.map {
        t =>
          t.meta.id -> tracker.nextTracker()
      }.toMap

      def ord(testId: TestMeta) = trackers(testId.id).nextOrdinal()

      def recordStart(test: TestMeta): Unit = {
        args.reporter.apply(TestStarting(
          ord(test),
          suiteName, suiteId, Some(suiteId),
          test.id.name,
          test.id.name,
          location = Some(LineInFile(test.pos.position.line, test.pos.position.file, None)),
        ))
      }

      val dreporter = new TestReporter {
        override def testStatus(test: TestMeta, testStatus: TestStatus): Unit = {
          testStatus match {
            case TestStatus.Scheduled =>

            case TestStatus.Running =>
              recordStart(test)

            case TestStatus.Succeed(duration) =>
              args.reporter.apply(TestSucceeded(
                ord(test),
                suiteName, suiteId, Some(suiteId),
                test.id.name,
                test.id.name,
                scala.collection.immutable.IndexedSeq.empty[RecordableEvent],
                location = Some(LineInFile(test.pos.position.line, test.pos.position.file, None)),
                duration = Some(duration.toMillis),
              ))
            case TestStatus.Failed(t, duration) =>
              args.reporter.apply(TestFailed(
                ord(test),
                "Test failed",
                suiteName, suiteId, Some(suiteId),
                test.id.name,
                test.id.name,
                scala.collection.immutable.IndexedSeq.empty[RecordableEvent],
                location = Some(LineInFile(test.pos.position.line, test.pos.position.file, None)),
                throwable = Some(t),
                duration = Some(duration.toMillis),
              ))
            case TestStatus.Cancelled(checks) =>
              recordStart(test)
              args.reporter.apply(TestCanceled(
                ord(test),
                "ignored",
                suiteName, suiteId, Some(suiteId),
                test.id.name,
                test.id.name,
                scala.collection.immutable.IndexedSeq.empty[RecordableEvent],
                location = Some(LineInFile(test.pos.position.line, test.pos.position.file, None)),
              ))
          }


          ///println(s"Test ${test.string} is $testStatus")
        }
      }

      val toRun = testName match {
        case None =>
          monadTests
        case Some(tn) =>
          if (!testNames.contains(tn)) {
            throw new IllegalArgumentException(Resources.testNotFound(testName))
          } else {
            monadTests.filter(_.meta.id.name == tn)
          }
      }


      val runner = new DistageTestRunner[F](dreporter, checker, ruenv, toRun)


      runner.run()
    }

    status.setCompleted()
    status
  }


  final override val styleName: String = "DistageSuite"

}
