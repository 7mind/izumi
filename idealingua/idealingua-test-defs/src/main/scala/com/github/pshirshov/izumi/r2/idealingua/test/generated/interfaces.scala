package com.github.pshirshov.izumi.r2.idealingua.test.generated

import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import com.github.pshirshov.izumi.r2.idealingua.test.impls
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._
import scalaz.zio.{ExitResult, IO, RTS}

trait GreeterServiceClient extends IRTResult {
  def greet(name: String, surname: String): Just[String]

  def sayhi(): Just[String]

  def nothing(): Just[Unit]

  def alternative(): Or[Long, String]
}

trait GreeterServiceServer[C] extends IRTResult {
  def greet(ctx: C, name: String, surname: String): Just[String]

  def sayhi(ctx: C): Just[String]

  def nothing(ctx: C): Just[String]

  def alternative(ctx: C): Or[Long, String]
}


class GreeterServiceClientWrapped[C](dispatcher: Dispatcher)
  extends GreeterServiceClient
    with IRTZioResult {
  override def greet(name: String, surname: String): IO[Nothing, String] = {
    dispatcher
      .dispatch(IRTMuxRequest(GreeterServiceMethods.mgreet.Input(name, surname), GreeterServiceMethods.mgreet.id))
      .redeem({ err => IO.terminate(err) }, { case IRTMuxResponse(IRTResBody(v: GreeterServiceMethods.mgreet.Output), method) if method == GreeterServiceMethods.mgreet.id =>
        IO.point(v.value)
      case v =>
        IO.terminate(new RuntimeException(s"wtf: $v, ${v.v.getClass}"))
      })

  }


  override def alternative(): IO[Long, String] = {
    dispatcher.dispatch(IRTMuxRequest(GreeterServiceMethods.malternative.Input(), GreeterServiceMethods.malternative.id))
      .redeem({
        err => IO.terminate(err)
      }, {
        case IRTMuxResponse(IRTResBody(v: GreeterServiceMethods.malternative.Output), method) if method == GreeterServiceMethods.malternative.id =>
          v match {
            case Left(va) =>
              IO.fail(va.value)

            case Right(va) =>
              IO.point(va.value)
          }
        case _ =>
          IO.terminate(new RuntimeException())
      })
  }

  override def sayhi(): IO[Nothing, String] = ???

  override def nothing(): IO[Nothing, Unit] = ???
}

object GreeterServiceMethods {
  val serviceId: IRTServiceId = IRTServiceId("GreeterService")

  object mgreet {
    val id: IRTMethodId = IRTMethodId(serviceId, IRTMethodName("greet"))

    case class Input(name: String, surname: String)

    object Input {
      implicit val encode: Encoder[Input] = deriveEncoder
      implicit val decode: Decoder[Input] = deriveDecoder
    }

    case class Output(value: String)

    object Output {
      implicit val encode: Encoder[Output] = deriveEncoder
      implicit val decode: Decoder[Output] = deriveDecoder
    }

  }

  object malternative {
    val id: IRTMethodId = IRTMethodId(serviceId, IRTMethodName("alternative"))

    type Output = Either[AlternativeOutput.Failure, AlternativeOutput.Success]

    case class Input()

    sealed trait AlternativeOutput

    object AlternativeOutput {

      final case class Failure(value: Long) extends AlternativeOutput

      final case class Success(value: String) extends AlternativeOutput

      implicit val encode: Encoder[AlternativeOutput] = deriveEncoder
      implicit val decode: Decoder[AlternativeOutput] = deriveDecoder
    }

    object Input {
      implicit val encode: Encoder[Input] = deriveEncoder
      implicit val decode: Decoder[Input] = deriveDecoder
    }

  }

}

