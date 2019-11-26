package izumi.idealingua.model.loader

import izumi.idealingua.model.il.ast.raw.models.ParsedModel

sealed trait ModelParsingResult {
  def path: FSPath
}

object ModelParsingResult {
  final case class Success(path: FSPath, model: ParsedModel) extends ModelParsingResult
  final case class Failure(path: FSPath, message: String) extends ModelParsingResult
}

case class ParsedModels(results: Seq[ModelParsingResult])
