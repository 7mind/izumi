package izumi.distage.roles.test.fixtures

import cats.effect.IO
import distage.Lifecycle
import izumi.distage.plugins.PluginDef
import izumi.distage.roles.model.{RoleDescriptor, RoleService}
import izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams
import izumi.fundamentals.platform.language.Quirks
import izumi.logstage.api.Log
import izumi.logstage.api.logger.LogSink

class AdoptedAutocloseablesCasePlugin extends PluginDef {
  make[AdoptedAutocloseablesCase]
  many[LogSink].add[BrokenSink]
  many[LogSink].add[BrokenSink2]
  many[LogSink].add {
    new BrokenSink()
  }
}

class BrokenSink2 extends LogSink {
  override def flush(e: Log.Entry): Unit = {
    Quirks.discard(e)
  }
}

class BrokenSink extends LogSink {
  override def flush(e: Log.Entry): Unit = {
    Quirks.discard(e)
  }
}

class AdoptedAutocloseablesCase(
  val sinks: Set[LogSink]
) extends RoleService[IO] {

  override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): DIResource[IO, Unit] = {
    DIResource.liftF(IO.unit)
  }
}

object AdoptedAutocloseablesCase extends RoleDescriptor {
  override final val id = "adopted-autocloseable"
}
