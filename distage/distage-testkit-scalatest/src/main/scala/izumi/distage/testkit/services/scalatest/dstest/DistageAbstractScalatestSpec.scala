package izumi.distage.testkit.services.scalatest.dstest

import distage.{TagK, TagKK}
import izumi.distage.constructors.HasConstructor
import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.providers.Functoid
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.services.dstest.DistageTestRunner.{DistageTest, TestId, TestMeta}
import izumi.distage.testkit.services.dstest._
import izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec._
import izumi.distage.testkit.services.{DISyntaxBIOBase, DISyntaxBase}
import izumi.functional.bio.Local3
import izumi.fundamentals.platform.language.{SourceFilePosition, SourceFilePositionMaterializer, unused}
import izumi.reflect.TagK3
import org.scalactic.source
import org.scalatest.Assertion
import org.scalatest.distage.TestCancellation
import org.scalatest.verbs.{CanVerb, MustVerb, ShouldVerb, StringVerbBlockRegistration}

trait WithSingletonTestRegistration[F[_]] extends AbstractDistageSpec[F] {
  private[this] lazy val firstRegistration: Boolean = DistageTestsRegistrySingleton.registerSuite[F](this.getClass.getName)

  override def registerTest(function: Functoid[F[_]], env: TestEnvironment, pos: SourceFilePosition, id: TestId): Unit = {
    if (firstRegistration) {
      DistageTestsRegistrySingleton.register[F](DistageTest(function, env, TestMeta(id, pos, System.identityHashCode(function).toLong)))
    }
  }
}

@org.scalatest.Finders(value = Array("org.scalatest.finders.WordSpecFinder"))
trait DistageAbstractScalatestSpec[F[_]] extends ShouldVerb with MustVerb with CanVerb with DistageTestEnv with WithSingletonTestRegistration[F] {
  this: AbstractDistageSpec[F] =>

  protected def config: TestConfig = TestConfig.forSuite(this.getClass)

  final protected[this] lazy val testEnv: TestEnvironment = makeTestEnv()
  protected[this] def makeTestEnv(): TestEnvironment = loadEnvironment(config)

  protected[this] def distageSuiteName: String = getSimpleNameOfAnObjectsClass(this)
  protected[this] def distageSuiteId: String = this.getClass.getName

  protected implicit val subjectRegistrationFunction1: StringVerbBlockRegistration = new StringVerbBlockRegistration {
    override def apply(left: String, verb: String, @unused pos: source.Position, f: () => Unit): Unit = {
      registerBranch(left, verb, f)
    }
  }

  protected[this] def registerBranch(description: String, verb: String, fun: () => Unit): Unit = {
    context = Some(SuiteContext(description, verb))
    fun()
    context = None
  }

  private[distage] var context: Option[SuiteContext] = None
}

object DistageAbstractScalatestSpec {
  final case class SuiteContext(left: String, verb: String) {
    def toName(name: String): String = {
      Seq(left, verb, name).mkString(" ")
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

    private def cancel(F: QuasiIO[F]): F[Nothing] = {
      F.maybeSuspend(cancelNow())
    }

    private def cancelNow(): Nothing = {
      TestCancellation.cancel(Some("test skipped!"), None, 1)
    }
  }

  class DSWordSpecStringWrapper[F[_]](
    context: Option[SuiteContext],
    suiteName: String,
    suiteId: String,
    testname: String,
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

    override protected def takeIO(function: Functoid[F[_]], pos: SourceFilePosition): Unit = {
      val id = TestId(
        context.fold(testname)(_.toName(testname)),
        suiteName,
        suiteId,
        suiteId,
      )
      reg.registerTest(function, env, pos, id)
    }
  }

  class DSWordSpecStringWrapper2[F[+_, +_]](
    context: Option[SuiteContext],
    suiteName: String,
    suiteId: String,
    testname: String,
    reg: TestRegistration[F[Throwable, `?`]],
    env: TestEnvironment,
  )(implicit override val tagBIO: TagKK[F],
    implicit override val tagMonoIO: TagK[F[Throwable, `?`]],
  ) extends DISyntaxBIOBase[F]
    with LowPriorityIdentityOverloads[F[Throwable, `?`]] {

    def in(function: Functoid[F[_, Unit]])(implicit pos: SourceFilePositionMaterializer): Unit = {
      takeBIO(function, pos.get)
    }

    def in(function: Functoid[F[_, Assertion]])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit): Unit = {
      takeBIO(function, pos.get)
    }

    def in(value: => F[_, Unit])(implicit pos: SourceFilePositionMaterializer): Unit = {
      takeBIO(() => value, pos.get)
    }

    def in(value: => F[_, Assertion])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit): Unit = {
      takeBIO(() => value, pos.get)
    }

