package com.github.pshirshov.izumi.idealingua.runtime.rpc

import io.circe.Json

final case class IRTRawCall(methodId: IRTMethodId, body: Json)
