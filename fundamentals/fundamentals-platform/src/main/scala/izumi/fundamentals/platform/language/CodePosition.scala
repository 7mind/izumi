package izumi.fundamentals.platform.language

final case class CodePosition(position: SourceFilePosition, applicationPointId: String) {
  override def toString: String = s"$applicationPointId@$position"
}
