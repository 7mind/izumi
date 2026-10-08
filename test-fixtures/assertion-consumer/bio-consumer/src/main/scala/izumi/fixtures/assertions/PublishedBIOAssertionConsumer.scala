package izumi.fixtures.assertions

import izumi.fundamentals.assertions.{Assert, AssertionFailure}
import izumi.fundamentals.assertions.bio.BIOAssertionSuspension.*
import zio.{ZIO, ZIOAppDefault}

object PublishedBIOAssertionConsumer extends ZIOAppDefault {
  override def run: ZIO[Any, Nothing, Unit] = ZIO.suspendSucceed {
    var executions = 0
    val effect: ZIO[Any, Nothing, Unit] = Assert.assert2[zio.IO]({ executions += 1; false })
    if (executions != 0) throw new IllegalStateException("Published binary assertion evaluated during construction")
    for {
      first <- effect.exit
      second <- effect.exit
      _ <- ZIO.succeed {
        val firstFailure = first.causeOption.flatMap(_.dieOption)
        val secondFailure = second.causeOption.flatMap(_.dieOption)
        if (executions != 2 || !firstFailure.exists(_.isInstanceOf[AssertionFailure]) || !secondFailure.exists(_.isInstanceOf[AssertionFailure]) || firstFailure == secondFailure) {
          throw new IllegalStateException("Published binary assertion lost deferred independent defects")
        }
        if (first.causeOption.exists(_.failureOption.nonEmpty) || second.causeOption.exists(_.failureOption.nonEmpty)) {
          throw new IllegalStateException("Published binary assertion changed the typed error channel")
        }
        println("PUBLISHED_BIO_ASSERTION_CONSUMER_OK binary=true defects=true")
      }
    } yield ()
  }
}
