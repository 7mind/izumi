package izumi.fundamentals.platform.language

final case class SourceFilePosition(file: String, line: Int) {
  override def toString: String = s"($file:$line)" // It will be a clickable link in intellij console
}

object SourceFilePosition {
  def unknown = SourceFilePosition("?", 0)
}
