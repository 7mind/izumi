package izumi.distage.model.plan.repr

import izumi.distage.model.reflection.{DIKey, SafeType}

trait KeyFormatter {
  def formatKey(key: DIKey): String
}
object KeyFormatter {
  def minimized(minimizer: KeyMinimizer): KeyFormatter = new KeyFormatter {
    override def formatKey(key: DIKey): String = minimizer.renderKey(key)
  }
  val Full: KeyFormatter = _.toString
}

trait TypeFormatter {
  def formatType(key: SafeType): String
}
object TypeFormatter {
  def minimized(minimizer: KeyMinimizer): TypeFormatter = new TypeFormatter {
    override def formatType(key: SafeType): String = minimizer.renderType(key)
  }
  val Full: TypeFormatter = _.tag.repr
}
