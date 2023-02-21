package izumi.distage.testkit.scalatest

import distage.{DefaultModule3, TagK3, TagKK}
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec
import izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec.DSWordSpecStringWrapper3
import izumi.logstage.distage.LogIO2Module
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
  *   val store = new PetStore[ZIO[PetStoreEnv, _, _]] {
  *     def purchasePet(name: String, cost: Int): RIO[PetStoreEnv, Boolean] = ZIO.accessM(_.get.purchasePet(name, cost))
  *   }
  *   val pets = new Pets[ZIO[PetsEnv, _, _]] {
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
  *       for {
  *         _    <- store.purchasePet("Zab", 213)
  *         pets <- pets.myPets
  *         _    <- assertIO(pets.contains("Zab"))
  *       } yield ()
  *       // : ZIO[PetsEnv, Throwable, Unit]
  *   }
  * }}}
  */
abstract class Spec3[FR[-_, +_, +_]: DefaultModule3](implicit val tagBIO3: TagK3[FR], val tagBIO: TagKK[FR[Any, _, _]])
  extends DistageScalatestTestSuiteRunner[FR[Any, Throwable, _]]
  with DistageAbstractScalatestSpec[FR[Any, Throwable, _]] {

  protected implicit def convertToWordSpecStringWrapperDS3(s: String): DSWordSpecStringWrapper3[FR] = {
    new DSWordSpecStringWrapper3(context, distageSuiteName, distageSuiteId, s, this, testEnv)
  }

  override protected def config: TestConfig = super.config.copy(
    moduleOverrides = LogIO2Module[FR[Any, _, _]]()(tagBIO)
  )
}
