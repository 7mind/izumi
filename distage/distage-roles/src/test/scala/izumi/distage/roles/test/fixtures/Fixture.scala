package izumi.distage.roles.test.fixtures

import izumi.distage.roles.model.IntegrationCheck
import izumi.fundamentals.platform.integration.ResourceCheck

import scala.collection.mutable

object Fixture {
  trait Dummy

  case class TestServiceConf(
    intval: Int,
    strval: String,
    overridenInt: Int,
    systemPropInt: Int,
    systemPropList: List[Int],
  )

  class InitCounter {
    val startedCloseables: mutable.ArrayBuffer[AutoCloseable] = mutable.ArrayBuffer()
    val closedCloseables: mutable.ArrayBuffer[AutoCloseable] = mutable.ArrayBuffer()
    val checkedResources: mutable.ArrayBuffer[IntegrationCheck] = mutable.ArrayBuffer()
  }

  trait Resource0

  class Resource1(val closeable: Resource2, counter: InitCounter) extends Resource0 with AutoCloseable with IntegrationCheck {
    counter.startedCloseables += this

    override def close(): Unit = counter.closedCloseables += this

    override def resourcesAvailable(): ResourceCheck = {
      counter.checkedResources += this
      ResourceCheck.Success()
    }
  }

  class Resource2(val roleComponent: Resource3, counter: InitCounter) extends Resource0 with AutoCloseable with IntegrationCheck {
    counter.startedCloseables += this

    override def close(): Unit = counter.closedCloseables += this

    override def resourcesAvailable(): ResourceCheck = {
      counter.checkedResources += this
      ResourceCheck.Success()
    }
  }

  case class Resource3(roleComponent: Resource4, counter: InitCounter) extends Resource0

  case class Resource4(closeable: Resource5, counter: InitCounter) extends Resource0

  case class Resource5(roleComponent: Resource6, counter: InitCounter) extends Resource0 with AutoCloseable {
    counter.startedCloseables += this

    override def close(): Unit = counter.closedCloseables += this
  }

  case class Resource6(counter: InitCounter) extends Resource0

}
