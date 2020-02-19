package izumi.distage.roles.test.fixtures

import distage.LocatorRef
import izumi.distage.framework.model.IntegrationCheck
import izumi.distage.model.definition.Axis
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.language.Quirks._

import scala.collection.mutable

object Fixture {
  trait Dummy

  case class TestServiceConf2(
                               strval: String,
                             )

  case class TestServiceConf(
                              intval: Int,
                              strval: String,
                              overridenInt: Int,
                              systemPropInt: Int,
                              systemPropList: List[Int],
                            )

  class XXX_ResourceEffectsRecorder {
    private val startedCloseables: mutable.ArrayBuffer[AutoCloseable] = mutable.ArrayBuffer()
    private val closedCloseables: mutable.ArrayBuffer[AutoCloseable] = mutable.ArrayBuffer()
    private val checkedResources: mutable.ArrayBuffer[IntegrationCheck] = mutable.ArrayBuffer()

    def onStart(c: AutoCloseable): Unit = this.synchronized(startedCloseables += c).discard()
    def onClose(c: AutoCloseable): Unit = this.synchronized(closedCloseables += c).discard()
    def onCheck(c: IntegrationCheck): Unit = this.synchronized(checkedResources += c).discard()

    def getStartedCloseables(): Seq[AutoCloseable] = this.synchronized(startedCloseables.toList)
    def getClosedCloseables(): Seq[AutoCloseable] = this.synchronized(closedCloseables.toList)
    def getCheckedResources(): Seq[IntegrationCheck] = this.synchronized(checkedResources.toList)
  }

  case class XXX_LocatorLeak(locatorRef: LocatorRef)

  trait TestResource

  trait ProbeResource extends TestResource with AutoCloseable {
    def counter: XXX_ResourceEffectsRecorder
    counter.onStart(this)

    override def close(): Unit = counter.onClose(this)


  }

  trait ProbeCheck extends ProbeResource with IntegrationCheck {
    override def resourcesAvailable(): ResourceCheck = {
      counter.onCheck(this)
      ResourceCheck.Success()
    }
  }

  class IntegrationResource0(val closeable: IntegrationResource1, val counter: XXX_ResourceEffectsRecorder) extends ProbeCheck
  class IntegrationResource1(val roleComponent: JustResource1, val counter: XXX_ResourceEffectsRecorder) extends ProbeCheck

  case class ProbeResource0(roleComponent: JustResource3, counter: XXX_ResourceEffectsRecorder) extends ProbeResource

  case class JustResource1(roleComponent: JustResource2, counter: XXX_ResourceEffectsRecorder) extends TestResource
  case class JustResource2(closeable: ProbeResource0, counter: XXX_ResourceEffectsRecorder) extends TestResource
  case class JustResource3(counter: XXX_ResourceEffectsRecorder) extends TestResource

  trait AxisComponent
  object AxisComponentIncorrect extends AxisComponent
  object AxisComponentCorrect extends AxisComponent

  object AxisComponentAxis extends Axis {
    case object Incorrect extends AxisValueDef
    case object Correct extends AxisValueDef
  }

  case class ListConf(ints: List[Int])

}
