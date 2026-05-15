package izumi.distage.roles.test.fixtures

import cats.effect.IO
import izumi.distage.model.definition.Lifecycle
import izumi.distage.plugins.PluginDef
import izumi.distage.roles.model.definition.RoleModuleDef
import izumi.distage.roles.model.{RoleDescriptor, RoleService}
import izumi.functional.bio.Bifunctorized
import izumi.fundamentals.platform.cli.model.EntrypointArgs
import izumi.logstage.api.Log
import izumi.logstage.api.logger.LogSink

class AdaptedAutocloseablesCasePlugin extends PluginDef with RoleModuleDef {
  makeRole[AdaptedAutocloseablesCase]
  many[LogSink].add[BrokenSink]
  many[LogSink].add[BrokenSink2]
  many[LogSink].add {
    new BrokenSink()
  }
}

class BrokenSink2(/*recorder: XXX_ResourceEffectsRecorder[IO]*/ ) extends LogSink {
  override def flush(e: Log.Entry): Unit = {}
}

class BrokenSink(/*recorder: XXX_ResourceEffectsRecorder[IO]*/ ) extends LogSink {
  override def flush(e: Log.Entry): Unit = {}
}

class AdaptedAutocloseablesCase(
  val sinks: Set[LogSink]
) extends RoleService[Bifunctorized[IO, +_, +_]] {

  override def start(roleParameters: EntrypointArgs): Lifecycle[Bifunctorized[IO, +_, +_], Throwable, Unit] = {
    Lifecycle.make_[Bifunctorized[IO, +_, +_], Throwable, Unit](
      acquire = Bifunctorized.bifunctorize[IO, Unit](IO.unit)
    )(release = Bifunctorized.bifunctorize[IO, Unit](IO.unit).asInstanceOf[Bifunctorized[IO, Nothing, Unit]])
  }
}

object AdaptedAutocloseablesCase extends RoleDescriptor {
  override final val id = "adapted-autocloseable"
}
