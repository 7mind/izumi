package izumi.distage.testkit.scalatest

import distage.{TagK3, TagKK}
import izumi.distage.effect.DefaultModule3
import izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec
import izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec.{DSWordSpecStringWrapper, DSWordSpecStringWrapper3}
import org.scalatest.distage.DistageScalatestTestSuiteRunner

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
  *   type PetStoreEnv = Has[PetStore[IO]]
  *   type PetsEnv = Has[Pets[IO]]
  *
  *   val store = new PetStore[ZIO[PetStoreEnv, ?, ?]] {
  *     def purchasePet(name: String, cost: Int): RIO[PetStoreEnv, Boolean] = ZIO.accessM(_.get.purchasePet(name, cost))
  *   }
  *   val pets = new Pets[ZIO[PetsEnv, ?, ?]] {
  *     def myPets: RIO[PetsEnv, List[String]] = ZIO.accessM(_.get.myPets)
  *   }
  *
  *   "test purchase pets" in {
  *     for {
  *       _    <- store.purchasePet("Zab", 213)
  *       pets <- pets.myPets
  *       _    <- assertIO(pets.contains("Zab"))
  *     } yield ()
  *     // : ZIO[PetStoreEnv with PetsEnv, Throwable, Unit]
  *   }
  * }}}
  *
  * Lambda parameters and environment may both be used at the same time to define dependencies:
  *
  * {{{
  *   "test purchase pets" in {
  *     (store: PetStore[IO]) =>
  *     for {
  *       _    <- store.purchasePet("Zab", 213)
  *       pets <- pets.myPets
  *       _    <- assertIO(pets.contains("Zab"))
  *     } yield ()
  *     // : ZIO[PetsEnv, Throwable, Unit]
  *   }
  * }}}
  */
abstract class DistageBIOEnvSpecScalatest[FR[-_, +_, +_]: DefaultModule3](implicit val tagBIO3: TagK3[FR], implicit val tagBIO: TagKK[FR[Any, ?, ?]])
  extends DistageScalatestTestSuiteRunner[FR[Any, Throwable, ?]]
  with DistageAbstractScalatestSpec[FR[Any, Throwable, ?]] {

  protected implicit def convertToWordSpecStringWrapperDS2(s: String): DSWordSpecStringWrapper3[FR] = {
    new DSWordSpecStringWrapper3(context, distageSuiteName, distageSuiteId, s, this, testEnv)
  }

  // disable single-parameter syntax by removing `implicit`
  override protected def convertToWordSpecStringWrapperDS(s: String): DSWordSpecStringWrapper[FR[Any, Throwable, ?]] = super.convertToWordSpecStringWrapperDS(s)
}
