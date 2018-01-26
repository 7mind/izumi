package org.bitbucket.pshirshov.izumi.distage.model.exceptions

import org.bitbucket.pshirshov.izumi.distage.model.plan.PlanningFailure


class UntranslatablePlanException(message: String, val badSteps: Seq[PlanningFailure]) extends DIException(message, null)
