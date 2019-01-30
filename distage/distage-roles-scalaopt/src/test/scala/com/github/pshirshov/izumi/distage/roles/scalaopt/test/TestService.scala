package com.github.pshirshov.izumi.distage.roles.scalaopt.test

import java.util.concurrent.ExecutorService

import com.github.pshirshov.izumi.distage.config.annotations.ConfPath
import com.github.pshirshov.izumi.distage.roles._
import com.github.pshirshov.izumi.fundamentals.platform.integration.ResourceCheck
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.logstage.api.IzLogger

import scala.collection.mutable

trait Dummy


case class TestServiceConf(
                            intval: Int
                            , strval: String
                            , overridenInt: Int
                            , systemPropInt: Int
                            , systemPropList: List[Int]
                          )

@RoleId(TestService.id)
class TestService(
                   @ConfPath("testservice") val conf: TestServiceConf
                   , val dummies: Set[Dummy]
                   , val closeables: Set[AutoCloseable]
                   , val counter: InitCounter
                   , logger: IzLogger
                   , notCloseable: NotCloseable
                   , val resources: Set[Resource]
                   , val es: ExecutorService
                 ) extends RoleService with RoleTask {
  notCloseable.discard()

  override def start(): Unit = {
    logger.info(s"Test service started; $dummies, $closeables")
  }

  override def stop(): Unit = {
    logger.info(s"Test service is going to stop")
  }
}

object TestService extends RoleDescriptor {
  override final val id = "testservice"
}

class InitCounter {
  val startedRoleComponents: mutable.ArrayBuffer[RoleComponent] = mutable.ArrayBuffer()
  val closedRoleComponents: mutable.ArrayBuffer[RoleComponent] = mutable.ArrayBuffer()
  val startedCloseables: mutable.ArrayBuffer[AutoCloseable] = mutable.ArrayBuffer()
  val closedCloseables: mutable.ArrayBuffer[AutoCloseable] = mutable.ArrayBuffer()
  val checkedResources: mutable.ArrayBuffer[IntegrationComponent] = mutable.ArrayBuffer()
}

trait Resource

class Resource1(val closeable: Resource2, counter: InitCounter) extends Resource with AutoCloseable with IntegrationComponent {
  counter.startedCloseables += this

  override def close(): Unit = counter.closedCloseables += this

  override def resourcesAvailable(): ResourceCheck = {
    counter.checkedResources += this
    ResourceCheck.Success()
  }
}

class Resource2(val roleComponent: Resource3, counter: InitCounter) extends Resource with AutoCloseable with IntegrationComponent {
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
