package com.github.pshirshov.izumi.idealingua.runtime.http4s

import cats.effect.IO
import com.github.pshirshov.izumi.idealingua.model.runtime.model.IDLService
import com.github.pshirshov.izumi.idealingua.model.runtime.transport.WireTransport
import org.http4s.dsl.Http4sDsl
import org.http4s.{Request, Response}

abstract class Http4sWireTransport[Service <: IDLService]
  extends WireTransport[Request[IO], Response[IO], Service] with Http4sDsl[IO]
