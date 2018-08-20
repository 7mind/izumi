package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import java.util.concurrent.ConcurrentHashMap

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.idealingua.runtime.bio.BIO
import com.github.pshirshov.izumi.idealingua.runtime.bio.BIO._
import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import io.circe.Json

import scala.concurrent.duration.FiniteDuration
import scala.language.higherKinds

class RequestState[Or[+ _, + _] : BIO] {
  val R: BIO[Or] = implicitly
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

  def handleResponse(mid: Option[RpcPacketId], data: Json): Or[Throwable, (RpcPacketId, IRTMethodId)] = {
    for {
      maybeMethod <- R.sync {
        for {
          id <- mid
          method <- methodOf(id)
        } yield (id, method)
      }
      method <- maybeMethod match {
        case Some((id, method)) =>
          respond(id, RawResponse(data, method))
          R.point((id, method))
        case None =>
          R.fail(new IRTMissingHandlerException(s"No handler for $mid", data))
      }
    } yield {
      method
    }
  }

  def poll(id: RpcPacketId, interval: FiniteDuration, timeout: FiniteDuration): Or[Nothing, Option[RawResponse]] =
    R.sleep(interval)
      .flatMap {
        _ =>
          checkResponse(id) match {
            case None =>
              R.fail(None)
            case Some(value) =>
              R.point(Some(value))
          }
      }
      .retryOrElse(timeout, R.point(None))
}
