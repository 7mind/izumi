package izumi.distage.testkit.services.st.dtest

import java.util.concurrent.ConcurrentHashMap

import distage.{SafeType, TagK}
import izumi.distage.testkit.services.dstest.DistageTestRunner.DistageTest
import izumi.fundamentals.platform.language.Quirks._

import scala.collection.mutable

object DistageTestsRegistrySingleton {
  private[DistageTestsRegistrySingleton] type Fake[T]
  private val registry = new mutable.HashMap[SafeType, mutable.ArrayBuffer[DistageTest[Fake]]]()

  def list[F[_]: TagK]: Seq[DistageTest[F]] = synchronized {
    registry.getOrElseUpdate(SafeType.getK[F], mutable.ArrayBuffer.empty).map(_.asInstanceOf[DistageTest[F]]).toSeq
  }

  def register[F[_]: TagK](t: DistageTest[F]): Unit = synchronized {
    registry.getOrElseUpdate(SafeType.getK[F], mutable.ArrayBuffer.empty).append(t.asInstanceOf[DistageTest[Fake]]).discard()
  }

  private val runTracker = new ConcurrentHashMap[SafeType, Boolean]()

  def ticketToProceed[F[_]: TagK](): Boolean = {
    val tpe = SafeType.getK[F]
    !runTracker.putIfAbsent(tpe, true)
  }
}
