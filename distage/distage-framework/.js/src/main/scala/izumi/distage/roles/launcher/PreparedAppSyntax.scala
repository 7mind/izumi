package izumi.distage.roles.launcher

import scala.concurrent.Future

trait PreparedAppSyntax {
  implicit class PreparedAppSyntaxImpl[F[_]](app: PreparedApp[F]) {
    def run(): Future[Unit] = {
      app.runner.runFuture(app.appResource.use(_ => app.effect.unit)(app.effect))
    }
  }
}