    override protected def takeIO(fAsThrowable: Functoid[F[Throwable, _]], pos: SourceFilePosition): Unit = {
      val id = TestId(
        context.fold(testname)(_.toName(testname)),
        suiteName,
        suiteId,
        suiteId,
      )
      reg.registerTest(fAsThrowable, env, pos, id)
    }
  }

  class DSWordSpecStringWrapper3[F[-_, +_, +_]: TagK3](
    context: Option[SuiteContext],
    suiteName: String,
    suiteId: String,
    testname: String,
    reg: TestRegistration[F[Any, Throwable, `?`]],
    env: TestEnvironment,
  )(implicit override val tagBIO: TagKK[F[Any, `?`, `?`]],
    implicit override val tagMonoIO: TagK[F[Any, Throwable, `?`]],
  ) extends DISyntaxBIOBase[F[Any, +?, +?]]
    with LowPriorityIdentityOverloads[F[Any, Throwable, `?`]] {

    def in[R: HasConstructor](function: Functoid[F[R, _, Unit]])(implicit pos: SourceFilePositionMaterializer): Unit = {
      takeBIO(
        function.zip(HasConstructor[R]).map2(Functoid.identity[Local3[F]]) {
          case ((eff, r), f) => f.provide(eff)(r)
        },
        pos.get,
      )
    }

    def in[R: HasConstructor](function: Functoid[F[R, _, Assertion]])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit): Unit = {
      takeBIO(
        function.zip(HasConstructor[R]).map2(Functoid.identity[Local3[F]]) {
          case ((eff, r), f) => f.provide(eff)(r)
        },
        pos.get,
      )
    }

    def in[R: HasConstructor](value: => F[R, _, Unit])(implicit pos: SourceFilePositionMaterializer): Unit = {
      takeBIO(Functoid.identity[Local3[F]].map2(HasConstructor[R])(_.provide(value)(_)), pos.get)
    }

    def in[R: HasConstructor](value: => F[R, _, Assertion])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit): Unit = {
      takeBIO(Functoid.identity[Local3[F]].map2(HasConstructor[R])(_.provide(value)(_)), pos.get)
    }

    def in(function: Functoid[F[Any, _, Unit]])(implicit pos: SourceFilePositionMaterializer): Unit = {
      takeBIO(function, pos.get)
    }

    def in(function: Functoid[F[Any, _, Assertion]])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit): Unit = {
      takeBIO(function, pos.get)
    }

    def in(value: => F[Any, _, Unit])(implicit pos: SourceFilePositionMaterializer): Unit = {
      takeBIO(() => value, pos.get)
    }

    def in(value: => F[Any, _, Assertion])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit): Unit = {
      takeBIO(() => value, pos.get)
    }

    override protected def takeIO(fAsThrowable: Functoid[F[Any, Throwable, _]], pos: SourceFilePosition): Unit = {
      val id = TestId(
        context.fold(testname)(_.toName(testname)),
        suiteName,
        suiteId,
        suiteId,
      )
      reg.registerTest(fAsThrowable, env, pos, id)
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
