package izumi.distage.testkit.services.scalatest.dstest

import distage.{Functoid, TagK, TagKK}
import izumi.distage.constructors.ZEnvConstructor
import izumi.distage.testkit.model.*
import izumi.distage.testkit.services.scalatest.dstest.ScalatestAbstractDistageSpec.*
import izumi.distage.testkit.spec.*
import izumi.functional.bio.{Bifunctorize, Bifunctorized, IO2}
import izumi.fundamentals.platform.language.{SourceFilePosition, SourceFilePositionMaterializer}
import org.scalatest.Assertion
import org.scalatest.distage.{NameUtil, TestCancellation}
import org.scalatest.verbs.{CanVerb, MustVerb, ShouldVerb, StringVerbBlockRegistration}
import zio.ZIO

import scala.annotation.unused
import scala.language.implicitConversions

@org.scalatest.Finders(value = Array("org.scalatest.finders.WordSpecFinder"))
trait ScalatestAbstractDistageSpec[F[+_, +_]] extends AbstractDistageSpec[F] with ShouldVerb with MustVerb with CanVerb with DistageTestEnv with WithTestRegistration[F[Throwable, _]] {

  override protected def config: TestConfig = TestConfig.forSuite(this.getClass)

  final protected lazy val testEnv: TestEnvironment = makeTestEnv()
  protected def makeTestEnv(): TestEnvironment = loadEnvironment[F](config, tagBIO, defaultModulesBIO)

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

  trait For2[F[+_, +_]] extends ScalatestAbstractDistageSpec[F] {

    protected implicit def convertToWordSpecStringWrapperDS2(s: String): DSWordSpecStringWrapper2[F] = {
      new DSWordSpecStringWrapper2(context, distageSuiteName, distageSuiteId, Seq(s), this, testEnv)
    }
  }

  /** Monofunctor-flavoured shape: extends the bifunctor base [[ScalatestAbstractDistageSpec]] on
    * `Bifunctorized[F, +_, +_]` and exposes a string-to-test-wrapper conversion whose `in` DSL
    * accepts plain `F[A]` bodies, lifted to `Bifunctorized[F, Throwable, A]` via [[Bifunctorize]].
    */
  trait For1[F[_]] extends ScalatestAbstractDistageSpec[Bifunctorized[F, +_, +_]] {
    implicit def tagMonoIO: TagK[F]
    implicit def bifunctorize1: Bifunctorize[F]

    protected implicit def convertToWordSpecStringWrapperDS1(s: String): DSWordSpecStringWrapper1[F] = {
      new DSWordSpecStringWrapper1(context, distageSuiteName, distageSuiteId, Seq(s), this, testEnv)
    }
  }

  /** Identity special-case shape: extends the bifunctor base on
    * [[Bifunctorized.IdentityBifunctorized]] (the MiniBIO-carrier route, not the zero-cost
    * generic carrier) and exposes a string-to-test-wrapper conversion whose `in` DSL accepts
    * plain `A` / `Identity[A]` bodies, lifted via [[Bifunctorized.bifunctorizeIdentity]].
    */
  trait ForIdentity extends ScalatestAbstractDistageSpec[Bifunctorized.IdentityBifunctorized] {
    protected implicit def convertToWordSpecStringWrapperDSIdentity(s: String): DSWordSpecStringWrapperIdentity = {
      new DSWordSpecStringWrapperIdentity(context, distageSuiteName, distageSuiteId, Seq(s), this, testEnv)
    }
  }

  trait ForZIO extends ScalatestAbstractDistageSpec[ZIO[Any, +_, +_]] {
    protected implicit def convertToWordSpecStringWrapperDS3(s: String): DSWordSpecStringWrapperZIO = {
      new DSWordSpecStringWrapperZIO(context, distageSuiteName, distageSuiteId, Seq(s), this, testEnv)
    }
  }

  final case class SuiteContext(prefix: Seq[String]) extends AnyVal {
    def toName(name: Seq[String]): Seq[String] = prefix ++ name
  }

  open class DSWordSpecStringWrapper2[F[+_, +_]](
    context: Option[SuiteContext],
    suiteName: String,
    suiteId: SuiteId,
    testname: Seq[String],
    reg: TestRegistration[F[Throwable, _]],
    env: TestEnvironment,
  )(implicit override val tagBIO: TagKK[F],
  ) extends DISyntaxBIOBase[F]
    with DSWordSpecStringWrapperLowPriorityIdentityOverloads[F] {

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

  /** String-to-test-wrapper for the monofunctor `Spec1[F[_]]` shape. The `in` DSL accepts plain
    * `F[A]` bodies (or `Functoid[F[A]]` constructions) and lifts each body to
    * `Bifunctorized[F, Throwable, A]` via the [[Bifunctorize]] typeclass, then hands off to the
    * existing bifunctor [[DISyntaxBIOBase]] machinery through `takeBIO`.
    *
    * The lift call is captured at registration time (for the by-name overloads) or composed
    * into the Functoid pipeline (for the Functoid overloads) — in both cases the result is a
    * `Functoid[Bifunctorized[F, Any, Any]]` consumed by `takeBIO`.
    */
  open class DSWordSpecStringWrapper1[F[_]](
    context: Option[SuiteContext],
    suiteName: String,
    suiteId: SuiteId,
    testname: Seq[String],
    reg: TestRegistration[Bifunctorized[F, Throwable, _]],
    env: TestEnvironment,
  )(implicit override val tagBIO: TagKK[Bifunctorized[F, +_, +_]],
    @unused tagMonoIO: TagK[F],
    B: Bifunctorize[F],
  ) extends DISyntaxBIOBase[Bifunctorized[F, +_, +_]]
    with DSWordSpecStringWrapperLowPriorityIdentityOverloads[Bifunctorized[F, +_, +_]] {

    infix def in(function: Functoid[F[Unit]])(implicit pos: SourceFilePositionMaterializer): Unit = {
      takeBIO(function.map(B.bifunctorize(_).asInstanceOf[Bifunctorized[F, Any, Any]]), pos.get)
    }

    infix def in(function: Functoid[F[Assertion]])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit): Unit = {
      takeBIO(function.map(B.bifunctorize(_).asInstanceOf[Bifunctorized[F, Any, Any]]), pos.get)
    }

    infix def in(value: => F[Unit])(implicit pos: SourceFilePositionMaterializer): Unit = {
      takeBIO(() => B.bifunctorize(value).asInstanceOf[Bifunctorized[F, Any, Any]], pos.get)
    }

    infix def in(value: => F[Assertion])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit): Unit = {
      takeBIO(() => B.bifunctorize(value).asInstanceOf[Bifunctorized[F, Any, Any]], pos.get)
    }

