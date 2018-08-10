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

final case class IRTRawCall(methodId: IRTMethodId, body: Json)


trait Method[C] extends IRTZioResult {
  type Input
  type Output

  def id: IRTMethodId

  def invoke(ctx: C, input: Input): Just[Output]

  def encodeRequest: PartialFunction[IRTReqBody, Json]

  def encodeResponse: PartialFunction[IRTResBody, Json]

  def decodeRequest: PartialFunction[IRTRawCall, Just[IRTReqBody]]

  def decodeResponse: PartialFunction[IRTRawCall, Just[IRTResBody]]

  protected def decoded[V](result: Either[DecodingFailure, V]): Just[V] = {
    result match {
      case Left(f) =>
        IO.terminate(f)
      case Right(r) =>
        IO.point(r)
    }
  }
}

trait WrappedService[C] {
  def serviceId: IRTServiceId

  def allMethods: Map[IRTMethodId, Method[C]]
}


class GreeterServiceWrapped[C](service: GreeterServiceServer[C] with IRTZioResult)
  extends WrappedService[C]
    with IRTZioResult {

  object greet extends Method[C] {
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

    override def invoke(ctx: C, input: Input): Just[Output] = {
      service.greet(ctx, input.name, input.surname)
        .map(v => Output(v))
    }

    override def encodeRequest: PartialFunction[IRTReqBody, Json] = {
      case IRTReqBody(value: Input) => value.asJson
    }

    override def encodeResponse: PartialFunction[IRTResBody, Json] = {
      case IRTResBody(value: Input) => value.asJson
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

  object alternative extends Method[C] {
    val id: IRTMethodId = IRTMethodId(serviceId, IRTMethodName("alternative"))

    type Output = Either[AlternativeOutput.Failure, AlternativeOutput.Success]

    case class Input()

    sealed trait AlternativeOutput

    object AlternativeOutput {

      case class Failure(value: Long) extends AlternativeOutput

      case class Success(value: String) extends AlternativeOutput

      implicit val encode: Encoder[AlternativeOutput] = deriveEncoder
      implicit val decode: Decoder[AlternativeOutput] = deriveDecoder
    }

    object Input {
      implicit val encode: Encoder[Input] = deriveEncoder
      implicit val decode: Decoder[Input] = deriveDecoder
    }

    override def invoke(ctx: C, input: Input): Just[Output] = {
      service.alternative(ctx)
        .redeem(err => IO.point(Left(AlternativeOutput.Failure(err))), succ => IO.point(Right(AlternativeOutput.Success(succ))))
    }

    override def encodeRequest: PartialFunction[IRTReqBody, Json] = {
      case IRTReqBody(value: Input) => value.asJson
    }

    override def encodeResponse: PartialFunction[IRTResBody, Json] = {
      case IRTResBody(value: Input) => value.asJson
    }

    override def decodeRequest: PartialFunction[IRTRawCall, Just[IRTReqBody]] = {
      case IRTRawCall(m, packet) if m == id =>
        decoded(packet.as[Input].map(v => IRTReqBody(v)))
    }

    override def decodeResponse: PartialFunction[IRTRawCall, Just[IRTResBody]] = {
      case IRTRawCall(m, packet) if m == id =>
        decoded(packet.as[AlternativeOutput].map {
          case v: AlternativeOutput.Success => IRTResBody(Right(v))
          case v: AlternativeOutput.Failure => IRTResBody(Left(v))
        })
    }
  }

  val serviceId: IRTServiceId = IRTServiceId("GreeterService")

  def allMethods: Map[IRTMethodId, Method[C]] = Seq(greet, alternative).map(m => m.id -> m).toMap
}

object Test {
  def main(args: Array[String]): Unit = {
    val greeter = new GreeterServiceWrapped[Unit](new impls.AbstractGreeterServer.Impl[Unit]())
    val services: Map[IRTServiceId, WrappedService[Unit]] = Seq(greeter).map(s => s.serviceId -> s).toMap


    val req1 = new greeter.greet.Input("John", "Doe")
    val json1 = req1.asJson.noSpaces
    println(json1)

    val req2 = new greeter.alternative.Input()
    val json2 = req2.asJson.noSpaces
    println(json2)

    val toInvoke = greeter.greet.id


    val invoked = doInvoke(services, json1, toInvoke)

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

  private def doInvoke(services: Map[IRTServiceId, WrappedService[Unit]], json1: String, toInvoke: IRTMethodId) = {
    val invoked =
      _root_.io.circe.parser.parse(json1).map {
        parsed =>
          services
            .get(toInvoke.service)
            .flatMap(_.allMethods.get(toInvoke))
            .map {
              method =>
                method.decodeRequest
                  .apply(IRTRawCall(toInvoke, parsed))
                  .flatMap {
                    request =>
                      IO.syncThrowable(request.value.asInstanceOf[method.Input])
                  }
                  .flatMap {
                    request =>
                      IO.syncThrowable(method.invoke((), request))
                  }
                  .flatMap {
                    v =>
                      v
                  }
            }
      }
    invoked
  }
}
