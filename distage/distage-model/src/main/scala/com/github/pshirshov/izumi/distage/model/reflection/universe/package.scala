package com.github.pshirshov.izumi.distage.model.reflection

import com.github.pshirshov.izumi.fundamentals.reflection.SingletonUniverse

package object universe {
  type MacroUniverse[U <: SingletonUniverse] = DIUniverse { val u: U }
}
