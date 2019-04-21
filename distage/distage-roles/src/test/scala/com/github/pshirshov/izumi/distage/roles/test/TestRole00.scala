package com.github.pshirshov.izumi.distage.roles.test

import java.util.concurrent.ExecutorService

import com.github.pshirshov.izumi.distage.config.annotations.ConfPath
import com.github.pshirshov.izumi.distage.model.definition.DIResource
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.roles._
import com.github.pshirshov.izumi.distage.roles.test.Junk._
import com.github.pshirshov.izumi.distage.roles.test.TestPlugin.NotCloseable
import com.github.pshirshov.izumi.fundamentals.platform.cli.Parameters
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.logstage.api.IzLogger

@RoleId(TestTask00.id)
class TestTask00[F[_] : DIEffect](logger: IzLogger) extends RoleTask2[F] {
  override def start(roleParameters: Parameters, freeArgs: Vector[String]): F[Unit] = {
    DIEffect[F].maybeSuspend {
      logger.info(s"[TestTask00] Entrypoint invoked!: $roleParameters, $freeArgs")
    }
  }
}

object TestTask00 extends RoleDescriptor {
  override final val id = "testtask00"
}


@RoleId(TestRole00.id)
class TestRole00[F[_] : DIEffect](
                                   @ConfPath("testservice") val conf: TestServiceConf
                                   , val dummies: Set[Dummy]
                                   , val closeables: Set[AutoCloseable]
                                   , val counter: InitCounter
                                   , logger: IzLogger
                                   , notCloseable: NotCloseable
                                   , val resources: Set[Resource]
                                   , val es: ExecutorService
                                 ) extends RoleService2[F]  {
  notCloseable.discard()

  override def start(roleParameters: Parameters, freeArgs: Vector[String]): DIResource[F, Unit] = DIResource.make(DIEffect[F].maybeSuspend {
    logger.info(s"[TestRole00] started: $roleParameters, $freeArgs, $dummies, $closeables")
  }) {
    _ =>
      DIEffect[F].maybeSuspend {
        logger.info(s"[TestRole00] exiting role...")
      }
  }
}



object TestRole00 extends RoleDescriptor {
  override final val id = "testrole00"
}

@RoleId(TestRole01.id)
class TestRole01[F[_] : DIEffect](logger: IzLogger) extends RoleService2[F] {
  override def start(roleParameters: Parameters, freeArgs: Vector[String]): DIResource[F, Unit] = DIResource.make(DIEffect[F].maybeSuspend {
    logger.info(s"[TestRole01] started: $roleParameters, $freeArgs")
  }) {
    _ =>
      DIEffect[F].maybeSuspend {
        logger.info(s"[TestRole01] exiting role...")
      }
  }
}

object TestRole01 extends RoleDescriptor {
  override final val id = "testrole01"
}

@RoleId(TestRole02.id)
class TestRole02[F[_] : DIEffect](logger: IzLogger) extends RoleService2[F] {
  override def start(roleParameters: Parameters, freeArgs: Vector[String]): DIResource[F, Unit] = DIResource.make(DIEffect[F].maybeSuspend {
    logger.info(s"[TestRole02] started: $roleParameters, $freeArgs")
  }) {
    _ =>
      DIEffect[F].maybeSuspend {
        logger.info(s"[TestRole02] exiting role...")
      }
  }
}

object TestRole02 extends RoleDescriptor {
  override final val id = "testrole02"
}

