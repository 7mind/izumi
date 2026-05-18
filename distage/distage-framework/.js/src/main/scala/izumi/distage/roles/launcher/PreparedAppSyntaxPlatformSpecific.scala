package izumi.distage.roles.launcher

import scala.concurrent.{ExecutionContext, Future}

trait PreparedAppSyntaxPlatformSpecific {
  implicit class PreparedAppSyntaxImpl[F[+_, +_]](app: PreparedApp[F]) {
    def run(): Future[Unit] = {
      val f = app.runner.unsafeRunAsyncAsFuture {
        app.appResource.use {
          appLocator =>
            app.roleAppEntrypoint.runTasksAndRoles(appLocator, app.effect, app.effectAsync)
        }(using app.effect)
      }
      f.map(_ => ())(ExecutionContext.parasitic)
    }
  }
}
