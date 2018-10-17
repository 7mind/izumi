package com.github.pshirshov.izumi.distage.roles.roles

sealed trait ResourceCheck

object ResourceCheck {
  final case class Success() extends ResourceCheck
  sealed trait Failure extends ResourceCheck
  final case class Failures(failures: Seq[Failure]) extends ResourceCheck
  final case class ResourceUnavailable(description: String) extends ResourceCheck
}
