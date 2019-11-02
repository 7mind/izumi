package izumi.distage.model.plan

sealed trait DependencyKind

object DependencyKind {

  final case object Depends extends DependencyKind

  final case object Required extends DependencyKind

}
