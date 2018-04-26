package com.github.pshirshov.izumi.idealingua.runtime.rpc

case class IRTInContext[V, Ctx](value: V, context: Ctx)
