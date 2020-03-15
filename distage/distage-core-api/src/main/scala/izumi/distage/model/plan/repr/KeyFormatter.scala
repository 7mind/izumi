package izumi.distage.model.plan.repr

import izumi.distage.model.reflection.{DIKey, SafeType}

trait KeyFormatter {
  def formatKey(key: DIKey): String
}
object KeyFormatter {
  def minimized(minimizer: KeyMinimizer): KeyFormatter = minimizer.renderKey
  val Full: KeyFormatter = _.toString
}

trait TypeFormatter {
  def formatType(key: SafeType): String
}
object TypeFormatter {
  def minimized(minimizer: KeyMinimizer): TypeFormatter = minimizer.renderType
  val Full: TypeFormatter = _.tag.repr
}
