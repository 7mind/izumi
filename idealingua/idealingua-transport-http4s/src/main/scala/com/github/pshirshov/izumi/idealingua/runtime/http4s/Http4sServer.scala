package com.github.pshirshov.izumi.idealingua.runtime.http4s

import cats.data.{Kleisli, NonEmptyList, OptionT}
import cats.effect.IO
import fs2.StreamApp
import org.http4s.{AuthedService, HttpService}
import org.http4s.dsl.Http4sDsl
import org.http4s.server.blaze.BlazeBuilder

trait DefaultAuthFailure[Error] extends Http4sDsl[IO] {
  val onFailure: AuthedService[NonEmptyList[Error], IO] = Kleisli({
    _ => OptionT.liftF(Forbidden())
  })
}

abstract class Http4sServer() extends StreamApp[IO] with Http4sDsl[IO] {
  val services: Map[HttpService[IO], String]

  def stream(args: List[String], requestShutdown: IO[Unit]): fs2.Stream[IO, StreamApp.ExitCode] = {
    import scala.concurrent.ExecutionContext.Implicits.global

    services
      .foldLeft(BlazeBuilder[IO].bindHttp(8080, "0.0.0.0")){
        case (server, (service, path)) => server.mountService(service, path)
      }
      .serve
  }
}