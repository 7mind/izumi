package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.plan.ReplanningContext

class ReplanningLimitReached(message: String, val context: ReplanningContext) extends DIException(message, null)
