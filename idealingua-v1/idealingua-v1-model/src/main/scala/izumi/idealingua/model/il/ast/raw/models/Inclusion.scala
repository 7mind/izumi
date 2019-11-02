package izumi.idealingua.model.il.ast.raw.models

final case class Inclusion(i: String) {
  override def toString: String = s"`$i`"
}
