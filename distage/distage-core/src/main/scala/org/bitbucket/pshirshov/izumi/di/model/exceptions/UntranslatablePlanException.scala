package org.bitbucket.pshirshov.izumi.di.model.exceptions

import org.bitbucket.pshirshov.izumi.di.model.plan.PlanningFailure


class UntranslatablePlanException(message: String, val badSteps: Seq[PlanningFailure]) extends DIException(message, null)
