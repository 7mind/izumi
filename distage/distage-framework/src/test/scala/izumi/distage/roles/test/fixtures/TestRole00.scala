package izumi.distage.roles.test.fixtures

import java.util.concurrent.ExecutorService

import izumi.distage.framework.model.IntegrationCheck
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.effect.QuasiEffect
import izumi.distage.roles.model.{RoleDescriptor, RoleService, RoleTask}
import izumi.distage.roles.test.fixtures.Fixture._
import izumi.distage.roles.test.fixtures.ResourcesPlugin.Conflict
import izumi.distage.roles.test.fixtures.TestPlugin.NotCloseable
import izumi.distage.roles.test.fixtures.roles.TestRole00.TestRole00Resource
import izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams
import izumi.fundamentals.platform.cli.model.schema.{ParserDef, RoleParserSchema}
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.language.Quirks._
import izumi.logstage.api.IzLogger

class TestTask00[F[_]: QuasiEffect](logger: IzLogger) extends RoleTask[F] {
  override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): F[Unit] = {
    QuasiEffect[F].maybeSuspend {
      logger.info(s"[TestTask00] Entrypoint invoked!: $roleParameters, $freeArgs")
    }
  }
}
object TestTask00 extends RoleDescriptor {
  override final val id = "testtask00"
}

object roles {

  class TestRole00[F[_]: QuasiEffect](
    logger: IzLogger,
    notCloseable: NotCloseable,
    val conf: TestServiceConf,
    val conf2: TestServiceConf2,
    val dummies: Set[Dummy],
    val setElems: Set[SetElement],
    val resource: TestRole00Resource[F],
    val resources: Set[TestResource[F]],
    val conflict: Conflict,
    val es: ExecutorService,
    val counter: XXX_ResourceEffectsRecorder[F],
    val ref: XXX_LocatorLeak,
  ) extends RoleService[F] {
    notCloseable.discard()

    override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): Lifecycle[F, Unit] = Lifecycle.make(QuasiEffect[F].maybeSuspend {
      logger.info(s"[TestRole00] started: $roleParameters, $freeArgs, $dummies, $conflict")
      assert(conf.overridenInt == 111)
    }) {
      _ =>
        QuasiEffect[F].maybeSuspend {
          logger.info(s"[TestRole00] exiting role...")
        }
    }
  }
  object TestRole00 extends RoleDescriptor {
    override final val id = "testrole00"

    override def parserSchema: RoleParserSchema = RoleParserSchema(id, ParserDef.Empty, Some("Example role"), None, freeArgsAllowed = true)

    final case class IntegrationOnlyCfg(flag: Boolean)
    final case class IntegrationOnlyCfg2(value: String)

    final case class SetElementOnlyCfg(abc: String)

    final class TestRole00Resource[F[_]](private val it: TestRole00ResourceIntegrationCheck[F])
    final class TestRole00ResourceIntegrationCheck[F[_]: QuasiEffect](
      private val cfg: IntegrationOnlyCfg,
      private val cfg2: IntegrationOnlyCfg2,
    ) extends IntegrationCheck[F] {
      override def resourcesAvailable(): F[ResourceCheck] = QuasiEffect[F].pure {
        assert(cfg2.value == "configvalue:updated")
        ResourceCheck.Success()
      }
    }
  }

}

class TestRole01[F[_]: QuasiEffect](logger: IzLogger) extends RoleService[F] {
  override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): Lifecycle[F, Unit] = Lifecycle.make(QuasiEffect[F].maybeSuspend {
    logger.info(s"[TestRole01] started: $roleParameters, $freeArgs")
  }) {
    _ =>
      QuasiEffect[F].maybeSuspend {
        logger.info(s"[TestRole01] exiting role...")
      }
  }
}
object TestRole01 extends RoleDescriptor {
  override final val id = "testrole01"

  override def parserSchema: RoleParserSchema = RoleParserSchema(id, ParserDef.Empty, Some("Example role"), None, freeArgsAllowed = false)
}

class TestRole02[F[_]: QuasiEffect](logger: IzLogger) extends RoleService[F] {
  override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): Lifecycle[F, Unit] = Lifecycle.make(QuasiEffect[F].maybeSuspend {
    logger.info(s"[TestRole02] started: $roleParameters, $freeArgs")
  }) {
    _ =>
      QuasiEffect[F].maybeSuspend {
        logger.info(s"[TestRole02] exiting role...")
      }
  }
}
object TestRole02 extends RoleDescriptor {
  override final val id = "testrole02"
}

class TestRole03[F[_]: QuasiEffect](
  logger: IzLogger,
  axisComponent: AxisComponent,
) extends RoleService[F] {
  override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): Lifecycle[F, Unit] = Lifecycle.make(QuasiEffect[F].maybeSuspend {
    logger.info(s"[TestRole03] started: $roleParameters, $freeArgs")
    assert(axisComponent == AxisComponentCorrect, TestRole03.expectedError)
  }) {
    _ =>
      QuasiEffect[F].maybeSuspend {
        logger.info(s"[TestRole03] exiting role...")
      }
  }
}
object TestRole03 extends RoleDescriptor {
  val expectedError = "bad axisComponent"
  override final val id = "testrole03"
}

class TestRole04[F[_]: QuasiEffect](
  logger: IzLogger,
  listconf: ListConf,
) extends RoleService[F] {
  override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): Lifecycle[F, Unit] = Lifecycle.make(QuasiEffect[F].maybeSuspend {
    logger.info(s"[TestRole04] started: $roleParameters, $freeArgs")
    assert(listconf.ints == List(3, 2, 1), listconf.ints)
  }) {
    _ =>
      QuasiEffect[F].maybeSuspend {
        logger.info(s"[TestRole04] exiting role...")
      }
  }
}
object TestRole04 extends RoleDescriptor {
  override final val id = "testrole04"
}
