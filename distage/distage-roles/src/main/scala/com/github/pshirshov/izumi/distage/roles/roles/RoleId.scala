package com.github.pshirshov.izumi.distage.roles.roles

final class RoleId(val id: String) extends scala.annotation.StaticAnnotation

trait RoleDescriptor {
  def id: String
}
