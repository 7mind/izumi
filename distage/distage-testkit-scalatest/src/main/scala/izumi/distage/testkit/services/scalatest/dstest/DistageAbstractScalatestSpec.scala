package izumi.distage.testkit.services.scalatest.dstest

import distage.{TagK, TagKK}
import izumi.distage.constructors.ZEnvConstructor
import izumi.distage.model.providers.Functoid
import izumi.distage.testkit.model.{DistageTest, SuiteId, SuiteMeta, TestConfig, TestEnvironment, TestId, TestMeta}
import izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec.*
import izumi.distage.testkit.spec.{AbstractDistageSpec, DISyntaxBIOBase, DISyntaxBase, DistageTestEnv, TestRegistration}
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.language.{SourceFilePosition, SourceFilePositionMaterializer}
import org.scalactic.source
import org.scalatest.Assertion
import org.scalatest.distage.TestCancellation
import org.scalatest.verbs.{CanVerb, MustVerb, ShouldVerb, StringVerbBlockRegistration}
import zio.ZIO

import scala.annotation.unused

trait WithSingletonTestRegistration[F[_]] extends AbstractDistageSpec[F] {
  private[this] lazy val firstRegistration: Boolean = DistageTestsRegistrySingleton.registerSuite[F](this.getClass.getName)

  override def registerTest[A](function: Functoid[F[A]], env: TestEnvironment, pos: SourceFilePosition, id: TestId, meta: SuiteMeta): Unit = {
    if (firstRegistration) {
      val test = DistageTest(function.asInstanceOf[Functoid[F[Any]]], env, TestMeta(id, pos, System.identityHashCode(function).toLong), meta)
      DistageTestsRegistrySingleton.register[F](test)
    }
  }
}

@org.scalatest.Finders(value = Array("org.scalatest.finders.WordSpecFinder"))
trait DistageAbstractScalatestSpec[F[_]] extends ShouldVerb with MustVerb with CanVerb with DistageTestEnv with WithSingletonTestRegistration[F] {
  this: AbstractDistageSpec[F] =>

  override protected def config: TestConfig = TestConfig.forSuite(this.getClass)

  final protected[this] lazy val testEnv: TestEnvironment = makeTestEnv()
  protected[this] def makeTestEnv(): TestEnvironment = loadEnvironment(config)

  protected[this] def distageSuiteName: String = getSimpleNameOfAnObjectsClass(this)

  protected[this] def distageSuiteId: SuiteId = SuiteId(this.getClass.getName)

  protected implicit val subjectRegistrationFunction1: StringVerbBlockRegistration = new StringVerbBlockRegistration {
    override def apply(left: String, verb: String, @unused pos: source.Position, f: () => Unit): Unit = {
      registerBranch(left, verb, f)
    }
  }

  protected[this] def registerBranch(description: String, verb: String, fun: () => Unit): Unit = {
    context = Some(SuiteContext(Seq(description, verb)))
    fun()
    context = None
  }

  private[distage] var context: Option[SuiteContext] = None
}

object DistageAbstractScalatestSpec {
  final case class SuiteContext(prefix: Seq[String]) {
    def toName(name: Seq[String]): Seq[String] = {
      prefix ++ name
    }
  }

  trait LowPriorityIdentityOverloads[F[_]] extends DISyntaxBase[F] {
    def in(function: Functoid[Unit])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit, d2: DummyImplicit): Unit = {
      takeAny(function, pos.get)
    }

