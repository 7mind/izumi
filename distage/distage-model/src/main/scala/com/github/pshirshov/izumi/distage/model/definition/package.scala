package com.github.pshirshov.izumi.distage.model

import scala.language.implicitConversions

package object definition {
  // doesn't work properly if put in ModuleBuilder or ContextDefinition companion object
  // (triggers with `object extends ModuleBuilder`, but doesn't with `new ModuleBuilder {}`)
  // works in package object though...
  implicit def moduleBuilderContextDefinition(moduleBuilder: ModuleBuilder): ModuleDef =
    moduleBuilder.build
}
