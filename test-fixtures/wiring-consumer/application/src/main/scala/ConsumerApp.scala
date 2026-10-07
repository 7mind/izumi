package izumi.fixtures.wiring

import izumi.distage.framework.CoreCheckableAppSimple
import izumi.distage.model.definition.{ModuleBase, ModuleDef}
import izumi.distage.model.plan.Roots
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.platform.functional.Identity

object ConsumerApp extends CoreCheckableAppSimple[Identity] {
  override def module: ModuleBase = new ModuleDef {
    make[String].fromValue("published wiring")
  }
  override def roots: Roots = Roots(DIKey[String])
}

object MissingDependencyApp extends CoreCheckableAppSimple[Identity] {
  override def module: ModuleBase = new ModuleDef {
    make[String].from((missing: Boolean) => missing.toString)
  }
  override def roots: Roots = Roots(DIKey[String])
}
