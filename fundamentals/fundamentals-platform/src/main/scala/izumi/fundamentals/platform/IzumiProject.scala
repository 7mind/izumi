package izumi.fundamentals.platform

object IzumiProject {
  final val bugtrackerUrl = "https://github.com/7mind/izumi/issues"
  final def bugReportPrompt(message: String, diag: String*): String = {
    (Seq(s"[BUG] $message, please report: $bugtrackerUrl") ++ diag).mkString("\n")
  }
}
