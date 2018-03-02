package com.github.pshirshov.izumi.idealingua.runtime.http4s

import cats.effect.IO
import com.github.pshirshov.izumi.idealingua.model.runtime.model.IDLInput
import com.github.pshirshov.izumi.idealingua.model.runtime.transport.WireProtocol
import org.http4s.{Request, Response}

abstract class Http4sProtocol[IN <: IDLInput, OUT] extends WireProtocol[Request[IO], IN, OUT, Response[IO]]
