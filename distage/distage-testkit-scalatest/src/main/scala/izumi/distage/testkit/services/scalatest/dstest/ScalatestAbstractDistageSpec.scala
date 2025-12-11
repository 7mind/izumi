package izumi.distage.testkit.services.scalatest.dstest

import distage.{Functoid, TagK, TagKK}
import izumi.distage.constructors.ZEnvConstructor
import izumi.distage.testkit.model.*
import izumi.distage.testkit.services.scalatest.dstest.ScalatestAbstractDistageSpec.*
import izumi.distage.testkit.spec.*
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.language.{SourceFilePosition, SourceFilePositionMaterializer}
import org.scalatest.Assertion
import org.scalatest.distage.{NameUtil, TestCancellation}
import org.scalatest.verbs.{CanVerb, MustVerb, ShouldVerb, StringVerbBlockRegistration}
import zio.ZIO

import scala.annotation.unused
import scala.language.implicitConversions

@org.scalatest.Finders(value = Array("org.scalatest.finders.WordSpecFinder"))
trait ScalatestAbstractDistageSpec[F[_]] extends AbstractDistageSpec[F] with ShouldVerb with MustVerb with CanVerb with DistageTestEnv with WithTestRegistration[F] {

  override protected def config: TestConfig = TestConfig.forSuite(this.getClass)

  final protected lazy val testEnv: TestEnvironment = makeTestEnv()
  protected def makeTestEnv(): TestEnvironment = loadEnvironment[F](config, tagMonoIO, defaultModulesIO)

  protected def distageSuiteName: String = NameUtil.exportNameUtil.getSimpleNameOfAnObjectsClass(this)
  protected def distageSuiteId: SuiteId = SuiteId(this.getClass.getName)

  protected implicit val subjectRegistrationFunction1: StringVerbBlockRegistration = (desc, verb, _, f) => registerBranch(desc, verb, f)

  protected def registerBranch(description: String, verb: String, fun: () => Unit): Unit = {
    val outerPrefix = context.fold(Seq.empty[String])(_.prefix)
    context = Some(SuiteContext(outerPrefix ++ Seq(description, verb)))
    fun()
    context = None
  }
  private[distage] var context: Option[SuiteContext] = None
}

object ScalatestAbstractDistageSpec {

  trait For1[F[_]] extends ScalatestAbstractDistageSpec[F] {
    protected implicit def convertToWordSpecStringWrapperDS(s: String): DSWordSpecStringWrapper[F] = {
      new DSWordSpecStringWrapper(context, distageSuiteName, distageSuiteId, Seq(s), this, testEnv)
    }
  }

  trait For2[F[+_, +_]] extends ScalatestAbstractDistageSpec[F[Throwable, _]] {
    implicit def tagBIO: TagKK[F]

    protected implicit def convertToWordSpecStringWrapperDS2(s: String): DSWordSpecStringWrapper2[F] = {
      new DSWordSpecStringWrapper2(context, distageSuiteName, distageSuiteId, Seq(s), this, testEnv)
    }
  }

  trait ForZIO extends ScalatestAbstractDistageSpec[ZIO[Any, Throwable, _]] {
    protected implicit def convertToWordSpecStringWrapperDS3(s: String): DSWordSpecStringWrapperZIO = {
      new DSWordSpecStringWrapperZIO(context, distageSuiteName, distageSuiteId, Seq(s), this, testEnv)
    }
  }

  final case class SuiteContext(prefix: Seq[String]) extends AnyVal {
    def toName(name: Seq[String]): Seq[String] = prefix ++ name
  }

  open class DSWordSpecStringWrapper[F[_]](
    context: Option[SuiteContext],
    suiteName: String,
    suiteId: SuiteId,
    testname: Seq[String],
    reg: TestRegistration[F],
    env: TestEnvironment,
  )(implicit override val tagMonoIO: TagK[F]
  ) extends DISyntaxBase[F]
    with DSWordSpecStringWrapperLowPriorityIdentityOverloads[F] {

    infix def in(function: Functoid[F[Unit]])(implicit pos: SourceFilePositionMaterializer): Unit = {
      takeIO(function, pos.get)
    }

    infix def in(function: Functoid[F[Assertion]])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit): Unit = {
      takeIO(function, pos.get)
    }

    infix def in(value: => F[Unit])(implicit pos: SourceFilePositionMaterializer): Unit = {
      takeIO(() => value, pos.get)
    }

