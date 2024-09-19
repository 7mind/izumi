package izumi.distage.roles.test.fixtures

import distage.config.ConfigModuleDef
import distage.{LocatorRef, Tag}
import izumi.distage.config.codec.{DIConfigMeta, DIConfigReader}
import izumi.distage.config.model.ConfigDoc
import izumi.distage.model.definition.Axis
import izumi.distage.model.provisioning.IntegrationCheck
import izumi.distage.roles.test.fixtures.roles.TestRole00.SetElementOnlyCfg
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.language.Quirks.*

import scala.collection.mutable

object Fixture {
  trait Dummy

  trait SetElement
  final case class SetElement1(setElementOnlyCfg: SetElementOnlyCfg) extends SetElement

  trait GenericServiceConf {
    def genericField: Int
  }
  object GenericServiceConf {
    case class Impl(genericField: Int, addedField: Int) extends GenericServiceConf
    def module[Conf <: GenericServiceConf: Tag: DIConfigReader: DIConfigMeta](path: String): ConfigModuleDef = new ConfigModuleDef {
      makeConfig[Conf](path)
    }
  }

  case class TestServiceConf2(
    strval: String,
    map: Map[String, String],
    list: List[String],
  )

  @ConfigDoc("docstest: case class doc")
  case class TestServiceConf(
    @ConfigDoc("docstest: field doc") intval: Int,
    strval: String,
    overridenInt: Int,
    explicitInt: Int,
    systemPropInt: Int,
    systemPropList: List[Int],
    a: A,
  )

  @ConfigDoc("docstest: sealed trait doc")
  sealed trait A

  @ConfigDoc("docstest: A1 doc")
  case class A1(v1: Int) extends A
  @ConfigDoc("docstest: A2 doc")
  case class A2(v2: String) extends A

  case class TestValueConf(value: Int)

  class XXX_ResourceEffectsRecorder[F[_]] {
    private val startedCloseables: mutable.ArrayBuffer[AutoCloseable] = mutable.ArrayBuffer()
    private val closedCloseables: mutable.ArrayBuffer[AutoCloseable] = mutable.ArrayBuffer()
    private val checkedResources: mutable.ArrayBuffer[IntegrationCheck[F]] = mutable.ArrayBuffer()

    def onStart(c: AutoCloseable): Unit = this.synchronized(startedCloseables += c).discard()
    def onClose(c: AutoCloseable): Unit = this.synchronized(closedCloseables += c).discard()
    def onCheck(c: IntegrationCheck[F]): Unit = this.synchronized(checkedResources += c).discard()

    def getStartedCloseables(): Seq[AutoCloseable] = this.synchronized(startedCloseables.toList)
    def getClosedCloseables(): Seq[AutoCloseable] = this.synchronized(closedCloseables.toList)
    def getCheckedResources(): Seq[IntegrationCheck[F]] = this.synchronized(checkedResources.toList)
  }

  case class XXX_LocatorLeak(locatorRef: LocatorRef)

  trait TestResource[F[_]]

  trait ProbeResource[F[_]] extends TestResource[F] with AutoCloseable {
    def counter: XXX_ResourceEffectsRecorder[F]
    counter.onStart(this)

    override def close(): Unit = counter.onClose(this)

  }

  abstract class ProbeCheck[F[_]: QuasiIO] extends ProbeResource[F] with IntegrationCheck[F] {
    override def resourcesAvailable(): F[ResourceCheck] = QuasiIO[F].maybeSuspend {
      System.err.println(s"!!!: $this")
      counter.onCheck(this)
      ResourceCheck.Success()
    }
  }

  class IntegrationResource0[F[_]: QuasiIO](val closeable: IntegrationResource1[F], val counter: XXX_ResourceEffectsRecorder[F]) extends ProbeCheck[F]
  class IntegrationResource1[F[_]: QuasiIO](val roleComponent: JustResource1[F], val counter: XXX_ResourceEffectsRecorder[F]) extends ProbeCheck[F]

  case class ProbeResource0[F[_]: QuasiIO](roleComponent: JustResource3[F], counter: XXX_ResourceEffectsRecorder[F]) extends ProbeResource[F]

  case class JustResource1[F[_]: QuasiIO](roleComponent: JustResource2[F], counter: XXX_ResourceEffectsRecorder[F]) extends TestResource[F]
  case class JustResource2[F[_]: QuasiIO](closeable: ProbeResource0[F], counter: XXX_ResourceEffectsRecorder[F]) extends TestResource[F]
  case class JustResource3[F[_]: QuasiIO](counter: XXX_ResourceEffectsRecorder[F]) extends TestResource[F]

  trait AxisComponent
  object AxisComponentIncorrect extends AxisComponent
  object AxisComponentCorrect extends AxisComponent

  object AxisComponentAxis extends Axis {
    case object Incorrect extends AxisChoiceDef
    case object Correct extends AxisChoiceDef
  }

  case class ListConf(ints: List[Int])

}
