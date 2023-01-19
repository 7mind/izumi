package izumi.distage.model.exceptions.runtime

import izumi.distage.model.exceptions.DIException
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.graphs.ToposortError
import izumi.fundamentals.platform.IzumiProject

class ToposortFailed(val error: ToposortError[DIKey]) extends DIException(IzumiProject.bugReportPrompt(s"BUG: toposort failed during plan rendering: $error"))
