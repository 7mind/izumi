package izumi.logstage.api

import izumi.fundamentals.platform.language.IzScala
import izumi.logstage.api.rendering.logunits.LogFormat
import izumi.logstage.api.rendering.{RenderingOptions, StringRenderingPolicy}
import logstage.LogIO2
import org.scalatest.wordspec.AnyWordSpec
import zio.{Runtime, Task, Unsafe, ZIO}

import scala.util.{Failure, Success, Try}

class LoggerLogMethodTest extends AnyWordSpec {
  private val tc = new TestClass
  private val logFormat = LogFormat.Default
  private val renderingOptions = RenderingOptions.colorless
  private val runtime = Runtime.default

  "IzLogger.logMethod and LogIO.logMethod " should {
    "log method" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = IzLogger(sink = testSink)

      logger.logMethod(Log.Level.Info)(tc.testFunc(x = 1, y = 2))

      val logEntry = testSink.fetch().head
      assert(renderMessage(logEntry) == "Call to testFunc(x=1, y=2) => result=3.0")
    }

    "log curried method" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = IzLogger(sink = testSink)

      logger.logMethod(Log.Level.Info)(tc.curriedFunc(x = 1)(y = 2))

      val logEntry = testSink.fetch().head
      assert(renderMessage(logEntry) == "Call to curriedFunc(x=1)(y=2) => result=3.0")
    }

    "log generic method" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = IzLogger(sink = testSink)

      logger.logMethod(Log.Level.Info)(tc.genericFunc[Int, Double](x = 2, y = 2.0))

      val logEntry = testSink.fetch().head
      if (IzScala.scalaRelease.major == 3) {
        assert(renderMessage(logEntry) == "Call to genericFunc[A=scala.Int B=scala.Double](x=2, y=2.0) => result=22.0")
      } else {
        assert(renderMessage(logEntry) == "Call to genericFunc[A=Int B=Double](x=2, y=2.0) => result=22.0")
      }
    }

    "log higher kinded type with implicit method" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = IzLogger(sink = testSink)

      implicit val b: Int = 2
      logger.logMethod(Log.Level.Info)(tc.hktCurFuncWithImplicit[List, Int](List(1, 2)))

      val logEntry = testSink.fetch().head
      if (IzScala.scalaRelease.major == 3) {
        assert(renderMessage(logEntry) == "Call to hktCurFuncWithImplicit[C=scala.List A=scala.Int](a=1; 2)(b=2) => result=true")
      } else {
        assert(renderMessage(logEntry) == "Call to hktCurFuncWithImplicit[C=List A=Int](a=1; 2)(b=2) => result=true")
      }
    }

    "log no arguments method" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = IzLogger(sink = testSink)

      logger.logMethod(Log.Level.Info)(tc.noArgsFunc())

      val logEntry = testSink.fetch().head
      assert(renderMessage(logEntry) == "Call to noArgsFunc() => result=()")
    }

    "log method with context bound" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = IzLogger(sink = testSink)

      implicit val ordering: Ordering[Int] = Ordering.Int
      logger.logMethod(Log.Level.Info)(tc.withContextBoundFunc(List(1, 2, 3)))

      val logEntry = testSink.fetch().head
      if (IzScala.scalaRelease.major == 3) {
        assert(renderMessage(logEntry) == s"Call to withContextBoundFunc[A=scala.Int](list=1; 2; 3)(evidence$$_1=${ordering.toString}) => result=1; 2; 3")
      } else {
        assert(renderMessage(logEntry) == s"Call to withContextBoundFunc[A=Int](list=1; 2; 3)(evidence$$_1=${ordering.toString}) => result=1; 2; 3")
      }
    }

    "log error" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = IzLogger(sink = testSink)

      Try(logger.logMethod(Log.Level.Info)(tc.withError(-1, 2))) match {
        case Failure(_) =>
          val logEntry = testSink.fetch().head
          assert(renderMessage(logEntry).startsWith("Call to withError(a=-1, b=2) => error=java.lang.Exception: Error during execution"))
        case Success(_) => fail()
      }
    }

    "filter type and implicit information" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = IzLogger(sink = testSink)

      implicit val b: Int = 2
      logger.logMethod(Log.Level.Info, false)(tc.hktCurFuncWithImplicit[List, Int](List(1, 2)))
      logger.logMethod(Log.Level.Info, false, false)(tc.hktCurFuncWithImplicit[List, Int](List(1, 2)))

      val logEntry = testSink.fetch().toIndexedSeq
      val withoutTypes = renderMessage(logEntry(0))
      val withoutTypesAndImplicits = renderMessage(logEntry(1))

      assert(withoutTypes == "Call to hktCurFuncWithImplicit(a=1; 2)(b=2) => result=true")
      assert(withoutTypesAndImplicits == "Call to hktCurFuncWithImplicit(a=1; 2) => result=true")
    }
  }

  "LogIO.logMethod" should {
    "log method wrapped in effect type" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger: LogIO2[zio.IO] = LogIO2.fromLogger(IzLogger(sink = testSink))

      runEff {
        logger.logMethodF(Log.Level.Info)(tc.withF(1, 2))
      }

      val logEntry = testSink.fetch().head
      assert(renderMessage(logEntry) == s"Call to withF(x=1, y=2) => result=3.0")
    }

    "log unwrapped method" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger: LogIO2[zio.IO] = LogIO2.fromLogger(IzLogger(sink = testSink))

      runEff {
        logger.logMethod[zio.Task, Double](Log.Level.Info)(tc.testFunc(1, 2))
      }

      val logEntry = testSink.fetch().head
      assert(renderMessage(logEntry) == "Call to testFunc(x=1, y=2) => result=3.0")
    }

    "log error" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger: LogIO2[zio.IO] = LogIO2.fromLogger(IzLogger(sink = testSink))

      Try(runEff(logger.logMethodF[zio.Task, Int](Log.Level.Info)(tc.withErrorF(-1, 2)))) match {
        case Failure(_) =>
          val logEntry = testSink.fetch().head
          assert(renderMessage(logEntry).startsWith("Call to withErrorF(a=-1, b=2) => error=java.lang.Exception: Error during execution"))
        case Success(_) => fail()
      }
    }
  }

  private def renderMessage(entry: Log.Entry): String = {
    logFormat.formatMessage(entry, renderingOptions).message
  }

  private def runEff(thunk: ZIO[Any, Any, Any]): Any = {
    runtime.unsafe
      .run(thunk)(implicitly, Unsafe.unsafe(identity))
      .getOrThrowFiberFailure()(Unsafe.unsafe(identity))
  }

  final class TestClass {
    def testFunc(x: Int, y: Int): Double = x.toDouble + y.toDouble
    def curriedFunc(x: Int)(y: Int): Double = x.toDouble + y.toDouble
    def genericFunc[A, B](x: A, y: B): String = x.toString + y.toString

    def hktCurFuncWithImplicit[C[X] <: Iterable[X], A](a: C[A])(implicit b: A): Boolean = {
      a.exists((_: A) == b)
    }
    def noArgsFunc(): Unit = ()
    def withContextBoundFunc[A: Ordering](list: List[A]): List[A] = list.sorted

    def withF(x: Int, y: Int): zio.IO[Nothing, Double] = ZIO.succeed(x.toDouble + y.toDouble)

    def withError(a: Int, b: Int): Int = {
      if (a < 0) {
        throw new Exception("Error during execution")
      } else a + b
    }
    def withErrorF(a: Int, b: Int): Task[Int] = {
      if (a < 0) {
        ZIO.fail(new Exception("Error during execution"))
      } else ZIO.attempt(a + b)
    }
  }
}
