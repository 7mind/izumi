package izumi.distage.roles.launcher

trait PreparedAppSyntaxPlatformSpecific {
  implicit class PreparedAppSyntaxImpl[F[+_, +_]](app: PreparedApp[F]) {
    def run(): Unit = {
      app.runner.unsafeRun {
        app.appResource.use {
          appLocator =>
            app.roleAppEntrypoint.runTasksAndRoles(appLocator, app.effect, app.effectAsync)
        }(using app.effect)
      }
    }
  }
}
