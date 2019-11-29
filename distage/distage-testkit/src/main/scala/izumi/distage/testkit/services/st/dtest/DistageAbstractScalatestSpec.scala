package izumi.distage.testkit.services.st.dtest

import distage.{BootstrapModule, DIKey, Module, Tag, TagK, TagKK}
import izumi.distage.model.definition.Axis.AxisValue
import izumi.distage.model.definition.{AxisBase, StandardAxis}
import izumi.distage.model.monadic.DIEffect
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.testkit.services.dstest.DistageTestRunner.{DistageTest, TestId, TestMeta}
import izumi.distage.testkit.services.dstest._
import izumi.distage.testkit.services.st.dtest.DistageAbstractScalatestSpec._
import izumi.distage.testkit.services.{DISyntaxBIOBase, DISyntaxBase}
import izumi.fundamentals.platform.language.{CodePosition, CodePositionMaterializer, Quirks}
import izumi.logstage.api.{IzLogger, Log}
import org.scalactic.source
import org.scalatest.TestCancellation

import scala.language.implicitConversions

trait WithSingletonTestRegistration[F[_]] extends AbstractDistageSpec[F] {
  override def registerTest(function: ProviderMagnet[F[_]], env: TestEnvironment, pos: CodePosition, id: TestId): Unit = {
    DistageTestsRegistrySingleton.register[F](DistageTest(function, env, TestMeta(id, pos, System.identityHashCode(function).toLong)))
  }
}

/**
  * @param pluginPackages  If `None`, scans recursively scans packages of the test class itself
  *
  * @param activation      Chosen configurations
  *
  * @param memoizedKeys    Setting `memoizedKeys` create a distinct memoization group
  *                        the exact same `memoizedKeys`
  *
  * @param moduleOverrides Using overrides will create a distinct memoization group
  *                        for this test, i.e. objects will be memoized only between
  *                        tests with the exact same overrides
  */
final case class TestConfig(
                             pluginPackages: Option[Seq[String]] = None
                           , activation: Map[AxisBase, AxisValue] = StandardAxis.testProdActivation
                           , memoizedKeys: Set[DIKey] = Set.empty
                           , moduleOverrides: Module = Module.empty
                           , bootstrapOverrides: BootstrapModule = BootstrapModule.empty
                           )

trait DistageAbstractScalatestSpec[F[_]] extends ScalatestWords with WithSingletonTestRegistration[F] {
  this: AbstractDistageSpec[F] =>

  private[this] lazy val testEnvProvider: TestEnvironmentProvider = makeTestEnvProvider()
  final protected lazy val testEnv: TestEnvironment = makeTestEnv()

  protected def makeTestEnvProvider(): TestEnvironmentProvider = {
    val c = config
    new TestEnvironmentProviderImpl(
      this.getClass,
      c.activation,
      c.memoizedKeys,
      c.bootstrapOverrides,
      c.moduleOverrides,
      c.pluginPackages,
    )
  }
  protected def makeTestEnv(): TestEnvironment = {
    testEnvProvider.loadEnvironment(logger)
  }

  protected def distageSuiteName: String = getSimpleNameOfAnObjectsClass(this)
  protected def distageSuiteId: String = this.getClass.getName

  protected def config: TestConfig = TestConfig()

  protected def logger: IzLogger = IzLogger(Log.Level.Debug)("phase" -> "test")

  //
  protected var context: Option[SuiteContext] = None

  override def registerBranch(description: String, childPrefix: Option[String], verb: String, methodName: String, stackDepth: Int, adjustment: Int, pos: source.Position, fun: () => Unit): Unit = {
    Quirks.discard(childPrefix, methodName, stackDepth, adjustment, pos)
    this.context = Some(SuiteContext(description, verb))
    fun()
    this.context = None
  }
  //

  protected implicit def convertToWordSpecStringWrapperDS(s: String): DSWordSpecStringWrapper[F] = {
    new DSWordSpecStringWrapper(context, distageSuiteName, distageSuiteId, s, this, testEnv)
  }
}

object DistageAbstractScalatestSpec {
  final case class SuiteContext(left: String, verb: String) {
    def toName(name: String): String = {
      Seq(left, verb, name).mkString(" ")
    }
  }

