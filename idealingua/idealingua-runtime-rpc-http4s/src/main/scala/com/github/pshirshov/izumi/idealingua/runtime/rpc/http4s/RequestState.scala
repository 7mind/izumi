package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import java.util.concurrent.ConcurrentHashMap

import com.github.pshirshov.izumi.functional.bio.BIOAsync
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import io.circe.Json
import com.github.pshirshov.izumi.functional.bio.BIO._

import scala.concurrent.duration.FiniteDuration

class RequestState[Or[+ _, + _] : BIOAsync] {
  val R: BIOAsync[Or] = implicitly

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

  def handleResponse(maybePacketId: Option[RpcPacketId], data: Json): Or[Throwable, PacketInfo] = {
    for {
      maybeMethod <- R.sync {
        for {
          id <- maybePacketId
          method <- methodOf(id)
        } yield PacketInfo(method, id)
      }

      method <- maybeMethod match {
        case Some(m@PacketInfo(method, id)) =>
          respond(id, RawResponse.GoodRawResponse(data, method))
          R.now(m)

        case None =>
          R.fail(new IRTMissingHandlerException(s"Cannot handle response for async request $maybePacketId: no service handler", data))
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
              R.fail(())
            case Some(value) =>
              R.now(Some(value))
          }
      }
      .retryOrElse(timeout, R.now(None))
}
