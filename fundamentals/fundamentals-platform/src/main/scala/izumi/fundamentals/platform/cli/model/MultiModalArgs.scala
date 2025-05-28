package izumi.fundamentals.platform.cli.model

case class ModalityArgs(id: String, args: Vector[String]) {
  def flatten: Vector[String] = s":$id" +: args
}

case class MultiModalArgs(primaryArgs: Vector[String], modalities: Vector[ModalityArgs]) {
  def flatten: Vector[String] = primaryArgs ++ modalities.flatMap(_.flatten)
}
