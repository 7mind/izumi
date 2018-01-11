package org.bitbucket.pshirshov.izumi.di.model.exceptions

import org.bitbucket.pshirshov.izumi.di.model.plan.DodgyOp

class UntranslatablePlanException(message: String, val badSteps: Seq[DodgyOp]) extends RuntimeException(message)
