package izumi.fixtures.host.plugins

import izumi.distage.plugins.PluginDef
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.fixtures.host.SharedResource
import izumi.functional.lifecycle.Lifecycle
import izumi.fundamentals.platform.functional.Identity

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths, StandardOpenOption}
import java.util.UUID

final class FixturePlugin extends PluginDef {
  make[SharedResource].tagged(Repo.Prod).fromResource(() => resource("prod"))
  make[SharedResource].tagged(Repo.Dummy).fromResource(() => resource("dummy"))

  private def resource(repo: String): Lifecycle[Identity, SharedResource] = Lifecycle.make[Identity, SharedResource] {
    val resource = new SharedResource(repo + "-" + UUID.randomUUID().toString, Paths.get(sys.props("izumi.fixture.audit-root")))
    val _ = Files.write(resource.directory.resolve(resource.id + ".acquire"), resource.id.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    resource
  } { resource =>
    val _ = Files.write(resource.directory.resolve(resource.id + ".release"), resource.id.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    ()
  }
}
