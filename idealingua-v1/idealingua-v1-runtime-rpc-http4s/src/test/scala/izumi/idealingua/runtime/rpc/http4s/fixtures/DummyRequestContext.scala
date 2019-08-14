package izumi.idealingua.runtime.rpc.http4s.fixtures

import org.http4s.Credentials

final case class DummyRequestContext(ip: String, credentials: Option[Credentials])
