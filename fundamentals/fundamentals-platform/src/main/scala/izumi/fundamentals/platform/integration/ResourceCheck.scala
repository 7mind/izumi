package izumi.fundamentals.platform.integration

import izumi.fundamentals.platform.exceptions.IzThrowable._

sealed trait ResourceCheck

object ResourceCheck {

  final case class Success() extends ResourceCheck

  sealed trait Failure extends ResourceCheck

  final case class ResourceUnavailable(description: String, cause: Option[Throwable]) extends Failure {
    override def toString: String = {
      cause match {
        case Some(t) =>
          s"Unavailable resource: $description, ${t.getClass}: ${t.stackTrace}"

        case None =>
          s"Unavailable resource: $description"

      }
    }
  }

}
