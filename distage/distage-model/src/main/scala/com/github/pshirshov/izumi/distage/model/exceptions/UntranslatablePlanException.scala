package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.InstantiationOp
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse


class UntranslatablePlanException(message: String, val conflicts: Map[RuntimeDIUniverse.DIKey, Set[InstantiationOp]]) extends DIException(message, null)
