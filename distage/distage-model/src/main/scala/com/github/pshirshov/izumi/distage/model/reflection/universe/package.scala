package com.github.pshirshov.izumi.distage.model.reflection

package object universe {
  type StaticDIUniverse[U] = DIUniverse { val u: U }
}
