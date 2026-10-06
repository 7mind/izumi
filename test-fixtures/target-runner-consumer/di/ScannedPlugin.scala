package candidate.plugins

import izumi.distage.config.model.AppConfig
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.distage.plugins.PluginDef
import izumi.functional.lifecycle.Lifecycle
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.uuid.IzUUID

final case class ImplementationRevision(value: String)
final class SharedResource(val id: String, val revision: String, val snapshot: String, val repo: String)

final class FixturePlugin extends PluginDef {
  println("SDK_DI_PLUGIN revision=" + implementationRevision)
  make[ImplementationRevision].fromValue(ImplementationRevision(implementationRevision))
  make[SharedResource].tagged(Repo.Dummy).fromResource { (version: ImplementationRevision, config: AppConfig) => resource("dummy", version, config) }
  make[SharedResource].tagged(Repo.Prod).fromResource { (version: ImplementationRevision, config: AppConfig) => resource("prod", version, config) }

  private def implementationRevision: String = "one"

  private def resource(repo: String, version: ImplementationRevision, config: AppConfig): Lifecycle[Identity, SharedResource] =
    Lifecycle.make[Identity, SharedResource] {
      val snapshot = config.config("snapshot").flatMap(_.asString).getOrElse(throw new IllegalStateException("SDK configuration snapshot is missing"))
      val value = new SharedResource(IzUUID.generateTimeUUID().toString, version.value, snapshot, repo)
      println("SDK_DI_ACQUIRE owner=" + value.id + " revision=" + value.revision + " snapshot=" + value.snapshot + " repo=" + value.repo)
      value
    } { value => println("SDK_DI_RELEASE owner=" + value.id) }
}

