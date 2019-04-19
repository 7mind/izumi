package com.github.pshirshov.izumi.distage.roles.cli

case class RoleAppArguments(globalParameters: Parameters, roles: Vector[RoleArg])
case class Parameters(flags: Vector[Flag], values: Vector[Value])
object Parameters {
  def empty: Parameters = Parameters(Vector.empty, Vector.empty)

}
case class RoleArg(role: String, roleParameters: Parameters, freeArgs: Vector[String])

case class Flag(name: String)
case class Value(name: String, value: String)
