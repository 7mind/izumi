package izumi.distage.model.exceptions.planning

import izumi.distage.model.exceptions.DIException

@deprecated("Needs to be removed", "20/10/2021")
case class DIBugException(message: String) extends DIException(s"DISTAGE BUG: $message; please report: https://github.com/7mind/izumi/issues")
