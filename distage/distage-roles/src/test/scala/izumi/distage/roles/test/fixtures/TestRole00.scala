package izumi.distage.roles.test.fixtures

import java.util.concurrent.ExecutorService

import izumi.distage.config.annotations.ConfPath
import izumi.distage.model.definition.DIResource
import izumi.distage.model.monadic.DIEffect
import izumi.distage.roles.model.{IntegrationCheck, RoleDescriptor, RoleService, RoleTask}
import izumi.distage.roles.test.fixtures.Fixture._
import izumi.distage.roles.test.fixtures.ResourcesPlugin.Conflict
import izumi.distage.roles.test.fixtures.TestPlugin.NotCloseable
import izumi.distage.roles.test.fixtures.TestRole00.TestRole00Resource
import izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams
import izumi.fundamentals.platform.cli.model.schema.{ParserDef, RoleParserSchema}
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.language.Quirks._
import izumi.logstage.api.IzLogger

class TestTask00[F[_]: DIEffect](logger: IzLogger) extends RoleTask[F] {
  override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): F[Unit] = {
    DIEffect[F].maybeSuspend {
      logger.info(s"[TestTask00] Entrypoint invoked!: $roleParameters, $freeArgs")
    }
  }
}

object TestTask00 extends RoleDescriptor {
  override final val id = "testtask00"
}

class TestRole00[F[_]: DIEffect](
  val conf: TestServiceConf @ConfPath("testservice"),
  val dummies: Set[Dummy],
  val counter: InitCounter,
  val resource: TestRole00Resource[F],
  logger: IzLogger,
  notCloseable: NotCloseable,
  val resources: Set[Resource0],
  val conflict: Conflict,
  val es: ExecutorService,
) extends RoleService[F] {
  notCloseable.discard()

  override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): DIResource[F, Unit] =
    DIResource.make(DIEffect[F].maybeSuspend {
      logger.info(s"[TestRole00] started: $roleParameters, $freeArgs, $dummies, $conflict")
    }) {
      _ =>
        DIEffect[F].maybeSuspend {
          logger.info(s"[TestRole00] exiting role...")
        }
    }
}

object TestRole00 extends RoleDescriptor {
  override final val id = "testrole00"

  override def parserSchema: RoleParserSchema = RoleParserSchema(id, ParserDef.Empty, Some("Example role"), None, freeArgsAllowed = true)

  final case class IntegrationOnlyCfg(flag: Boolean)

  final class TestRole00Resource[F[_]](private val it: TestRole00ResourceIntegrationCheck)
  final class TestRole00ResourceIntegrationCheck(
    private val cfg: IntegrationOnlyCfg @ConfPath("integrationOnlyCfg"),
  ) extends IntegrationCheck {
    override def resourcesAvailable(): ResourceCheck = ResourceCheck.Success()
  }
}

class TestRole01[F[_]: DIEffect](logger: IzLogger) extends RoleService[F] {
  override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): DIResource[F, Unit] =
    DIResource.make(DIEffect[F].maybeSuspend {
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

  override def parserSchema: RoleParserSchema = RoleParserSchema(id, ParserDef.Empty, Some("Example role"), None, freeArgsAllowed = false)
}

class TestRole02[F[_]: DIEffect](logger: IzLogger) extends RoleService[F] {
  override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): DIResource[F, Unit] =
    DIResource.make(DIEffect[F].maybeSuspend {
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
