package izumi.fundamentals.bio

import cats.Eq
import cats.effect.{Concurrent, ContextShift}
import cats.effect.laws.ConcurrentLaws
import cats.effect.laws.discipline.ConcurrentTests
import cats.effect.laws.util.TestContext
import izumi.functional.bio.BIO
import org.scalacheck.{Arbitrary, Cogen, Gen}
import zio.{Runtime, ZIO}
import zio.internal.{Executor, Platform, Tracing}
import izumi.functional.bio.catz._
import izumi.fundamentals.bio.env.ZIOTestEnv

import scala.util.Try

import scala.concurrent.ExecutionContext

class BioZioLawsTest extends CatsLawsTestBase with ZIOTestEnv {
  val concurrentTestZio = new ConcurrentTests[zio.Task] {
    val laws = new ConcurrentLaws[zio.Task] {
      val F = Concurrent[zio.Task]
      import zio.interop.catz.zioContextShift
      val contextShift = ContextShift[zio.Task]
    }
  }

  checkAll("ConcurrentZIO", concurrentTestZio.sync[Int, Int, Int])
}
