package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

final case class ParsedModel private(definitions: Seq[TopLevelDefn], includes: Seq[String])


object ParsedModel {
  def apply(definitions: Seq[TopLevelDefn]): ParsedModel = {
    val includes = definitions.collect({ case d: ILInclude => d.i })

    new ParsedModel(definitions.filterNot(_.isInstanceOf[ILInclude]), includes)
  }
}
