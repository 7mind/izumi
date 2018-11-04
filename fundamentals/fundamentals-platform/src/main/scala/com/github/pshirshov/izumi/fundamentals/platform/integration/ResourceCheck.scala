package com.github.pshirshov.izumi.fundamentals.platform.integration

import com.github.pshirshov.izumi.fundamentals.platform.exceptions.IzThrowable._

sealed trait ResourceCheck

object ResourceCheck {

  final case class Success() extends ResourceCheck

  sealed trait Failure extends ResourceCheck

  final case class ResourceUnavailable(description: String, cause: Option[Throwable]) extends Failure {
    override def toString: String = s"ResourceUnavailable(description=$description, cause=${cause.map(_.stackTrace)})"
  }

}
