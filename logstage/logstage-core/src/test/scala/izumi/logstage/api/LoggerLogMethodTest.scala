package izumi.logstage.api

import izumi.functional.bio.TypedError
import izumi.fundamentals.platform.language.IzScala
import izumi.fundamentals.platform.language.Quirks.Discarder
import izumi.logstage.api.Log.LogArg
import izumi.logstage.api.rendering.{RenderingOptions, StringRenderingPolicy}
import izumi.logstage.api.strict.IzStrictLogger
import izumi.logstage.macros.EncodingMode
import logstage.{LogIO2, LogZIO, LogstageCodec}
import org.scalatest.exceptions.TestFailedException
import org.scalatest.wordspec.AnyWordSpec
import zio.{FiberFailure, Task, Unsafe, ZEnvironment, ZIO}

import scala.util.{Failure, Success, Try}

class LoggerLogMethodTest extends AnyWordSpec {
  private val tc = new TestClass
  private val runtime = zio.Runtime.default

  def `log method`(test: TestSink => Any)(implicit testFuncName: String = "testFunc", mode: EncodingMode = EncodingMode.NonStrict): Unit = {
    val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))

    test(testSink)

    val Seq(logEntry) = testSink.fetch()

    mode match {
      case EncodingMode.Raw =>
        assert(logEntry.message.template == StringContext("Call to testFunc(1, 2) => 3.0"))
        assert(logEntry.message.args.isEmpty)
      case _ =>
        val stringContext = StringContext(
          s"Call to $testFuncName(",
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
        assert(logEntry.context.static.pos.position.file.contains("LoggerLogMethodTest.scala"))
    }
    ()
  }

  def `log curried method`(test: TestSink => Any): Unit = {
    val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))

    test(testSink)

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
    val Seq(logEntry) = testSink.fetch()
    assert(logEntry.message.template == stringContext)
    assert(logEntry.message.args == args).discard()
  }

  def `log method with side-effecting parameter has surprising semantics`(test: TestSink => Any): Unit = {
    val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))

    test(testSink)

    val Seq(logEntry) = testSink.fetch()
    val stringContext = StringContext(
      "Call to add(",
      ") => ",
      "",
    )
    val args = Seq(
      LogArg(Seq("x"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
      LogArg(Seq("result"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
    )
    assert(logEntry.message.template == stringContext)
    assert(logEntry.message.args == args)
    ()
  }

  def `log method with by-name parameter has surprising semantics`(test: TestSink => Any): Unit = {
    val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))

    test(testSink)

    val Seq(logEntry) = testSink.fetch()
    val stringContext = StringContext(
      "Call to byNameTestFunc(",
      ") => ",
      "",
    )
    val args = Seq(
      LogArg(Seq("fn"), 3, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
      LogArg(Seq("result"), List(1, 2), hiddenName = false, Some(LogstageCodec.listCodec(LogstageCodec.LogstageCodecInt))),
    )
    assert(logEntry.message.template == stringContext)
    assert(logEntry.message.args == args)
    ()
  }

  def `log overloaded methods`(test: TestSink => Any): Unit = {
    val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))

    test(testSink)

    val Seq(add1P, add2P, add2PD) = testSink.fetch().toIndexedSeq

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
    ()
  }

  def `filter type and implicit information`(test: TestSink => Any): Unit = {
    val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))

    test(testSink)

    val logEntry = testSink.fetch().toIndexedSeq
    val withoutTypes = logEntry(0)
    val withoutTypesAndImplicits = logEntry(1)
    val withTypesWithoutImplicits = logEntry(2)

    val (withoutTypesStringContext, withoutTypesArgs) = {
      val stringContext = StringContext(
        "Call to hktCurFuncWithImplicit(",
        ")(",
        ", ",
        ", ",
        ") => ",
        "",
      )
      val args = Seq(
        LogArg(Seq("a"), 1, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        LogArg(Seq("b"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        LogArg(Seq("c"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        LogArg(Seq("d"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        LogArg(Seq("result"), false, hiddenName = false, Some(LogstageCodec.LogstageCodecBoolean)),
      )
      (stringContext, args)
    }
    assert(withoutTypes.message.template == withoutTypesStringContext)
    assert(withoutTypes.message.args == withoutTypesArgs)

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
    assert(withoutTypesAndImplicits.message.template == withoutTypesAndImplicitsStringContext)
    assert(withoutTypesAndImplicits.message.args == withoutTypesAndImplicitsArgs)

    val (withTypesWithoutImplicitsStringContext, withTypesWithoutImplicitsArgs) = {
      val stringContext = StringContext(
        "Call to hktCurFuncWithImplicit[",
        ", ",
        ", ",
        "](",
        ") => ",
        "",
      )
      val args = Seq(
        LogArg(Seq("C"), "List", hiddenName = false, Some(LogstageCodec.LogstageCodecString)),
        LogArg(Seq("F"), "Option[String]", hiddenName = false, Some(LogstageCodec.LogstageCodecString)),
        LogArg(Seq("A"), "Int", hiddenName = false, Some(LogstageCodec.LogstageCodecString)),
        LogArg(Seq("a"), 1, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        LogArg(Seq("result"), false, hiddenName = false, Some(LogstageCodec.LogstageCodecBoolean)),
      )
      (stringContext, args)
    }
    assert(withTypesWithoutImplicits.message.template == withTypesWithoutImplicitsStringContext)
    assert(withTypesWithoutImplicits.message.args == withTypesWithoutImplicitsArgs)
    ()
  }

  def `log error`(test: TestSink => Try[Any])(implicit testFuncName: String = "withError"): Unit = {
    val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))

    test(testSink) match {
      case Failure(e) =>
        val stringContext = StringContext(
          s"Call to $testFuncName(",
          ", ",
          ") => ",
          "",
        )
        val args = Seq(
          LogArg(Seq("a"), -1, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
          LogArg(Seq("b"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
          LogArg(Seq("error"), e, hiddenName = false, Some(LogstageCodec.LogstageCodecThrowable)),
        )

        val Seq(logEntry) = testSink.fetch()
        assert(logEntry.message.template == stringContext)
        assert(logEntry.message.args == args)
        ()
      case succ @ Success(_) =>
        fail(s"Expected failure but got success=$succ")
    }
  }

  def `log method with context bound`(test: TestSink => Ordering[?]): Unit = {
    val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))

    val ordering = test(testSink)

    val stringContext = StringContext(
      "Call to withContextBoundFunc[",
      "](",
      ", ",
      ")(",
      ") => ",
      "",
    )
    val args = Seq(
      LogArg(Seq("A"), "Int", hiddenName = false, Some(LogstageCodec.LogstageCodecString)),
      LogArg(Seq("x"), 1, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
      LogArg(Seq("y"), 1, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
      LogArg(Seq("evidence$1"), ordering, hiddenName = false, None),
      LogArg(Seq("result"), true, hiddenName = false, Some(LogstageCodec.LogstageCodecBoolean)),
    )

    val Seq(logEntry) = testSink.fetch()
    assert(logEntry.message.template == stringContext)
    assert(logEntry.message.args == args)
    ()
  }

  def `log no arguments method`(test: TestSink => Any): Unit = {
    val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))

    test(testSink)

    val stringContext = StringContext(
      "Call to noArgsFunc() => ",
      "",
    )
    val args = Seq(
      LogArg(Seq("result"), (), hiddenName = false, None)
    )

    val Seq(logEntry) = testSink.fetch()
    assert(logEntry.message.template == stringContext)
    assert(logEntry.message.args == args)
    ()
  }

  def `log higher kinded type with implicit method`(test: TestSink => Any): Unit = {
    val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))

    test(testSink)

    val stringContext = StringContext(
      "Call to hktCurFuncWithImplicit[",
      ", ",
      ", ",
      "](",
      ")(",
      ", ",
      ", ",
      ") => ",
      "",
    )
    val vectorTryStringTpeString = if (IzScala.scalaRelease.major == 2) "Vector[scala.util.Try[String]]" else "Vector[Try[String]]"
    val args = Seq(
      LogArg(Seq("C"), "List", hiddenName = false, Some(LogstageCodec.LogstageCodecString)),
      LogArg(Seq("F"), vectorTryStringTpeString, hiddenName = false, Some(LogstageCodec.LogstageCodecString)),
      LogArg(Seq("A"), "Int", hiddenName = false, Some(LogstageCodec.LogstageCodecString)),
      LogArg(Seq("a"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
      LogArg(Seq("b"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
      LogArg(Seq("c"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
      LogArg(Seq("d"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
      LogArg(Seq("result"), true, hiddenName = false, Some(LogstageCodec.LogstageCodecBoolean)),
    )

    val Seq(logEntry) = testSink.fetch()
    assert(logEntry.message.template == stringContext)
    assert(logEntry.message.args == args)
    ()
  }

  def `log higher kinded type with implicit method without types`(test: TestSink => Any): Unit = {
    val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))

    test(testSink)

    val stringContext = StringContext(
      "Call to hktCurFuncWithImplicit(",
      ")(",
      ", ",
      ", ",
      ") => ",
      "",
    )
    val args = Seq(
      LogArg(Seq("a"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
      LogArg(Seq("b"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
      LogArg(Seq("c"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
      LogArg(Seq("d"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
      LogArg(Seq("result"), true, hiddenName = false, Some(LogstageCodec.LogstageCodecBoolean)),
    )

    val Seq(logEntry) = testSink.fetch()
    assert(logEntry.message.template == stringContext)
    assert(logEntry.message.args == args)
    ()
  }

  def `log generic method`(test: TestSink => Any): Unit = {
    val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))

    test(testSink)

    val stringContext = StringContext(
      "Call to genericFunc[",
      ", ",
      "](",
      ", ",
      ") => ",
      "",
    )

    val args = Seq(
      LogArg(Seq("A"), "Int", hiddenName = false, Some(LogstageCodec.LogstageCodecString)),
      LogArg(Seq("B"), "String", hiddenName = false, Some(LogstageCodec.LogstageCodecString)),
      LogArg(Seq("x"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
      LogArg(Seq("y"), "b", hiddenName = false, Some(LogstageCodec.LogstageCodecString)),
      LogArg(Seq("result"), "2b", hiddenName = false, Some(LogstageCodec.LogstageCodecString)),
    )

    val Seq(logEntry) = testSink.fetch()
    assert(logEntry.message.template == stringContext)
    assert(logEntry.message.args == args).discard()
  }

  "IzLogger.logMethod" should {

    "log method" in {
      `log method` {
        testSink =>
          val logger = IzLogger(sink = testSink)

          val x = 1
          val result = 2
          val res = logger.logMethod(Log.Level.Info)(tc.testFunc(x, result))
          assert(res == 3.0)
      }
    }

    "log curried method" in {
      `log curried method` {
        testSink =>
          val logger = IzLogger(sink = testSink)
          logger.logMethod(Log.Level.Info)(tc.curriedFunc(x = 1)(y = 2))
      }
    }

    "log generic method" in {
      `log generic method` {
        testSink =>
          val logger = IzLogger(sink = testSink)
          logger.logMethod(Log.Level.Info, printTypes = true)(tc.genericFunc[Int, String](x = 2, y = "b"))
      }
    }

    "log higher kinded type with implicit method" in {
      `log higher kinded type with implicit method` {
        testSink =>
          val logger = IzLogger(sink = testSink)
          implicit val b: Int = 2
          logger.logMethod(Log.Level.Info, printTypes = true, printImplicits = true)(tc.hktCurFuncWithImplicit[List, Vector[Try[String]], Int](2))
      }
    }

    "log higher kinded type with implicit method without types" in {
      `log higher kinded type with implicit method without types` {
        testSink =>
          val logger = IzLogger(sink = testSink)
          implicit val b: Int = 2
          logger.logMethod(Log.Level.Info, printTypes = false, printImplicits = true)(tc.hktCurFuncWithImplicit[List, Vector[Try[String]], Int](2))
      }
    }

    "log no arguments method" in {
      `log no arguments method` {
        testSink =>
          val logger = IzLogger(sink = testSink)
          logger.logMethod(Log.Level.Info)(tc.noArgsFunc())

      }
    }

    "log method with context bound" in {
      `log method with context bound` {
        testSink =>
          val logger = IzLogger(sink = testSink)
          implicit val ordering: Ordering[Int] = Ordering.Int
          logger.logMethod(Log.Level.Info, printTypes = true, printImplicits = true)(tc.withContextBoundFunc(1, 1))
          ordering
      }
    }

    "log error" in {
      `log error` {
        testSink =>
          val logger = IzLogger(sink = testSink)
          Try(logger.logMethod(Log.Level.Info)(tc.withError(-1, 2)))
      }
    }

    "filter type and implicit information" in {
      `filter type and implicit information` {
        testSink =>
          val logger = IzLogger(sink = testSink)
          implicit val b: Int = 2
          logger.logMethod(Log.Level.Info, printTypes = false, printImplicits = true)(tc.hktCurFuncWithImplicit[List, Option[String], Int](1))
          logger.logMethod(Log.Level.Info, printTypes = false, printImplicits = false)(tc.hktCurFuncWithImplicit[List, Option[String], Int](1))
          logger.logMethod(Log.Level.Info, printTypes = true, printImplicits = false)(tc.hktCurFuncWithImplicit[List, Option[String], Int](1))
      }
    }

    "log overloaded methods" in {
      `log overloaded methods` {
        testSink =>
          val logger = IzLogger(sink = testSink)
          logger.logMethod(Log.Level.Info)(tc.add(1))
          logger.logMethod(Log.Level.Info)(tc.add(1, 1))
          logger.logMethod(Log.Level.Info)(tc.add(1.0, 1.0))
      }
    }

    "log method with by-name parameter has surprising semantics" in {
      `log method with by-name parameter has surprising semantics` {
        testSink =>
          val logger = IzLogger(sink = testSink)
          var counter = 0
          logger.logMethod(Log.Level.Info)(tc.byNameTestFunc { counter += 1; counter })
          assert(counter == 3)
      }
    }

    "log method with side-effecting parameter has surprising semantics" in {
      `log method with side-effecting parameter has surprising semantics` {
        testSink =>
          val logger = IzLogger(sink = testSink)
          var counter = 0
          logger.logMethod(Log.Level.Info)(tc.add { counter += 1; counter })
          assert(counter == 2)
      }
    }

  }

  "IzStrictLogger.logMethod" should {

    "fail to log method with context bound when there's no LogstageCodec instance" in {
      val logger = IzStrictLogger()
      implicit val ordering: Ordering[Int] = Ordering.Int
      (logger, ordering).discard()
      val err = intercept[TestFailedException] {
        assertCompiles("logger.logMethod(Log.Level.Info, true, true)(tc.withContextBoundFunc(1, 1))")
      }

      assert(err.getMessage().contains("Implicit search failed"))
      assert(err.getMessage().contains("LogstageCodec["))
      assert(err.getMessage().contains("Ordering["))
      assert(err.getMessage().contains("Int]"))
    }

  }

  "RawLogger.logMethod" should {
    "log method" in {
      `log method` {
        testSink =>
          val logger = IzLogger(sink = testSink).raw

          val x = 1
          val result = 2
          val res = logger.logMethod(Log.Level.Info)(tc.testFunc(x, result))
          assert(res == 3.0)
      }(using mode = EncodingMode.Raw)
    }
  }

  "LogIO.logMethod" should {

    "log unwrapped method" in {
      `log method` {
        testSink =>
          val logger: LogIO2[zio.IO] = LogIO2.fromLogger(IzLogger(sink = testSink))
          runEff {
            logger.logMethod(Log.Level.Info)(tc.testFunc(1, 2))
          }
      }
    }

    "log unwrapped method with LogIO[F[Throwable, _]]" in {
      `log method` {
        testSink =>
          val logger: LogIO2[zio.IO] = LogIO2.fromLogger(IzLogger(sink = testSink))
          runEff {
            logger.widenError[Throwable].logMethod(Log.Level.Info)(tc.testFunc(1, 2))
          }
      }
    }

    "logZIO unwrapped method" in {
      `log method` {
        testSink =>
          val logger: LogIO2[zio.IO] = LogIO2.fromLogger(IzLogger(sink = testSink))
          runEff {
            val eff = LogZIO.log.logMethod(Log.Level.Info)(tc.testFunc(1, 2))
            eff.provideEnvironment(ZEnvironment[LogZIO](logger))
          }
      }
    }

    "logIO log method with by-name parameter has surprising semantics" in {
      `log method with by-name parameter has surprising semantics` {
        testSink =>
          val logger: LogIO2[zio.IO] = LogIO2.fromLogger(IzLogger(sink = testSink))
          var counter = 0
          runEff {
            logger.logMethod(Log.Level.Info)(tc.byNameTestFunc { counter += 1; counter })
          }
          assert(counter == 3)
      }
    }

    "logIO log method with side-effecting parameter has surprising semantics" in {
      `log method with side-effecting parameter has surprising semantics` {
        testSink =>
          val logger: LogIO2[zio.IO] = LogIO2.fromLogger(IzLogger(sink = testSink))
          var counter = 0
          runEff {
            logger.logMethod(Log.Level.Info)(tc.add { counter += 1; counter })
          }
          assert(counter == 2)
      }
    }

  }

  "LogIO.logMethodF" should {

    "log method wrapped in effect type" in {
      `log method` {
        testSink =>
          val logger: LogIO2[zio.IO] = LogIO2.fromLogger(IzLogger(sink = testSink))
          runEff {
            logger.logMethodF(Log.Level.Info, printTypes = true).apply(tc.withF(1, 2))
          }
      }(using testFuncName = "withF")
    }

    "log error" in {
      `log error` {
        testSink =>
          val logger: LogIO2[zio.IO] = LogIO2.fromLogger(IzLogger(sink = testSink))
          Try(runEff {
            logger.logMethodF(Log.Level.Info)(tc.withErrorF(-1, 2))
          })
      }(using testFuncName = "withErrorF")
    }

  }

  def runEff[A](thunk: ZIO[Any, Any, A]): A = {
    try Unsafe.unsafe(implicit unsafe => runtime.unsafe.run(thunk).getOrThrowFiberFailure())
    catch {
      case f: FiberFailure => throw f.cause.squashWith(TypedError.wrapIfNotThrowable)
    }
  }

  final class TestClass {
    def testFunc(x: Int, y: Int): Double = x.toDouble + y.toDouble
    def curriedFunc(x: Int)(y: Int): Double = x.toDouble + y.toDouble
    def genericFunc[A, B](x: A, y: B): String = x.toString + y.toString

    def byNameTestFunc(fn: => Int): List[Int] = {
      List.fill(2)(fn)
    }

    def hktCurFuncWithImplicit[C[X] <: Iterable[X], F, A](a: A)(implicit b: A, c: A, d: A): Boolean = {
      c.discard()
      d.discard()
      a == b
    }
    def noArgsFunc(): Unit = ()
    def withContextBoundFunc[A: Ordering](x: A, y: A): Boolean = Ordering[A].equiv(x, y)

    def withF(x: Int, y: Int): zio.IO[Nothing, Double] = {
      println(x + y)
      ZIO.succeed(println((x + y) * 2)) *>
      ZIO.succeed(x.toDouble + y.toDouble)
    }

    def byNameTestFuncF(fn: => Int): Task[List[Int]] = {
      ZIO.replicateZIO(2)(ZIO.succeed(fn)).map(_.toList)
    }

    def withError[E: Numeric: Ordering](a: E, b: E): E = {
      import Ordering.Implicits.*
      import Numeric.Implicits.*

      if (a < Numeric[E].zero) {
        throw new Exception("Error during execution")
      } else {
        a + b
      }
    }
    def withErrorF[E: Numeric: Ordering](a: E, b: E): Task[E] = {
      import Ordering.Implicits.*
      import Numeric.Implicits.*

      if (a < Numeric[E].zero) {
        ZIO.fail(new Exception("Error during execution"))
      } else {
        ZIO.attempt(a + b)
      }
    }

    def add(x: Int): Int = x + x
    def add(x: Int, y: Int): Int = x + y
    def add(y: Double, z: Double): Int = y.toInt + z.toInt
  }
}
