package com.github.pshirshov.izumi.distage.model

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse

package object definition {
  type Binding = BindingT[RuntimeUniverse.DIKey]
}
