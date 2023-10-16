package izumi.distage.testkit.scalatest

import distage.{DefaultModule3, TagK3, TagKK}
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec
import izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec.DSWordSpecStringWrapperZIO
import izumi.logstage.distage.LogIO2Module
import org.scalatest.distage.DistageScalatestTestSuiteRunner
import zio.ZIO

import scala.language.implicitConversions

/**
  * Allows summoning objects from DI in tests via ZIO environment intersection types
  *
  * {{{
  *   trait PetStore[F[_, _]] {
  *     def purchasePet(name: String, cost: Int): F[Throwable, Boolean]
  *   }
  *
  *   trait Pets[F[_, _]]
  *     def myPets: F[Throwable, List[String]]
  *   }
  *
  *   val store = new PetStore[ZIO[PetStore[IO], _, _]] {
  *     def purchasePet(name: String, cost: Int): RIO[PetStore[IO], Boolean] = ZIO.accessM(_.get.purchasePet(name, cost))
  *   }
  *   val pets = new Pets[ZIO[Pets[IO], _, _]] {
  *     def myPets: RIO[Pets[IO], List[String]] = ZIO.accessM(_.get.myPets)
  *   }
  *
  *   "test purchase pets" in {
  *     for {
  *       _    <- store.purchasePet("Zab", 213)
  *       pets <- pets.myPets
  *       _    <- assertIO(pets.contains("Zab"))
  *     } yield ()
  *     // : ZIO[PetStore[IO] with Pets[IO], Throwable, Unit]
  *   }
  * }}}
  *
  * Lambda parameters and environment may both be used at the same time to define dependencies:
  *
  * {{{
  *   "test purchase pets" in {
  *     (store: PetStore[IO]) =>
  *       for {
  *         _    <- store.purchasePet("Zab", cost = 213)
  *         pets <- pets.myPets
  *         _    <- assertIO(pets.contains("Zab"))
  *       } yield ()
  *       // : ZIO[PetsEnv, Throwable, Unit]
  *   }
  * }}}
  */
abstract class SpecZIO(implicit val defaultModule3: DefaultModule3[ZIO], val tagBIO3: TagK3[ZIO], val tagBIO: TagKK[ZIO[Any, _, _]])
  extends DistageScalatestTestSuiteRunner[ZIO[Any, Throwable, _]]
  with DistageAbstractScalatestSpec[ZIO[Any, Throwable, _]] {

  protected implicit def convertToWordSpecStringWrapperDS3(s: String): DSWordSpecStringWrapperZIO = {
    new DSWordSpecStringWrapperZIO(context, distageSuiteName, distageSuiteId, Seq(s), this, testEnv)
  }

  override protected def config: TestConfig = super.config.copy(
    moduleOverrides = LogIO2Module[ZIO[Any, _, _]]()(tagBIO)
  )
}