  class DSWordSpecStringWrapper[F[_]](
                                       context: Option[SuiteContext],
                                       suiteName: String,
                                       suiteId: String,
                                       testname: String,
                                       reg: TestRegistration[F],
                                       env: TestEnvironment,
                                     )(
                                       implicit override val tagMonoIO: TagK[F]
                                     ) extends DISyntaxBase[F] {
    override protected def takeIO(function: ProviderMagnet[F[_]], pos: CodePosition): Unit = {
      val id = TestId(
        context.map(_.toName(testname)).getOrElse(testname),
        suiteName,
        suiteId,
        suiteName,
      )
      reg.registerTest(function, env, pos, id)
    }

    def in(function: ProviderMagnet[F[_]])(implicit pos: CodePositionMaterializer): Unit = {
      takeIO(function, pos.get)
    }

    def in[T: Tag](function: T => F[_])(implicit pos: CodePositionMaterializer): Unit = {
      takeFunIO(function, pos.get)
    }

    def in(function: ProviderMagnet[_])(implicit pos: CodePositionMaterializer, dummyImplicit: DummyImplicit): Unit = {
      takeAny(function, pos.get)
    }

    def in[T: Tag](function: T => _)(implicit pos: CodePositionMaterializer, dummyImplicit: DummyImplicit): Unit = {
      takeFunAny(function, pos.get)
    }

    def skip(function: ProviderMagnet[F[_]])(implicit pos: CodePositionMaterializer): Unit = {
      Quirks.discard(function)
      takeIO((eff: DIEffect[F]) => cancel(eff), pos.get)
    }

    final def skip[T: Tag](function: T => F[_])(implicit pos: CodePositionMaterializer): Unit = {
      Quirks.discard(function)
      takeIO((eff: DIEffect[F]) => cancel(eff), pos.get)
    }

    def skip(function: ProviderMagnet[_])(implicit pos: CodePositionMaterializer, dummyImplicit: DummyImplicit): Unit = {
      Quirks.discard(function)
      takeIO((eff: DIEffect[F]) => cancel(eff), pos.get)
    }

    def skip[T: Tag](function: T => _)(implicit pos: CodePositionMaterializer, dummyImplicit: DummyImplicit): Unit = {
      Quirks.discard(function)
      takeFunAny((_: T) => cancelNow(), pos.get)
    }

    private def cancel(eff: DIEffect[F]) = {
      eff.maybeSuspend(cancelNow())
    }

    private def cancelNow() = {
      TestCancellation.cancel(Some("test skipped!"), None, 1)
    }
  }

  class DSWordSpecStringWrapper2[F[+ _, + _]](
                                               context: Option[SuiteContext],
                                               suiteName: String,
                                               suiteId: String,
                                               testname: String,
                                               reg: TestRegistration[F[Throwable, ?]],
                                               env: TestEnvironment,
                                             )(
                                               implicit override val tagBIO: TagKK[F],
                                               val tagK: TagK[F[Throwable, *]],
                                             ) extends DISyntaxBIOBase[F] {

    override protected def takeAs1(fAsThrowable: ProviderMagnet[F[Throwable, _]], pos: CodePosition): Unit = {
      val id = TestId(
        context.map(_.toName(testname)).getOrElse(testname),
        suiteName,
        suiteId,
        suiteName,
      )
      reg.registerTest(fAsThrowable, env, pos, id)
    }

    def in(function: ProviderMagnet[F[_, _]])(implicit pos: CodePositionMaterializer): Unit = {
      take2(function, pos.get)
    }


    final def in[T: Tag](function: T => F[_, _])(implicit pos: CodePositionMaterializer): Unit = {
      take2(function, pos.get)
    }

    def skip(function: ProviderMagnet[F[_, _]])(implicit pos: CodePositionMaterializer): Unit = {
      Quirks.discard(function)
      takeAs1((eff: DIEffect[F[Throwable, *]]) => cancel(eff), pos.get)
    }

    final def skip[T: Tag](function: T => F[_, _])(implicit pos: CodePositionMaterializer): Unit = {
      Quirks.discard(function)
      takeAs1((eff: DIEffect[F[Throwable, *]]) => cancel(eff), pos.get)
    }

    private def cancel(eff: DIEffect[F[Throwable, *]]) = {
      eff.maybeSuspend(cancelNow())
    }

    private def cancelNow() = {
      TestCancellation.cancel(Some("test skipped!"), None, 1)
    }
  }

//  abstract class StringVerbBlockRegistration {
//    def apply(string: String, verb: String, pos: source.Position, block: () => Unit): Unit
//  }

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
