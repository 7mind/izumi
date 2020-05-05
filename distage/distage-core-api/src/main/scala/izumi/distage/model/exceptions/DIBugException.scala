package izumi.distage.model.exceptions

case class DIBugException(message: String) extends DIException(s"DISTAGE BUG: $message; please report: https://github.com/7mind/izumi/issues")
