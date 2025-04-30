package izumi.logstage.api

import izumi.fundamentals.platform.language.IzScala
import izumi.logstage.api.Log.LogArg
import izumi.logstage.api.rendering.{RenderingOptions, StringRenderingPolicy}
import logstage.{LogIO2, LogstageCodec}
import org.scalatest.wordspec.AnyWordSpec
import zio.{Task, Unsafe, ZIO}

import scala.util.{Failure, Success, Try}

class LoggerLogMethodTest extends AnyWordSpec {
  private val tc = new TestClass
  private val runtime = zio.Runtime.default

  "IzLogger.logMethod and LogIO.logMethod " should {
    "log method" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = IzLogger(sink = testSink)

      logger.logMethod(Log.Level.Info)(tc.testFunc(x = 1, y = 2))

      val logEntry = testSink.fetch().head
      val stringContext = StringContext(
        "Call to testFunc(",
        ", ",
        ") => ",
        "",
      )
      val args = Seq(
        LogArg(Seq("x"), 1, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        LogArg(Seq("y"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        LogArg(Seq("result"), 3.0, hiddenName = false, Some(LogstageCodec.LogstageCodecDouble)),
      )
      assert(logEntry.message.template == stringContext)
      assert(logEntry.message.args == args)
    }

    "log curried method" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = IzLogger(sink = testSink)

      logger.logMethod(Log.Level.Info)(tc.curriedFunc(x = 1)(y = 2))

      val stringContext = StringContext(
        "Call to curriedFunc(",
        ")(",
        ") => ",
        "",
      )
      val args = Seq(
        LogArg(Seq("x"), 1, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        LogArg(Seq("y"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        LogArg(Seq("result"), 3.0, hiddenName = false, Some(LogstageCodec.LogstageCodecDouble)),
      )
      val logEntry = testSink.fetch().head
      assert(logEntry.message.template == stringContext)
      assert(logEntry.message.args == args)
    }

    "log generic method" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = IzLogger(sink = testSink)

      logger.logMethod(Log.Level.Info)(tc.genericFunc[Int, Int](x = 2, y = 2))

      val str = if (IzScala.scalaRelease.major == 3) {
        "Call to genericFunc[A=scala.Int B=scala.Int]("
      } else {
        "Call to genericFunc[A=Int B=Int]("
      }
      val stringContext = StringContext(
        str,
        ", ",
        ") => ",
        "",
      )

      val args = Seq(
        LogArg(Seq("x"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        LogArg(Seq("y"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        LogArg(Seq("result"), "22", hiddenName = false, Some(LogstageCodec.LogstageCodecString)),
      )

      val logEntry = testSink.fetch().head
      assert(logEntry.message.template == stringContext)
      assert(logEntry.message.args == args)
    }

    "log higher kinded type with implicit method" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = IzLogger(sink = testSink)

      implicit val b: Int = 2
      logger.logMethod(Log.Level.Info)(tc.hktCurFuncWithImplicit[List, Int](2))

      val str = if (IzScala.scalaRelease.major == 3) {
        "Call to hktCurFuncWithImplicit[C=scala.List A=scala.Int]("
      } else {
        "Call to hktCurFuncWithImplicit[C=List A=Int]("
      }
      val stringContext = StringContext(
        str,
        ")(",
        ") => ",
        "",
      )
      val args = Seq(
        LogArg(Seq("a"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        LogArg(Seq("b"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        LogArg(Seq("result"), true, hiddenName = false, Some(LogstageCodec.LogstageCodecBoolean)),
      )

      val logEntry = testSink.fetch().head
      assert(logEntry.message.template == stringContext)
      assert(logEntry.message.args == args)
    }

    "log no arguments method" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = IzLogger(sink = testSink)

      logger.logMethod(Log.Level.Info)(tc.noArgsFunc())

      val stringContext = StringContext(
        "Call to noArgsFunc() => ",
        "",
      )
      val args = Seq(
        LogArg(Seq("result"), (), hiddenName = false, None)
      )

      val logEntry = testSink.fetch().head
      assert(logEntry.message.template == stringContext)
      assert(logEntry.message.args == args)
    }

    "log method with context bound" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = IzLogger(sink = testSink)

      implicit val ordering: Ordering[Int] = Ordering.Int
      logger.logMethod(Log.Level.Info)(tc.withContextBoundFunc(1, 1))

      val str = if (IzScala.scalaRelease.major == 3) {
        "Call to withContextBoundFunc[A=scala.Int]("
      } else {
        "Call to withContextBoundFunc[A=Int]("
      }
      val stringContext = StringContext(
        str,
        ", ",
        ")(",
        ") => ",
        "",
      )
      val args = Seq(
        LogArg(Seq("x"), 1, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        LogArg(Seq("y"), 1, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        LogArg(Seq("evidence$1"), ordering, hiddenName = false, None),
        LogArg(Seq("result"), true, hiddenName = false, Some(LogstageCodec.LogstageCodecBoolean)),
      )

      val logEntry = testSink.fetch().head
      assert(logEntry.message.template == stringContext)
      assert(logEntry.message.args == args)
    }

    "log error" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = IzLogger(sink = testSink)

      Try(logger.logMethod(Log.Level.Info)(tc.withError(-1, 2))) match {
        case Failure(e) =>
          val stringContext = StringContext(
            "Call to withError(",
            ", ",
            ") => ",
            "",
          )
          val args = Seq(
            LogArg(Seq("a"), -1, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
            LogArg(Seq("b"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
            LogArg(Seq("error"), e, hiddenName = false, Some(LogstageCodec.LogstageCodecThrowable)),
          )

          val logEntry = testSink.fetch().head
          assert(logEntry.message.template == stringContext)
          assert(logEntry.message.args == args)
        case Success(_) => fail()
      }
    }

    "filter type and implicit information" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = IzLogger(sink = testSink)

      implicit val b: Int = 2
      logger.logMethod(Log.Level.Info, false)(tc.hktCurFuncWithImplicit[List, Int](1))
      logger.logMethod(Log.Level.Info, false, false)(tc.hktCurFuncWithImplicit[List, Int](1))

      val logEntry = testSink.fetch().toIndexedSeq
      val withoutTypes = logEntry(0)
      val withoutTypesAndImplicits = logEntry(1)

      val (withoutTypesStringContext, withoutTypesArgs) = {
        val stringContext = StringContext(
          "Call to hktCurFuncWithImplicit(",
          ")(",
          ") => ",
          "",
        )
        val args = Seq(
          LogArg(Seq("a"), 1, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
          LogArg(Seq("b"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
          LogArg(Seq("result"), false, hiddenName = false, Some(LogstageCodec.LogstageCodecBoolean)),
        )
        (stringContext, args)
      }

      val (withoutTypesAndImplicitsStringContext, withoutTypesAndImplicitsArgs) = {
        val stringContext = StringContext(
          "Call to hktCurFuncWithImplicit(",
          ") => ",
          "",
        )
        val args = Seq(
          LogArg(Seq("a"), 1, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
          LogArg(Seq("result"), false, hiddenName = false, Some(LogstageCodec.LogstageCodecBoolean)),
        )
        (stringContext, args)
      }

      assert(withoutTypes.message.template == withoutTypesStringContext)
      assert(withoutTypes.message.args == withoutTypesArgs)
      assert(withoutTypesAndImplicits.message.template == withoutTypesAndImplicitsStringContext)
      assert(withoutTypesAndImplicits.message.args == withoutTypesAndImplicitsArgs)
    }

    "log overloaded methods" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = IzLogger(sink = testSink)

      logger.logMethod(Log.Level.Info)(tc.add(1))
      logger.logMethod(Log.Level.Info)(tc.add(1, 1))
      logger.logMethod(Log.Level.Info)(tc.add(1.0, 1.0))

      val logEntry = testSink.fetch().toIndexedSeq
      val add1P = logEntry(0)
      val add2P = logEntry(1)
      val add2PD = logEntry(2)

      val (add1PStringContext, add1PArgs) = {
        val stringContext = StringContext(
          "Call to add(",
          ") => ",
          "",
        )
        val args = Seq(
          LogArg(Seq("x"), 1, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
          LogArg(Seq("result"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        )
        (stringContext, args)
      }

      val (add2PStringContext, add2PArgs) = {
        val stringContext = StringContext(
          "Call to add(",
          ", ",
          ") => ",
          "",
        )
        val args = Seq(
          LogArg(Seq("x"), 1, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
          LogArg(Seq("y"), 1, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
          LogArg(Seq("result"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        )
        (stringContext, args)
      }

      val (add2PDStringContext, add2PDArgs) = {
        val stringContext = StringContext(
          "Call to add(",
          ", ",
          ") => ",
          "",
        )
        val args = Seq(
          LogArg(Seq("y"), 1.0, hiddenName = false, Some(LogstageCodec.LogstageCodecDouble)),
          LogArg(Seq("z"), 1.0, hiddenName = false, Some(LogstageCodec.LogstageCodecDouble)),
          LogArg(Seq("result"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        )
        (stringContext, args)
      }

      assert(add1P.message.template == add1PStringContext)
      assert(add1P.message.args == add1PArgs)
      assert(add2P.message.template == add2PStringContext)
      assert(add2P.message.args == add2PArgs)
      assert(add2PD.message.template == add2PDStringContext)
      assert(add2PD.message.args == add2PDArgs)
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

      val stringContext = StringContext(
        "Call to withF(",
        ", ",
        ") => ",
        "",
      )
      val args = Seq(
        LogArg(Seq("x"), 1, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        LogArg(Seq("y"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        LogArg(Seq("result"), 3.0, hiddenName = false, Some(LogstageCodec.LogstageCodecDouble)),
      )

      assert(logEntry.message.template == stringContext)
      assert(logEntry.message.args == args)
    }

    "log unwrapped method" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger: LogIO2[zio.IO] = LogIO2.fromLogger(IzLogger(sink = testSink))

      runEff {
        logger.logMethod[zio.Task, Double](Log.Level.Info)(tc.testFunc(1, 2))
      }

      val logEntry = testSink.fetch().head

      val stringContext = StringContext(
        "Call to testFunc(",
        ", ",
        ") => ",
        "",
      )
      val args = Seq(
        LogArg(Seq("x"), 1, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        LogArg(Seq("y"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        LogArg(Seq("result"), 3.0, hiddenName = false, Some(LogstageCodec.LogstageCodecDouble)),
      )

      assert(logEntry.message.template == stringContext)
      assert(logEntry.message.args == args)
    }

    "log error" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger: LogIO2[zio.IO] = LogIO2.fromLogger(IzLogger(sink = testSink))

      runEff {
        logger.logMethodF[zio.Task, Int](Log.Level.Info)(tc.withErrorF(-1, 2)).catchAll {
          e =>
            ZIO.attempt {
              val stringContext = StringContext(
                "Call to withErrorF(",
                ", ",
                ") => ",
                "",
              )
              val args = Seq(
                LogArg(Seq("a"), -1, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
                LogArg(Seq("b"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
                LogArg(Seq("error"), e, hiddenName = false, None),
              )

              val logEntry = testSink.fetch().head
              assert(logEntry.message.template == stringContext)
              assert(logEntry.message.args == args)
            }
        }
      }
    }
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

    def hktCurFuncWithImplicit[C[X] <: Iterable[X], A](a: A)(implicit b: A): Boolean = {
      a == b
    }
    def noArgsFunc(): Unit = ()
    def withContextBoundFunc[A: Ordering](x: A, y: A): Boolean = Ordering[A].equiv(x, y)

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

    def add(x: Int): Int = x + x
    def add(x: Int, y: Int): Int = x + y
    def add(y: Double, z: Double): Int = y.toInt + z.toInt
  }
}
