package izumi.distage.roles.launcher

import scala.concurrent.Future

trait PreparedAppSyntaxPlatformSpecific {
  implicit class PreparedAppSyntaxImpl[F[_]](app: PreparedApp[F]) {
    def run(): Future[Unit] = {
      app.runner.runFuture {
        app.appResource.use {
          appLocator =>
            app.roleAppEntrypoint.runTasksAndRoles(appLocator, app.effect)
        }(using app.effect)
      }
    }
  }
}
