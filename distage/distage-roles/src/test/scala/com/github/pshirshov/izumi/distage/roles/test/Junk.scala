package com.github.pshirshov.izumi.distage.roles.test

import com.github.pshirshov.izumi.distage.roles.{IntegrationCheck, RoleComponent}
import com.github.pshirshov.izumi.fundamentals.platform.integration.ResourceCheck

import scala.collection.mutable

object Junk {
  trait Dummy


  case class TestServiceConf(
                              intval: Int
                              , strval: String
                              , overridenInt: Int
                              , systemPropInt: Int
                              , systemPropList: List[Int]
                            )



  class InitCounter {
    val startedRoleComponents: mutable.ArrayBuffer[RoleComponent] = mutable.ArrayBuffer()
    val closedRoleComponents: mutable.ArrayBuffer[RoleComponent] = mutable.ArrayBuffer()
    val startedCloseables: mutable.ArrayBuffer[AutoCloseable] = mutable.ArrayBuffer()
    val closedCloseables: mutable.ArrayBuffer[AutoCloseable] = mutable.ArrayBuffer()
    val checkedResources: mutable.ArrayBuffer[IntegrationCheck] = mutable.ArrayBuffer()
  }

  trait Resource

  class Resource1(val closeable: Resource2, counter: InitCounter) extends Resource with AutoCloseable with IntegrationCheck {
    counter.startedCloseables += this

    override def close(): Unit = counter.closedCloseables += this

    override def resourcesAvailable(): ResourceCheck = {
      counter.checkedResources += this
      ResourceCheck.Success()
    }
  }

  class Resource2(val roleComponent: Resource3, counter: InitCounter) extends Resource with AutoCloseable with IntegrationCheck {
    counter.startedCloseables += this

    override def close(): Unit = counter.closedCloseables += this

    override def resourcesAvailable(): ResourceCheck = {
      counter.checkedResources += this
      ResourceCheck.Success()
    }
  }

  class Resource3(val roleComponent: Resource4, counter: InitCounter) extends Resource with RoleComponent {
    override def start(): Unit = counter.startedRoleComponents += this

    override def stop(): Unit = counter.closedRoleComponents += this
  }

  class Resource4(val closeable: Resource5, counter: InitCounter) extends Resource with RoleComponent {
    override def start(): Unit = counter.startedRoleComponents += this

    override def stop(): Unit = counter.closedRoleComponents += this
  }

  class Resource5(val roleComponent: Resource6, counter: InitCounter) extends Resource with AutoCloseable {
    counter.startedCloseables += this

    override def close(): Unit = counter.closedCloseables += this
  }

  class Resource6(counter: InitCounter) extends Resource with RoleComponent {
    override def start(): Unit = counter.startedRoleComponents += this

    override def stop(): Unit = counter.closedRoleComponents += this
  }

}
