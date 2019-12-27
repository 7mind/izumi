package izumi.distage.testkit.services.scalatest.dstest

import java.util.concurrent.ConcurrentHashMap

import distage.{SafeType, TagK}
import izumi.distage.testkit.services.dstest.DistageTestRunner.DistageTest
import izumi.fundamentals.platform.language.Quirks._
import org.scalatest.{StatefulStatus, Tracker}

import scala.collection.mutable

object DistageTestsRegistrySingleton {
  private[DistageTestsRegistrySingleton] type Fake[T]
  private[this] val registry = new mutable.HashMap[SafeType, mutable.ArrayBuffer[DistageTest[Fake]]]()
  private[this] val statuses = new mutable.HashMap[String, StatefulStatus]()
  private[this] val trackers = new mutable.HashMap[String, Either[mutable.ArrayBuffer[Tracker => Unit], Tracker]]()
  private[this] val runTracker = new ConcurrentHashMap[SafeType, Boolean]()

  def list[F[_]: TagK]: Seq[DistageTest[F]] = synchronized {
    registry.getOrElseUpdate(SafeType.getK[F], mutable.ArrayBuffer.empty).map(_.asInstanceOf[DistageTest[F]]).toSeq
  }

  def register[F[_]: TagK](t: DistageTest[F]): Unit = synchronized {
    registry.getOrElseUpdate(SafeType.getK[F], mutable.ArrayBuffer.empty).append(t.asInstanceOf[DistageTest[Fake]]).discard()
  }

  def ticketToProceed[F[_]: TagK](): Boolean = {
    val tpe = SafeType.getK[F]
    !runTracker.putIfAbsent(tpe, true)
  }

  def registerStatus(suiteId: String, status: StatefulStatus): Unit = synchronized {
    statuses(suiteId) = status
  }

  def completeStatuses(suiteIds: Set[String]): Unit = synchronized {
    suiteIds.foreach(statuses.get(_).foreach(_.setCompleted()))
  }

  def runReport(suiteId: String)(f: Tracker => Unit): Unit = synchronized {
    trackers.getOrElseUpdate(suiteId, Left(mutable.ArrayBuffer.empty)) match {
      case Left(reports) =>
        reports += f
      case Right(tracker) =>
        f(tracker)
    }
    ()
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
