package izumi.idealingua.model.output

final case class ModuleId(path: Seq[String], name: String) {
  override def toString: String = s"module:${path.mkString("/")}/$name"
}