    override protected def takeIO[A](fAsThrowable: Functoid[Bifunctorized[F, Throwable, A]], pos: SourceFilePosition): Unit = {
      val id = TestId(context.fold(testname)(_.toName(testname)), suiteId)
      reg.registerTest(fAsThrowable, env, pos, id, SuiteMeta(id.suite, suiteName, suiteId.suiteId))
    }
  }

  /** String-to-test-wrapper for [[izumi.distage.testkit.scalatest.SpecIdentity]]. The `in` DSL
    * accepts plain `A` / `Identity[A]` bodies and lifts each to `IdentityBifunctorized[Throwable, A]`
    * via [[Bifunctorized.bifunctorizeIdentity]] (the MiniBIO-carrier route). The lift suspends
    * the body in a `MiniBIO.Sync` thunk so synchronous Throwables thrown during evaluation are
    * routed into the typed error channel.
    *
    * The `DSWordSpecStringWrapperLowPriorityIdentityOverloads` low-priority `in` overloads already
    * cover `=> Unit` / `=> Assertion` bodies and lift through `takeAny`'s `F.pure` — but `takeAny`
    * does NOT suspend evaluation, so any synchronous Throwable in those bodies would be observed
    * at registration time, not as a test failure. The Identity wrappers below override that path
    * by using [[Bifunctorized.bifunctorizeIdentity]] (which DOES suspend) for bodies that compute
    * Unit/Assertion in the Identity effect.
    */
  open class DSWordSpecStringWrapperIdentity(
    context: Option[SuiteContext],
    suiteName: String,
    suiteId: SuiteId,
    testname: Seq[String],
    reg: TestRegistration[Bifunctorized.IdentityBifunctorized[Throwable, _]],
    env: TestEnvironment,
  )(implicit override val tagBIO: TagKK[Bifunctorized.IdentityBifunctorized],
  ) extends DISyntaxBIOBase[Bifunctorized.IdentityBifunctorized]
    with DSWordSpecStringWrapperLowPriorityIdentityOverloads[Bifunctorized.IdentityBifunctorized] {

    // High-priority `in` overloads using `Bifunctorized.bifunctorizeIdentity` (the MiniBIO `syncThrowable`
    // route): the body is suspended in a `MiniBIO.Sync` thunk so synchronous Throwables during evaluation
    // are routed into the typed Throwable error channel before the test runner observes them. Without
    // these the LowPriorityIdentityOverloads `takeAny` path (using `F.pure`) eagerly evaluates the body
    // and only the surrounding `F.syncThrowable` in `IndividualTestRunner` catches throws — functionally
    // equivalent for the test reporter but semantically distinct (per the M5 spec: SpecIdentity lifts
    // user bodies via `bifunctorizeIdentity`).
    infix def in(value: => Unit)(implicit pos: SourceFilePositionMaterializer): Unit = {
      takeBIO(
        () =>
          Bifunctorized.bifunctorizeIdentity(value).asInstanceOf[Bifunctorized.IdentityBifunctorized[Any, Any]],
        pos.get,
      )
    }

    infix def in(value: => Assertion)(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit): Unit = {
      takeBIO(
        () =>
          Bifunctorized.bifunctorizeIdentity(value).asInstanceOf[Bifunctorized.IdentityBifunctorized[Any, Any]],
        pos.get,
      )
    }

    override protected def takeIO[A](fAsThrowable: Functoid[Bifunctorized.IdentityBifunctorized[Throwable, A]], pos: SourceFilePosition): Unit = {
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
  )(implicit override val tagBIO: TagKK[ZIO[Any, +_, +_]],
  ) extends DISyntaxBIOBase[ZIO[Any, +_, +_]]
    with DSWordSpecStringWrapperLowPriorityIdentityOverloads[ZIO[Any, +_, +_]] {

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

  trait DSWordSpecStringWrapperLowPriorityIdentityOverloads[F[+_, +_]] extends DISyntaxBase[F] {

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
      takeFunIO[Nothing, IO2[F]](cancel, pos.get)
    }

    private def cancel[A](F: IO2[F]): F[Throwable, A] = {
      F.syncThrowable(cancelNow())
    }

    private def cancelNow(): Nothing = {
      TestCancellation.cancel(Some("test skipped!"), None, 1)
    }
  }

}
