package izumi.idealingua.runtime.rpc.http4s

import java.util.UUID

case class WsSessionId(sessionId: UUID) extends AnyVal

case class WsClientId[ClientId](sessionId: WsSessionId, id: Option[ClientId]) {
  override def toString: String = s"${id.getOrElse("?")} / ${sessionId.sessionId}"
}
