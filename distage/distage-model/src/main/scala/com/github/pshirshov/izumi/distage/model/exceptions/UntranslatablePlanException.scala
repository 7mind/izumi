package com.github.pshirshov.izumi.distage.model.exceptions


class UntranslatablePlanException(message: String, val badSteps: Seq[PlanningFailure]) extends DIException(message, null)
