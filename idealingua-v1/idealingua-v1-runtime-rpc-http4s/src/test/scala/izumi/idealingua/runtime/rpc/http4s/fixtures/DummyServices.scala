package izumi.idealingua.runtime.rpc.http4s.fixtures

import izumi.functional.bio.BIO
import izumi.idealingua.runtime.rpc._
import izumi.r2.idealingua.test.generated.{GreeterServiceClientWrapped, GreeterServiceServerWrapped}
import izumi.r2.idealingua.test.impls.AbstractGreeterServer

class DummyServices[F[+ _, + _] : BIO, Ctx] {

  object Server {
    private val greeterService = new AbstractGreeterServer.Impl[F, Ctx]
    private val greeterDispatcher = new GreeterServiceServerWrapped(greeterService)
    private val dispatchers: Set[IRTWrappedService[F, Ctx]] = Set(greeterDispatcher).map(d => new DummyAuthorizingDispatcher(d))
    val multiplexor = new IRTServerMultiplexor[F, Ctx, Ctx](dispatchers, ContextExtender.id)

    private val clients: Set[IRTWrappedClient] = Set(GreeterServiceClientWrapped)
    val codec = new IRTClientMultiplexor[F](clients)
  }

  object Client {
    private val greeterService = new AbstractGreeterServer.Impl[F, Unit]
    private val greeterDispatcher = new GreeterServiceServerWrapped(greeterService)
    private val dispatchers: Set[IRTWrappedService[F, Unit]] = Set(greeterDispatcher)

    private val clients: Set[IRTWrappedClient] = Set(GreeterServiceClientWrapped)
    val codec = new IRTClientMultiplexor[F](clients)
    val buzzerMultiplexor = new IRTServerMultiplexor[F, Unit, Unit](dispatchers, ContextExtender.id)
  }

}
