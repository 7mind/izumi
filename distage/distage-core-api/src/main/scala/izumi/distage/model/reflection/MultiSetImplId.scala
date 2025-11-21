package izumi.distage.model.reflection

import izumi.distage.model.definition.ImplDef

final case class MultiSetImplId(set: DIKey, impl: ImplDef)

object MultiSetImplId {
  implicit object SetImplIdContract extends IdContract[MultiSetImplId] {
    override def repr(v: MultiSetImplId): String = s"set/${v.set}#${v.impl.hashCode}"
  }
}
