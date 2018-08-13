package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.AuthMiddleware
import scalaz.zio.ExitResult


trait WithHttp4sServer {
  this: Http4sContext with WithHttp4sLoggingMiddleware =>

  class HttpServer[Ctx](
                         protected val muxer: IRTServerMultiplexor[BIO, Ctx]
                         , protected val contextProvider: AuthMiddleware[CIO, Ctx]
                       ) {
    protected val dsl: Http4sDsl[CIO] = WithHttp4sServer.this.dsl

    import dsl._

    def service: HttpRoutes[CIO] = {
      val svc = AuthedService(handler())
      val aservice: HttpRoutes[CIO] = contextProvider(svc)
      loggingMiddle(aservice)
    }

    protected def handler(): PartialFunction[AuthedRequest[CIO, Ctx], CIO[Response[CIO]]] = {
      case request@GET -> Root / service / method as ctx =>
        val methodId = IRTMethodId(IRTServiceId(service), IRTMethodName(method))
        run(request, "{}", ctx, methodId)

      case request@POST -> Root / service / method as ctx =>
        val methodId = IRTMethodId(IRTServiceId(service), IRTMethodName(method))
        request.req.decode[String] {
          body =>
            run(request, body, ctx, methodId)
        }
    }

    protected def run(request: AuthedRequest[CIO, Ctx], body: String, context: Ctx, toInvoke: IRTMethodId): CIO[Response[CIO]] = {
      muxer.doInvoke(body, context, toInvoke) match {
        case Right(Some(value)) =>
          ZIOR.unsafeRunSync(value) match {
            case ExitResult.Completed(v) =>
              dsl.Ok(v)

            case ExitResult.Failed(error, _) =>
              handleError(request, List(error), "failure")

            case ExitResult.Terminated(causes) =>
              handleError(request, causes, "termination")
          }

        case Right(None) =>
          logger.trace(s"No handler for $request")
          dsl.NotFound()

        case Left(e) =>
          logger.trace(s"Parsing failure while handling $request: $e")
          dsl.BadRequest()

      }
    }

    private def handleError(request: AuthedRequest[CIO, Ctx], causes: List[Throwable], kind: String): CIO[Response[CIO]] = {
      causes.headOption match {
        case Some(cause: IRTHttpFailureException) =>
          logger.debug(s"Request rejected, $request, $cause")
          CIO.pure(Response(status = cause.status))

        case cause =>
          logger.error(s"Execution failed, $kind, $request, $cause")
          dsl.InternalServerError()
      }
    }
  }

}
