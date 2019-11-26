package izumi.distage.model.plan.repr

import izumi.distage.model.reflection.universe.RuntimeDIUniverse._

trait KeyFormatter {
  def formatKey(key: DIKey): String
}

object KeyFormatter {

  object Full extends KeyFormatter {
    override def formatKey(key: DIKey): String = key.toString
  }

}

trait TypeFormatter {
  def formatType(key: SafeType): String
}

object TypeFormatter {

  object Full extends TypeFormatter {
    override def formatType(key: SafeType): String = key.tag.repr
  }

}
