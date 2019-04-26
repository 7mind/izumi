package com.github.pshirshov.izumi.distage.roles.model

import com.github.pshirshov.izumi.fundamentals.platform.cli.model.schema.{ParserDef, RoleParserSchema}

trait RoleDescriptor {
  def id: String
  def parserSchema: RoleParserSchema = RoleParserSchema(id, ParserDef.Empty, None, None, freeArgsAllowed = false)
}
