package izumi.fundamentals.bio

import cats.effect.laws.discipline.{ConcurrentTests, SyncTests}
import cats.effect.laws.util.TestInstances
import cats.effect.laws.{ConcurrentLaws, SyncLaws}
import cats.effect.{Concurrent, ContextShift, Sync}
import izumi.functional.bio.catz._
import izumi.functional.bio.impl.MiniBIO
import monix.bio.Task
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.Checkers
import org.typelevel.discipline.scalatest.FunSuiteDiscipline

class MiniBIOCatsLaws extends AnyFunSuite with FunSuiteDiscipline with TestInstances with Checkers with Rnd {
  val syncTests = new SyncTests[MiniBIO[Throwable, ?]] {
    val laws = new SyncLaws[MiniBIO[Throwable, ?]] {
      val F = Sync[MiniBIO[Throwable, ?]]
    }
  }


  val concurrentTestsMonix = new ConcurrentTests[monix.bio.Task] {
    override val laws = new ConcurrentLaws[Task] {
      val F = Concurrent[Task]
      val contextShift = ContextShift[Task]
    }
  }

  val concurrentTestZio = new ConcurrentTests[zio.Task] {
    val laws = new ConcurrentLaws[zio.Task] {
      val F = Concurrent[zio.Task]
      import zio.interop.catz._
      val contextShift = ContextShift[zio.Task]
    }
  }


  checkAll("Sync[MiniBIO]", syncTests.sync[Int, Int, Int])
  //checkAll("ConcurrentMonix", concurrentTestsMonix.sync[Int, Int, Int])
  //checkAll("ConcurrentZIO", concurrentTestZio.sync[Int, Int, Int])
}
