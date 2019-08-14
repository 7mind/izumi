package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.plan.PlanTopology

class ForwardRefException(message: String, val topology: PlanTopology) extends DIException(message, null)


