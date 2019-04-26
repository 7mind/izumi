package com.github.pshirshov.izumi.fundamentals.platform.cli.model.schema

case class ParserSchema(descriptors: Seq[RoleParserSchema])

case class RoleParserSchema(id: String, parser: ParserDef, doc: Option[String])


