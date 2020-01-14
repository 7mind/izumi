package izumi.distage.testkit.services.scalatest.dstest

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

import distage.{SafeType, TagK}
import izumi.distage.testkit.services.dstest.DistageTestRunner.DistageTest
import izumi.fundamentals.platform.language.Quirks._
import org.scalatest.{StatefulStatus, Tracker}

import scala.collection.mutable

object DistageTestsRegistrySingleton {
  protected[this] type Fake[T]
  private[this] object Fake
  private[this] val registry = new mutable.HashMap[SafeType, mutable.ArrayBuffer[DistageTest[Fake]]]()
  private[this] val statuses = new mutable.HashMap[String, StatefulStatus]()
  private[this] val trackers = new mutable.HashMap[String, Either[mutable.ArrayBuffer[Tracker => Unit], Tracker]]()
  private[this] val runTracker = new ConcurrentHashMap[SafeType, Fake.type]()
  private[this] val knownSuites = new ConcurrentHashMap[(SafeType, String), Fake.type]()
  private[this] val registrationOpen = new AtomicBoolean(true)

  def disableRegistration(): Unit = {
    registrationOpen.set(false)
  }

  def registerSuite[F[_]: TagK](suiteId: String): Boolean = synchronized {
    val tpe = SafeType.getK[F]
    knownSuites.putIfAbsent((tpe, suiteId), Fake) eq null
  }

  def register[F[_]: TagK](t: DistageTest[F]): Unit = synchronized {
    if (registrationOpen.get()) {
      registry.getOrElseUpdate(SafeType.getK[F], mutable.ArrayBuffer.empty).append(t.asInstanceOf[DistageTest[Fake]]).discard()
    }
  }

  def proceedWithTests[F[_]: TagK](): Option[Seq[DistageTest[F]]] = {
    val tpe = SafeType.getK[F]
    if (runTracker.putIfAbsent(tpe, Fake) eq null) {
      Some(registeredTests[F])
    } else {
      None
    }
  }

  def registeredTests[F[_]: TagK]: Seq[DistageTest[F]] = synchronized {
    registry.getOrElseUpdate(SafeType.getK[F], mutable.ArrayBuffer.empty).map(_.asInstanceOf[DistageTest[F]]).toSeq
  }

  def registerStatus(suiteId: String): StatefulStatus = synchronized {
    statuses.getOrElseUpdate(suiteId, new StatefulStatus)
  }

  def completeStatuses(suiteIds: Set[String]): Unit = synchronized {
    suiteIds.foreach(statuses.get(_).foreach(_.setCompleted()))
  }

  def runReport(suiteId: String)(f: Tracker => Unit): Unit = synchronized {
    trackers.getOrElseUpdate(suiteId, Left(mutable.ArrayBuffer.empty)) match {
      case Left(reports) =>
        (reports += f).discard()
      case Right(tracker) =>
        f(tracker)
    }
  }

  def registerTracker(suiteId: String)(tracker: Tracker): Unit = synchronized {
    trackers.getOrElseUpdate(suiteId, Right(tracker)) match {
      case Left(reports) =>
        trackers(suiteId) = Right(tracker)
        reports.foreach(_.apply(tracker))
      case Right(_) =>
    }
  }
}