class GreeterServiceServerWrapped[C](service: GreeterServiceServer[C] with IRTZioResult)
  extends IRTWrappedService[C]
    with IRTZioResult {

  object greet extends IRTMethodWrapper[C] with IRTMarshaller {

    override type Input = GreeterServiceMethods.mgreet.Input
    override type Output = GreeterServiceMethods.mgreet.Output

    override def id: IRTMethodId = GreeterServiceMethods.mgreet.id

    override def invoke(ctx: C, input: Input): Just[Output] = {
      service.greet(ctx, input.name, input.surname)
        .map(v => GreeterServiceMethods.mgreet.Output(v))
    }

    override def encodeRequest: PartialFunction[IRTReqBody, Json] = {
      case IRTReqBody(value: Input) => value.asJson
    }

    override def encodeResponse: PartialFunction[IRTResBody, Json] = {
      case IRTResBody(value: Output) => value.asJson
    }

    override def decodeRequest: PartialFunction[IRTRawCall, Just[IRTReqBody]] = {
      case IRTRawCall(m, packet) if m == id =>
        decoded(packet.as[Input].map(v => IRTReqBody(v)))
    }

    override def decodeResponse: PartialFunction[IRTRawCall, Just[IRTResBody]] = {
      case IRTRawCall(m, packet) if m == id =>
        decoded(packet.as[Output].map(v => IRTResBody(v)))
    }
  }

  object alternative extends IRTMethodWrapper[C] with IRTMarshaller {


    override type Input = GreeterServiceMethods.malternative.Input
    override type Output = GreeterServiceMethods.malternative.Output

    override def id: IRTMethodId = GreeterServiceMethods.malternative.id

    override def invoke(ctx: C, input: Input): Just[Output] = {
      service.alternative(ctx)
        .redeem(err => IO.point(Left(GreeterServiceMethods.malternative.AlternativeOutput.Failure(err))), succ => IO.point(Right(GreeterServiceMethods.malternative.AlternativeOutput.Success(succ))))
    }

    override def encodeRequest: PartialFunction[IRTReqBody, Json] = {
      case IRTReqBody(value: Input) => value.asJson
    }

    override def encodeResponse: PartialFunction[IRTResBody, Json] = {
      case IRTResBody(value: Output) =>
        val out: GreeterServiceMethods.malternative.AlternativeOutput = value match {
          case Left(v) =>
            v
          case Right(r) =>
            r
        }
        out.asJson
    }

    override def decodeRequest: PartialFunction[IRTRawCall, Just[IRTReqBody]] = {
      case IRTRawCall(m, packet) if m == id =>
        decoded(packet.as[Input].map(v => IRTReqBody(v)))
    }

    override def decodeResponse: PartialFunction[IRTRawCall, Just[IRTResBody]] = {
      case IRTRawCall(m, packet) if m == id =>
        decoded(packet.as[GreeterServiceMethods.malternative.AlternativeOutput].map {
          case v: GreeterServiceMethods.malternative.AlternativeOutput.Success => IRTResBody(Right(v))
          case v: GreeterServiceMethods.malternative.AlternativeOutput.Failure => IRTResBody(Left(v))
        })
    }
  }


  override def serviceId: IRTServiceId = GreeterServiceMethods.serviceId

  val allMethods: Map[IRTMethodId, IRTMethodWrapper[C]] = {
    Seq(
      greet
      , alternative
    )
      .map(m => m.id -> m).toMap
  }

  val allCodecs: Map[IRTMethodId, IRTMarshaller] = {
    Seq(
      greet
      , alternative
    )
      .map(m => m.id -> m).toMap
  }
}


object Test {
  def main(args: Array[String]): Unit = {
    val greeter = new GreeterServiceServerWrapped[Unit](new impls.AbstractGreeterServer.Impl[Unit]())
    val multiplexor = new IRTMultiplexor[Unit](Set(greeter))

    val req1 = new greeter.greet.Input("John", "Doe")
    val json1 = req1.asJson.noSpaces
    println(json1)

    val req2 = new greeter.alternative.Input()
    val json2 = req2.asJson.noSpaces
    println(json2)

    val toInvoke = greeter.greet.id


    val invoked = multiplexor.doInvoke(json1, (), toInvoke)

    object io extends RTS {
      override def defaultHandler: List[Throwable] => IO[Nothing, Unit] = _ => IO.sync(())
    }

    invoked match {
      case Right(Some(value)) =>
        io.unsafeRunSync(value) match {
          case ExitResult.Completed(v) =>
            println(("Success", v))
          case ExitResult.Failed(error, defects) =>
            println(("Failure", error))
          case ExitResult.Terminated(causes) =>
            println(("Termination", causes))
        }
      case Right(None) =>
      // 404
      case Left(e) =>
      // 500 -> bad content
    }
  }


}
