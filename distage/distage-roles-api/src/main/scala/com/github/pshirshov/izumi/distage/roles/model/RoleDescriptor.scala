package com.github.pshirshov.izumi.distage.roles.model

import com.github.pshirshov.izumi.fundamentals.platform.cli.ParserDef

trait RoleDescriptor {
  def id: String
  def parser: ParserDef
}
