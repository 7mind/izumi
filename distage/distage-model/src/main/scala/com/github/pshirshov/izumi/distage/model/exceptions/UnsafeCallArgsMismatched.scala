package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.fundamentals.reflection.RuntimeUniverse

class UnsafeCallArgsMismatched(message: String, val expected: Seq[RuntimeUniverse.TypeFull], val actual: Seq[Any]) extends DIException(message, null)
