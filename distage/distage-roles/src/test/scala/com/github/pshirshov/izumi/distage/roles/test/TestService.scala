package com.github.pshirshov.izumi.distage.roles.test

import java.util.concurrent.ExecutorService

import com.github.pshirshov.izumi.distage.config.annotations.ConfPath
import com.github.pshirshov.izumi.distage.model.definition.DIResource
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.roles._
import com.github.pshirshov.izumi.distage.roles.cli.Parameters
import com.github.pshirshov.izumi.distage.roles.test.TestPlugin.NotCloseable
import com.github.pshirshov.izumi.fundamentals.platform.integration.ResourceCheck
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.logstage.api.IzLogger

import scala.collection.mutable

@RoleId(TestTask.id)
class TestTask[F[_] : DIEffect](logger: IzLogger) extends RoleTask2[F] {
  override def start(roleParameters: Parameters, freeArgs: Vector[String]): F[Unit] = {
    DIEffect[F].maybeSuspend {
      logger.info(s"Entrypoint invoked!: $roleParameters, $freeArgs")
    }
  }
}

object TestTask extends RoleDescriptor {
  override final val id = "testtask"
}

@RoleId(TestRole2.id)
class TestRole2[F[_] : DIEffect](logger: IzLogger) extends RoleService2[F] {
  override def start(roleParameters: Parameters, freeArgs: Vector[String]): DIResource[F, Unit] = DIResource.make(DIEffect[F].maybeSuspend {
    logger.info(s"[testrole2] started: $roleParameters, $freeArgs")
  }) {
    _ =>
      DIEffect[F].maybeSuspend {
        logger.info(s"[testrole2] exiting role...")
      }
  }
}

object TestRole2 extends RoleDescriptor {
  override final val id = "testrole"
}

@RoleId(TestRole3.id)
class TestRole3[F[_] : DIEffect](logger: IzLogger) extends RoleService2[F] {
  override def start(roleParameters: Parameters, freeArgs: Vector[String]): DIResource[F, Unit] = DIResource.make(DIEffect[F].maybeSuspend {
    logger.info(s"[testrole3] started: $roleParameters, $freeArgs")
  }) {
    _ =>
      DIEffect[F].maybeSuspend {
        logger.info(s"[testrole2] exiting role...")
      }
  }
}

object TestRole3 extends RoleDescriptor {
  override final val id = "testrole3"
}

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
