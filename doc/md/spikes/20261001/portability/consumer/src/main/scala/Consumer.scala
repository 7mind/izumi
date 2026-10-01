import izumi.fundamentals.platform.language.{SourceFilePosition, SourceFilePositionMaterializer}

object Consumer {
  def main(args: Array[String]): Unit = {
    val position = SourceFilePositionMaterializer.sourcePosition
    assert(position.file == "Consumer.scala")
    assert(position.line > 0)
    println(s"LOCAL_ARTIFACT_MACRO_CONSUMED $position")
  }
}
