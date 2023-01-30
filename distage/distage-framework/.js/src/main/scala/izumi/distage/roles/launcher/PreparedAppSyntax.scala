package izumi.distage.roles.launcher

trait PreparedAppSyntax {
  implicit class PreparedAppSyntaxImpl[F[_]](app: PreparedApp[F]) {
    def run(): Unit = {
      ???
    }
  }
}
