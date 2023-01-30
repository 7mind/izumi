package izumi.distage.roles.launcher

trait PreparedAppSyntax {
  implicit class PreparedAppSyntaxImpl[F[_]](app: PreparedApp[F]) {
    def run(): Unit = {
      app.runner.run(app.appResource.use(_ => app.effect.unit)(app.effect))
    }
  }
}
