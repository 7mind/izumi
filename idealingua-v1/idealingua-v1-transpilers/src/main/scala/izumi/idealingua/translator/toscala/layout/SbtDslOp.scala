package izumi.idealingua.translator.toscala.layout

sealed trait SbtDslOp

object SbtDslOp {

  final case class Append[T](v: T, scope: Scope = Scope.ThisBuild) extends SbtDslOp

  final case class Assign[T](v: T, scope: Scope = Scope.ThisBuild) extends SbtDslOp

}
