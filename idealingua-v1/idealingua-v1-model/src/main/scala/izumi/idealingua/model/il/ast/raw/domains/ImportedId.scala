package izumi.idealingua.model.il.ast.raw.domains

final case class ImportedId(name: String, as: Option[String]) {
  def importedAs: String = as.getOrElse(name)
}
