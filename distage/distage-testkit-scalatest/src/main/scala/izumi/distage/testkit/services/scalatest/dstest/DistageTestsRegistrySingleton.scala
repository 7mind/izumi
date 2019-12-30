package izumi.distage.testkit.services.scalatest.dstest

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

import distage.{SafeType, TagK}
import izumi.distage.testkit.services.dstest.DistageTestRunner.DistageTest
import izumi.fundamentals.platform.language.Quirks._

import scala.collection.mutable

object DistageTestsRegistrySingleton {
  private[DistageTestsRegistrySingleton] type Fake[T]
  private[this] val registry = new mutable.HashMap[SafeType, mutable.ArrayBuffer[DistageTest[Fake]]]()
  private[this] val runTracker = new ConcurrentHashMap[SafeType, Boolean]()
  private[this] val knownSuites = new ConcurrentHashMap[(SafeType, String), Boolean]()
  private[this] val registrationOpen = new AtomicBoolean(true)


  def disableRegistration(): Unit = {
    registrationOpen.set(false)
  }

  def registerSuite[F[_]: TagK](suiteId: String): Boolean = synchronized {
    val tpe = SafeType.getK[F]
    if (!knownSuites.putIfAbsent((tpe, suiteId), true)) {
      true
    } else {
      false
    }
  }
  def register[F[_] : TagK](t: DistageTest[F]): Unit = synchronized {
    if (registrationOpen.get()) {
      registry.getOrElseUpdate(SafeType.getK[F], mutable.ArrayBuffer.empty).append(t.asInstanceOf[DistageTest[Fake]]).discard()
    }
  }

  def proceedWithTests[F[_] : TagK](): Option[Seq[DistageTest[F]]] = {
    val tpe = SafeType.getK[F]
    if (!runTracker.putIfAbsent(tpe, true)) {
      Some(list[F])
    } else {
      None
    }
  }

  def list[F[_] : TagK]: Seq[DistageTest[F]] = synchronized {
    registry.getOrElseUpdate(SafeType.getK[F], mutable.ArrayBuffer.empty).map(_.asInstanceOf[DistageTest[F]]).toSeq
  }
}