    infix def in(value: => F[Assertion])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit): Unit = {
      takeIO(() => value, pos.get)
    }

    override protected def takeIO[A](function: Functoid[F[A]], pos: SourceFilePosition): Unit = {
      val id = TestId(context.fold(testname)(_.toName(testname)), suiteId)
      reg.registerTest(function, env, pos, id, SuiteMeta(id.suite, suiteName, suiteId.suiteId))
    }
  }

  open class DSWordSpecStringWrapper2[F[+_, +_]](
    context: Option[SuiteContext],
    suiteName: String,
    suiteId: SuiteId,
    testname: Seq[String],
    reg: TestRegistration[F[Throwable, _]],
    env: TestEnvironment,
  )(implicit override val tagBIO: TagKK[F],
    override val tagMonoIO: TagK[F[Throwable, _]],
  ) extends DISyntaxBIOBase[F]
    with DSWordSpecStringWrapperLowPriorityIdentityOverloads[F[Throwable, _]] {

    infix def in(function: Functoid[F[Any, Unit]])(implicit pos: SourceFilePositionMaterializer): Unit = {
      takeBIO(function.asInstanceOf[Functoid[F[Any, Any]]], pos.get)
    }

    infix def in(function: Functoid[F[Any, Assertion]])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit): Unit = {
      takeBIO(function.asInstanceOf[Functoid[F[Any, Any]]], pos.get)
    }

    infix def in(value: => F[Any, Unit])(implicit pos: SourceFilePositionMaterializer): Unit = {
      takeBIO(() => value.asInstanceOf[F[Any, Any]], pos.get)
    }

    infix def in(value: => F[Any, Assertion])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit): Unit = {
      takeBIO(() => value.asInstanceOf[F[Any, Any]], pos.get)
    }

    override protected def takeIO[A](fAsThrowable: Functoid[F[Throwable, A]], pos: SourceFilePosition): Unit = {
      val id = TestId(context.fold(testname)(_.toName(testname)), suiteId)
      reg.registerTest(fAsThrowable, env, pos, id, SuiteMeta(id.suite, suiteName, suiteId.suiteId))
    }
  }

  open class DSWordSpecStringWrapperZIO(
    context: Option[SuiteContext],
    suiteName: String,
    suiteId: SuiteId,
    testname: Seq[String],
    reg: TestRegistration[ZIO[Any, Throwable, _]],
    env: TestEnvironment,
  )(implicit override val tagBIO: TagKK[ZIO[Any, _, _]],
    override val tagMonoIO: TagK[ZIO[Any, Throwable, _]],
  ) extends DISyntaxBIOBase[ZIO[Any, +_, +_]]
    with DSWordSpecStringWrapperLowPriorityIdentityOverloads[ZIO[Any, Throwable, _]] {

    infix def in[R: ZEnvConstructor](function: Functoid[ZIO[R, Any, Unit]])(implicit pos: SourceFilePositionMaterializer): Unit = {
      takeBIO(
        function.map2(ZEnvConstructor[R]) {
          case (eff, r) => eff.provideEnvironment(r)
        },
        pos.get,
      )
    }

    infix def in[R: ZEnvConstructor](function: Functoid[ZIO[R, Any, Assertion]])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit): Unit = {
      takeBIO(
        function.map2(ZEnvConstructor[R]) {
          case (eff, r) => eff.provideEnvironment(r)
        },
        pos.get,
      )
    }

    infix def in[R: ZEnvConstructor](value: => ZIO[R, Any, Unit])(implicit pos: SourceFilePositionMaterializer): Unit = {
      takeBIO(ZEnvConstructor[R].map(value.provideEnvironment(_)), pos.get)
    }

    infix def in[R: ZEnvConstructor](value: => ZIO[R, Any, Assertion])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit): Unit = {
      takeBIO(ZEnvConstructor[R].map(value.provideEnvironment(_)), pos.get)
    }

    infix def in(function: Functoid[ZIO[Any, Any, Unit]])(implicit pos: SourceFilePositionMaterializer): Unit = {
      takeBIO(function, pos.get)
    }

    infix def in(function: Functoid[ZIO[Any, Any, Assertion]])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit): Unit = {
      takeBIO(function, pos.get)
    }

    infix def in(value: => ZIO[Any, Any, Unit])(implicit pos: SourceFilePositionMaterializer): Unit = {
      takeBIO(() => value, pos.get)
    }

    infix def in(value: => ZIO[Any, Any, Assertion])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit): Unit = {
      takeBIO(() => value, pos.get)
    }

    override protected def takeIO[A](fAsThrowable: Functoid[ZIO[Any, Throwable, A]], pos: SourceFilePosition): Unit = {
      val id = TestId(context.fold(testname)(_.toName(testname)), suiteId)
      reg.registerTest(fAsThrowable, env, pos, id, SuiteMeta(id.suite, suiteName, suiteId.suiteId))
    }
  }

  trait DSWordSpecStringWrapperLowPriorityIdentityOverloads[F[_]] extends DISyntaxBase[F] {

    infix def in(function: Functoid[Unit])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit, d2: DummyImplicit): Unit = {
      takeAny(function, pos.get)
    }

    infix def in(function: Functoid[Assertion])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit, d2: DummyImplicit, d3: DummyImplicit): Unit = {
      takeAny(function, pos.get)
    }

    infix def in(value: => Unit)(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit, d2: DummyImplicit): Unit = {
      takeAny(() => value, pos.get)
    }

    infix def in(value: => Assertion)(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit, d2: DummyImplicit, d3: DummyImplicit): Unit = {
      takeAny(() => value, pos.get)
    }

    infix def skip(@unused value: => Any)(implicit pos: SourceFilePositionMaterializer): Unit = {
      takeFunIO[Nothing, QuasiIO[F]](cancel, pos.get)
    }

    private def cancel[A](F: QuasiIO[F]): F[A] = {
      F.maybeSuspend(cancelNow())
    }

    private def cancelNow(): Nothing = {
      TestCancellation.cancel(Some("test skipped!"), None, 1)
    }
  }

}
