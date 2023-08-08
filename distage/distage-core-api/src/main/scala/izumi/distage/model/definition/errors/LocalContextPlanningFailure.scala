package izumi.distage.model.definition.errors

import izumi.distage.model.definition.{Binding, ImplDef}

final case class LocalContextPlanningFailure(binding: Binding, impl: ImplDef.ContextImpl, errors: List[DIError])