    def in(function: Functoid[Assertion])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit, d2: DummyImplicit, d3: DummyImplicit): Unit = {
      takeAny(function, pos.get)
    }

    def in(value: => Unit)(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit, d2: DummyImplicit): Unit = {
      takeAny(() => value, pos.get)
    }

    def in(value: => Assertion)(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit, d2: DummyImplicit, d3: DummyImplicit): Unit = {
      takeAny(() => value, pos.get)
    }

    def skip(@unused value: => Any)(implicit pos: SourceFilePositionMaterializer): Unit = {
      takeFunIO(cancel, pos.get)
    }

    private def cancel[A](F: QuasiIO[F]): F[A] = {
      F.maybeSuspend(cancelNow())
    }

    private def cancelNow(): Nothing = {
      TestCancellation.cancel(Some("test skipped!"), None, 1)
    }
  }

  class DSWordSpecStringWrapper[F[_]](
    context: Option[SuiteContext],
    suiteName: String,
    suiteId: SuiteId,
    testname: Seq[String],
    reg: TestRegistration[F],
    env: TestEnvironment,
  )(implicit override val tagMonoIO: TagK[F]
  ) extends DISyntaxBase[F]
    with LowPriorityIdentityOverloads[F] {

    def in(function: Functoid[F[Unit]])(implicit pos: SourceFilePositionMaterializer): Unit = {
      takeIO(function, pos.get)
    }

    def in(function: Functoid[F[Assertion]])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit): Unit = {
      takeIO(function, pos.get)
    }

    def in(value: => F[Unit])(implicit pos: SourceFilePositionMaterializer): Unit = {
      takeIO(() => value, pos.get)
    }

    def in(value: => F[Assertion])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit): Unit = {
      takeIO(() => value, pos.get)
    }

    override protected def takeIO[A](function: Functoid[F[A]], pos: SourceFilePosition): Unit = {
      val id = TestId(
        context.fold(testname)(_.toName(testname)),
        suiteId,
      )
      reg.registerTest(function, env, pos, id, SuiteMeta(id.suite, suiteName, suiteId.suiteId))
    }
  }

  class DSWordSpecStringWrapper2[F[+_, +_]](
    context: Option[SuiteContext],
    suiteName: String,
    suiteId: SuiteId,
    testname: Seq[String],
    reg: TestRegistration[F[Throwable, _]],
    env: TestEnvironment,
  )(implicit override val tagBIO: TagKK[F],
    override val tagMonoIO: TagK[F[Throwable, _]],
  ) extends DISyntaxBIOBase[F]
    with LowPriorityIdentityOverloads[F[Throwable, _]] {

    def in(function: Functoid[F[Any, Unit]])(implicit pos: SourceFilePositionMaterializer): Unit = {
      takeBIO(function.asInstanceOf[Functoid[F[Any, Any]]], pos.get)
    }

    def in(function: Functoid[F[Any, Assertion]])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit): Unit = {
      takeBIO(function.asInstanceOf[Functoid[F[Any, Any]]], pos.get)
    }

    def in(value: => F[Any, Unit])(implicit pos: SourceFilePositionMaterializer): Unit = {
      takeBIO(() => value.asInstanceOf[F[Any, Any]], pos.get)
    }

    def in(value: => F[Any, Assertion])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit): Unit = {
      takeBIO(() => value.asInstanceOf[F[Any, Any]], pos.get)
    }

    override protected def takeIO[A](fAsThrowable: Functoid[F[Throwable, A]], pos: SourceFilePosition): Unit = {
      val id = TestId(
        context.fold(testname)(_.toName(testname)),
        suiteId,
      )
      reg.registerTest(fAsThrowable, env, pos, id, SuiteMeta(id.suite, suiteName, suiteId.suiteId))
    }
  }

  class DSWordSpecStringWrapperZIO(
    context: Option[SuiteContext],
    suiteName: String,
    suiteId: SuiteId,
    testname: Seq[String],
    reg: TestRegistration[ZIO[Any, Throwable, _]],
    env: TestEnvironment,
  )(implicit override val tagBIO: TagKK[ZIO[Any, _, _]],
    override val tagMonoIO: TagK[ZIO[Any, Throwable, _]],
  ) extends DISyntaxBIOBase[ZIO[Any, +_, +_]]
    with LowPriorityIdentityOverloads[ZIO[Any, Throwable, _]] {

    def in[R: ZEnvConstructor](function: Functoid[ZIO[R, Any, Unit]])(implicit pos: SourceFilePositionMaterializer): Unit = {
      takeBIO(
        function.map2(ZEnvConstructor[R]) {
          case (eff, r) => eff.provideEnvironment(r)
        },
        pos.get,
      )
    }

    def in[R: ZEnvConstructor](function: Functoid[ZIO[R, Any, Assertion]])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit): Unit = {
      takeBIO(
        function.map2(ZEnvConstructor[R]) {
          case (eff, r) => eff.provideEnvironment(r)
        },
        pos.get,
      )
    }

    def in[R: ZEnvConstructor](value: => ZIO[R, Any, Unit])(implicit pos: SourceFilePositionMaterializer): Unit = {
      takeBIO(ZEnvConstructor[R].map(value.provideEnvironment(_)), pos.get)
    }

    def in[R: ZEnvConstructor](value: => ZIO[R, Any, Assertion])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit): Unit = {
      takeBIO(ZEnvConstructor[R].map(value.provideEnvironment(_)), pos.get)
    }

    def in(function: Functoid[ZIO[Any, Any, Unit]])(implicit pos: SourceFilePositionMaterializer): Unit = {
      takeBIO(function, pos.get)
    }

    def in(function: Functoid[ZIO[Any, Any, Assertion]])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit): Unit = {
      takeBIO(function, pos.get)
    }

    def in(value: => ZIO[Any, Any, Unit])(implicit pos: SourceFilePositionMaterializer): Unit = {
      takeBIO(() => value, pos.get)
    }

    def in(value: => ZIO[Any, Any, Assertion])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit): Unit = {
      takeBIO(() => value, pos.get)
    }

    override protected def takeIO[A](fAsThrowable: Functoid[ZIO[Any, Throwable, A]], pos: SourceFilePosition): Unit = {
      val id = TestId(
        context.fold(testname)(_.toName(testname)),
        suiteId,
      )
      reg.registerTest(fAsThrowable, env, pos, id, SuiteMeta(id.suite, suiteName, suiteId.suiteId))
    }
  }

  def getSimpleNameOfAnObjectsClass(o: AnyRef): String = stripDollars(parseSimpleName(o.getClass.getName))

  // [bv: this is a good example of the expression type refactor. I moved this from SuiteClassNameListCellRenderer]
  // this will be needed by the GUI classes, etc.
  def parseSimpleName(fullyQualifiedName: String): String = {

    val dotPos = fullyQualifiedName.lastIndexOf('.')

    // [bv: need to check the dotPos != fullyQualifiedName.length]
    if (dotPos != -1 && dotPos != fullyQualifiedName.length)
      fullyQualifiedName.substring(dotPos + 1)
    else
      fullyQualifiedName
  }

  // This attempts to strip dollar signs that happen when using the interpreter. It is quite fragile
  // and already broke once. In the early days, all funky dollar sign encrusted names coming out of
  // the interpreter started with "line". Now they don't, but in both cases they seemed to have at
  // least one "$iw$" in them. So now I leave the string alone unless I see a "$iw$" in it. Worst case
  // is sometimes people will get ugly strings coming out of the interpreter. -bv April 3, 2012
  def stripDollars(s: String): String = {
    val lastDollarIndex = s.lastIndexOf('$')
    if (lastDollarIndex < s.length - 1)
      if (lastDollarIndex == -1 || !s.contains("$iw$")) s else s.substring(lastDollarIndex + 1)
    else {
      // The last char is a dollar sign
      val lastNonDollarChar = s.reverse.find(_ != '$')
      lastNonDollarChar match {
        case None => s
        case Some(c) => {
          val lastNonDollarIndex = s.lastIndexOf(c.toInt)
          if (lastNonDollarIndex == -1) s
          else stripDollars(s.substring(0, lastNonDollarIndex + 1))
        }
      }
    }
  }
}
