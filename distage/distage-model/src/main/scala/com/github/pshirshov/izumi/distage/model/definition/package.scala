package com.github.pshirshov.izumi.distage.model

import scala.language.implicitConversions

package object definition {
  implicit def moduleDefIsBindingDSL[M](moduleDef: M)(implicit ev: M => AbstractModuleDef): BindingDSL =
    new BindingDSL(moduleDef.bindings)

}
