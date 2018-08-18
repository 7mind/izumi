package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import java.util.concurrent.ConcurrentHashMap

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.idealingua.runtime.rpc.{IRTMethodId, IRTMissingHandlerException, RpcPacketId}
import io.circe.Json
import scalaz.zio.{IO, Retry}

import scala.concurrent.duration.FiniteDuration

class RequestState {
  // TODO: stale item cleanups
  protected val requests: ConcurrentHashMap[RpcPacketId, IRTMethodId] = new ConcurrentHashMap[RpcPacketId, IRTMethodId]()
  protected val responses: ConcurrentHashMap[RpcPacketId, RawResponse] = new ConcurrentHashMap[RpcPacketId, RawResponse]()

  def methodOf(id: RpcPacketId): Option[IRTMethodId] = {
    Option(requests.get(id))
  }

  def request(id: RpcPacketId, methodId: IRTMethodId): Unit = {
    Quirks.discard(requests.put(id, methodId))
  }

  def checkResponse(id: RpcPacketId): Option[RawResponse] = {
    Option(responses.get(id))
  }

  def forget(id: RpcPacketId): Unit = {
    Quirks.discard(requests.remove(id), responses.remove(id))
  }

  def respond(id: RpcPacketId, response: RawResponse): Unit = {
    Option(requests.remove(id)) match {
      case Some(_) =>
        Quirks.discard(responses.put(id, response))
      case None =>
        // We ignore responses for unknown requests
    }
  }

  def clear(): Unit = {
    // TODO: autocloseable + latch?
    Quirks.discard(requests.clear(), responses.clear())
  }

  def handleResponse(mid: Option[RpcPacketId], data: Json): IO[Throwable, (RpcPacketId, IRTMethodId)] = {
    for {
      maybeMethod <- IO.sync {
        for {
          id <- mid
          method <- methodOf(id)
        } yield (id, method)
      }
      method <- maybeMethod match {
        case Some((id, method)) =>
          respond(id, RawResponse(data, method))
          IO.point((id, method))
        case None =>
          IO.fail(new IRTMissingHandlerException(s"No handler for $mid", data))
      }
    } yield {
      method
    }
  }

  def poll(id: RpcPacketId, interval: FiniteDuration, timeout: FiniteDuration): IO[Nothing, Option[RawResponse]] = {
    IO.sleep(interval)
      .flatMap {
        _ =>
          checkResponse(id) match {
            case None =>
              IO.fail(None)
            case Some(value) =>
              IO.point(Some(value))
          }
      }
      .retryOrElse(Retry.duration(timeout), {
        (_: Any, _: Any) =>
          IO.point(None)
      })
  }
}
