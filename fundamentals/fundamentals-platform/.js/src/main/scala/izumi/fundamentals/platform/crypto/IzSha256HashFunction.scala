package izumi.fundamentals.platform.crypto

import scala.annotation.unused
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.annotation.{JSGlobal, JSImport}
import scala.scalajs.js.typedarray.Int8Array

class IzSha256HashFunction(impl: () => ScalaJSSHA256) extends IzHashFunction {
  override def hash(bytes: Array[Byte]): Array[Byte] = {
    val sha256 = impl()
    sha256.update(new Int8Array(bytes.toJSArray))
    val hexdigest = sha256.digest("hex")
    hexdigest.sliding(2, 2).toArray.map(Integer.parseInt(_, 16).toByte)
  }

  override def id: IzHashId = IzHashId.SHA_256
}

object IzSha256HashFunction extends IzHashFunction {
  override def hash(bytes: Array[Byte]): Array[Byte] = getImpl.hash(bytes)
  override def id: IzHashId = getImpl.id

  // scalajs fuckery
  def getImpl: IzSha256HashFunction = synchronized {
    impl
  }

  private var impl: IzSha256HashFunction = Global

  def setImported(): Unit = synchronized {
    impl = Imported
  }

  object Global extends IzSha256HashFunction(() => new ScalaJSSHA256.GlobalSHA256())
  object Imported extends IzSha256HashFunction(() => new ScalaJSSHA256.ImportedSHA256())

}

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
