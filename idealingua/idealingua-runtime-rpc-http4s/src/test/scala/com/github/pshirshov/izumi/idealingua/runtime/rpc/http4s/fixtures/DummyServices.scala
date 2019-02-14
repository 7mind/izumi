package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s.fixtures

import com.github.pshirshov.izumi.functional.bio.BIO
import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import com.github.pshirshov.izumi.r2.idealingua.test.generated.{GreeterServiceClientWrapped, GreeterServiceServerWrapped}
import com.github.pshirshov.izumi.r2.idealingua.test.impls.AbstractGreeterServer

class DummyServices[R[+ _, + _] : BIO, Ctx] {

  object Server {
    private val greeterService = new AbstractGreeterServer.Impl[R, Ctx]
    private val greeterDispatcher = new GreeterServiceServerWrapped(greeterService)
    private val dispatchers: Set[IRTWrappedService[R, Ctx]] = Set(greeterDispatcher).map(d => new DummyAuthorizingDispatcher(d))
    val multiplexor = new IRTServerMultiplexor[R, Ctx, Ctx](dispatchers, ContextExtender.id)

    private val clients: Set[IRTWrappedClient] = Set(GreeterServiceClientWrapped)
    val codec = new IRTClientMultiplexor[R](clients)
  }

  object Client {
    private val greeterService = new AbstractGreeterServer.Impl[R, Unit]
    private val greeterDispatcher = new GreeterServiceServerWrapped(greeterService)
    private val dispatchers: Set[IRTWrappedService[R, Unit]] = Set(greeterDispatcher)

    private val clients: Set[IRTWrappedClient] = Set(GreeterServiceClientWrapped)
    val codec = new IRTClientMultiplexor[R](clients)
    val buzzerMultiplexor = new IRTServerMultiplexor[R, Unit, Unit](dispatchers, ContextExtender.id)
  }

}
