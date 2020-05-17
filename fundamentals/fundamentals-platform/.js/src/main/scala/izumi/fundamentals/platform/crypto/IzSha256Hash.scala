package izumi.fundamentals.platform.crypto

import izumi.fundamentals.platform
import izumi.fundamentals.platform.crypto
import izumi.fundamentals.platform.language.unused

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.{JSGlobal, JSImport}
import scala.scalajs.js.typedarray.Int8Array

@js.native
trait ScalaJSSHA256 extends js.Any {
  def update(@unused msg: Int8Array): Unit = js.native
  def digest(@unused enc: String): String = js.native
}

object ScalaJSSHA256 {
  @js.native
  @JSImport("hash.js", "sha256")
  class ImportedSHA256() extends js.Object with ScalaJSSHA256

  @js.native
  @JSGlobal("sha256")
  class GlobalSHA256() extends js.Object with ScalaJSSHA256
}

class IzSha256Hash(impl: () => ScalaJSSHA256) extends IzHash {
  override def hash(bytes: Array[Byte]): Array[Byte] = {
    val sha256 = impl()
    sha256.update(new Int8Array(bytes.toJSArray))
    val hexdigest = sha256.digest("hex")
    hexdigest.sliding(2, 2).toArray.map(Integer.parseInt(_, 16).toByte)
  }
}

object IzSha256Hash {
  // scalajs fuckery
  private var impl: IzSha256Hash = Global
  def setImported(): Unit = synchronized {
    impl = Imported
  }
  def getImpl: IzSha256Hash = synchronized {
    impl
  }

  object Global extends IzSha256Hash(() => new crypto.ScalaJSSHA256.GlobalSHA256())
  object Imported extends IzSha256Hash(() => new platform.crypto.ScalaJSSHA256.ImportedSHA256())
}
