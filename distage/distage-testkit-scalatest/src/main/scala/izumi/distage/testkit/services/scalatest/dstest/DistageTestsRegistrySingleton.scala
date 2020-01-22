package izumi.distage.testkit.services.scalatest.dstest

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

import distage.{SafeType, TagK}
import izumi.distage.testkit.services.dstest.DistageTestRunner.DistageTest
import izumi.functional.Value
import izumi.fundamentals.platform.language.Quirks._
import org.scalatest.{StatefulStatus, Tracker}

import scala.collection.mutable

object DistageTestsRegistrySingleton {
  protected[this] type Fake[T]
  private[this] object Fake
  private[this] val registry = new mutable.HashMap[SafeType, mutable.ArrayBuffer[DistageTest[Fake]]]()
  private[this] val statuses = new mutable.HashMap[SafeType, Option[mutable.HashMap[String, StatefulStatus]]]()
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
      registry
        .getOrElseUpdate(SafeType.getK[F], mutable.ArrayBuffer.empty)
        .append(castTest(t))
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
    val arr = registry.getOrElseUpdate(SafeType.getK[F], mutable.ArrayBuffer.empty)
    castArray(arr).toSeq
  }

  def registerStatus[F[_]: TagK](suiteId: String): StatefulStatus = synchronized {
    statuses
      .getOrElseUpdate(SafeType.getK[F], Some(mutable.HashMap.empty))
      .fold {
        // return completed test if the runner has already ran before this test got registered
        Value(new StatefulStatus).eff(_.setCompleted()).get
      } {
        _.getOrElseUpdate(suiteId, new StatefulStatus)
      }
  }

  def completeStatuses[F[_]: TagK](): Unit = synchronized {
    statuses.get(SafeType.getK[F])
      .flatten
      .foreach {
        _.valuesIterator.foreach {
          status =>
            if (!status.isCompleted) {
              status.setCompleted()
            }
        }
      }
    statuses.put(SafeType.getK[F], None).discard()
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

  @inline private[this] def castTest[F[_]](t: DistageTest[F]): DistageTest[Fake] = t.asInstanceOf[DistageTest[Fake]]
  @inline private[this] def castArray[C[_], F[_]](a: C[DistageTest[Fake]]): C[DistageTest[F]] = a.asInstanceOf[C[DistageTest[F]]]
}
