package izumi.distage.model.definition.errors

import izumi.distage.model.definition.{Binding, ImplDef}
import izumi.fundamentals.collections.nonempty.NEList

final case class LocalContextPlanningFailure(binding: Binding, impl: ImplDef.ContextImpl, errors: NEList[DIError])
