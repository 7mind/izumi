package izumi.idealingua.translator

final case class ExtensionId(id: String) extends AnyVal {
  override def toString: String = id
}

trait TranslatorExtension {
  def id: ExtensionId = ExtensionId(getClass.getSimpleName.split('$').head)

  override def toString: String = id.id
}
