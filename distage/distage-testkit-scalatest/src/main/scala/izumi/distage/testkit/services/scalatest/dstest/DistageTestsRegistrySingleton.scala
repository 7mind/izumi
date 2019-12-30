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
  private[this] val registrationOpen = new AtomicBoolean(true)


  def disableRegistration(): Unit = {
    registrationOpen.set(false)
  }

  def list[F[_] : TagK]: Seq[DistageTest[F]] = synchronized {
    registry.getOrElseUpdate(SafeType.getK[F], mutable.ArrayBuffer.empty).map(_.asInstanceOf[DistageTest[F]]).toSeq
  }

  def register[F[_] : TagK](t: DistageTest[F]): Unit = synchronized {
    if (registrationOpen.get()) {
      println("Test registered")
      registry.getOrElseUpdate(SafeType.getK[F], mutable.ArrayBuffer.empty).append(t.asInstanceOf[DistageTest[Fake]]).discard()
    } else {
      println("test ignored")
    }
  }

  def ticketToProceed[F[_] : TagK](): Boolean = {
    val tpe = SafeType.getK[F]
    !runTracker.putIfAbsent(tpe, true)
  }
}
