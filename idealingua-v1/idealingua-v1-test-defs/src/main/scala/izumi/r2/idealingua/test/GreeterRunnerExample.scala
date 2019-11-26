package izumi.r2.idealingua.test

import _root_.io.circe.syntax._
import izumi.idealingua.runtime.rpc.{ContextExtender, IRTServerMultiplexor}
import izumi.r2.idealingua.test.generated.GreeterServiceServerWrapped
import zio._

object GreeterRunnerExample {
  def main(args: Array[String]): Unit = {
    val greeter = new GreeterServiceServerWrapped[IO, Unit](new impls.AbstractGreeterServer.Impl[IO, Unit]())
    val multiplexor = new IRTServerMultiplexor[IO, Unit, Unit](Set(greeter), ContextExtender.id)

    val req1 = new greeter.greet.signature.Input("John", "Doe")
    val json1 = req1.asJson
    println(json1)

    val req2 = new greeter.alternative.signature.Input()
    val json2 = req2.asJson
    println(json2)


    val invoked1 = multiplexor.doInvoke(json1, (), greeter.greet.signature.id)
    val invoked2 = multiplexor.doInvoke(json1, (), greeter.alternative.signature.id)

    object io extends DefaultRuntime

    println(io.unsafeRunSync(invoked1))
    println(io.unsafeRunSync(invoked2))
  }


}
