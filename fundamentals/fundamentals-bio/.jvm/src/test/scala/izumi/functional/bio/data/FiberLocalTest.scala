package izumi.functional.bio.data

import izumi.functional.bio.{Async2, F, PrimitivesLocal2, UnsafeRun2}
import org.scalatest.wordspec.AnyWordSpec

class FiberLocalTestZIO
  extends FiberLocalTestBase[zio.IO](
    runner = UnsafeRun2.createZIO()
  )

abstract class FiberLocalTestBase[F[+_, +_]: Async2: PrimitivesLocal2](
  runner: UnsafeRun2[F]
) extends AnyWordSpec {

  "FiberLocal" should {

    "create ref, modify, get" in {
      val effect = for {
        valueRef <- F.mkFiberLocal[Int](21)
        get <- valueRef.get
        get2 <- valueRef.locallyWith(_ * 2)(valueRef.get)
        _ <- F.sync(assert(get == 21))
        _ <- F.sync(assert(get2 == 42))
      } yield ()
      runner.unsafeRun(effect)
    }

    "use ref in parTraverse" in {
      val effect = for {
        valueRef <- F.mkFiberLocal[Int](10)
        values <- valueRef.locallyWith(_ * 2)(
          F.parTraverse(1 to 10)(i => valueRef.locallyWith(_ + i)(valueRef.get))
        )
        get <- valueRef.get
        _ <- F.sync(assert(values == (1 to 10).map(i => get * 2 + i).toList))
      } yield ()
      runner.unsafeRun(effect)
    }

  }

}
