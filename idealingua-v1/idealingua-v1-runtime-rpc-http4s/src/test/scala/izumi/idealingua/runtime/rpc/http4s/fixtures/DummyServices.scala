package izumi.idealingua.runtime.rpc.http4s.fixtures

import izumi.functional.bio.BIO
import izumi.idealingua.runtime.rpc._
import izumi.r2.idealingua.test.generated.{GreeterServiceClientWrapped, GreeterServiceServerWrapped}
import izumi.r2.idealingua.test.impls.AbstractGreeterServer

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
