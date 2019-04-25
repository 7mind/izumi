package com.github.pshirshov.izumi.distage.roles.model

import com.github.pshirshov.izumi.fundamentals.platform.cli.model.schema.ParserDef

trait RoleDescriptor {
  def id: String
  def parser: ParserDef = ParserDef.Empty
  def doc: Option[String] = None
}
