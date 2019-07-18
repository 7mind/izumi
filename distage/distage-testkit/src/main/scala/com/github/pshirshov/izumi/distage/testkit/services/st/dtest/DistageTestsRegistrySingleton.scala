package com.github.pshirshov.izumi.distage.testkit.services.st.dtest

import java.util.concurrent.ConcurrentHashMap

import com.github.pshirshov.izumi.distage.testkit.services.dstest.DistageTestRunner.DistageTest
import distage.{SafeType, TagK}

import scala.collection.mutable

object DistageTestsRegistrySingleton {
  private type Fake[T] = T
  private val registry = new mutable.HashMap[SafeType, mutable.ArrayBuffer[DistageTest[Fake]]]()

  def list[F[_]: TagK]: Seq[DistageTest[F]] = synchronized {
    registry.getOrElseUpdate(SafeType.getK[F], mutable.ArrayBuffer.empty).map(_.asInstanceOf[DistageTest[F]])
  }

  def register[F[_]: TagK](t: DistageTest[F]): Unit = synchronized {
    registry.getOrElseUpdate(SafeType.getK[F], mutable.ArrayBuffer.empty).append(t.asInstanceOf[DistageTest[Fake]])
  }


  private def runTracker = new ConcurrentHashMap[SafeType, Boolean]()

  def ticketToProceed[F[_]: TagK](): Boolean = {
    val tpe = SafeType.getK[F]
    !runTracker.putIfAbsent(tpe, true)
  }
}
