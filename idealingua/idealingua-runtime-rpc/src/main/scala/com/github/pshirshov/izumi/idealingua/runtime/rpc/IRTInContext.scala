package com.github.pshirshov.izumi.idealingua.runtime.rpc

final case class IRTInContext[V, Ctx](value: V, context: Ctx)
