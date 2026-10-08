package izumi.distage.roles.launcher

trait PreparedAppSyntaxPlatformSpecific {
  implicit class PreparedAppSyntaxImpl[F[_]](app: PreparedApp[F]) {
    def run(): Unit = {
      app.runner.runBlocking {
        app.appResource.use {
          appLocator =>
            app.roleAppEntrypoint.runTasksAndRoles(appLocator, app.effect, app.effectAsync)
        }(using app.effect)
      }
    }
  }
}
