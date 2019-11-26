package izumi.idealingua.runtime.rpc.http4s.fixtures

import java.util.concurrent.atomic.AtomicReference

import org.http4s.headers.Authorization
import org.http4s.{BasicCredentials, Header}

trait TestDispatcher {
  val creds = new AtomicReference[Seq[Header]](Seq.empty)

  def setupCredentials(login: String, password: String): Unit = {
    creds.set(Seq(Authorization(BasicCredentials(login, password))))
  }

  def cancelCredentials(): Unit = {
    creds.set(Seq.empty)
  }
}






