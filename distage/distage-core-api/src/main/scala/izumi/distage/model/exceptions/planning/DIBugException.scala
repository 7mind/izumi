package izumi.distage.model.exceptions.planning

import izumi.distage.model.exceptions.DIException
import izumi.fundamentals.platform.IzumiProject

@deprecated("Needs to be removed", "20/10/2021")
class DIBugException(message: String) extends DIException(IzumiProject.bugReportPrompt(message))
