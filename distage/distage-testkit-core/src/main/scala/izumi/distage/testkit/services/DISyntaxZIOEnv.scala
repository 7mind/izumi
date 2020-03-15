package izumi.distage.testkit.services

import distage.Tag
import izumi.distage.constructors.TraitConstructor
import izumi.distage.model.providers.ProviderMagnet
import zio.{IO, ZIO}

import scala.language.implicitConversions

/**
  * Allows summoning DI objects via ZIO environment intersection types
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
  *   trait PetStoreEnv {
  *     def store: PetStore[IO]
  *   }
  *
  *   trait PetsEnv {
  *     def pets: Pets[IO]
  *   }
  *
  *   val store = new PetStore[ZIO[PetStoreEnv, ?, ?]] {
  *     def purchasePet(name: String, cost: Int): RIO[PetStoreEnv, Boolean] = ZIO.accessM(_.store.purchasePet(name, cost))
  *   }
  *   val pets = new Pets[ZIO[PetsEnv, ?, ?]] {
  *     def myPets: RIO[PetsEnv, List[String]] = ZIO.accessM(_.pets.myPets)
  *   }
  *
  *   "test purchase pets" in {
  *     for {
  *       _    <- store.purchasePet("Zab", 213)
  *       pets <- pets.myPets
  *     } yield assert(pets.contains("Zab"))
  *     // : ZIO[PetStoreEnv with PetsEnv, Throwable, Unit]
  *   }
  * }}}
  */
trait DISyntaxZIOEnv {
  implicit def zioToFn[R: TraitConstructor, E: Tag, A: Tag](zio: ZIO[R, E, A]): ProviderMagnet[IO[E, A]] = {
    TraitConstructor[R].map(zio.provide)
  }

  def args[R: Tag: TraitConstructor, E: Tag, A: Tag](zio: ProviderMagnet[ZIO[R, E, A]]): ProviderMagnet[IO[E, A]] = {
    zio.map2(TraitConstructor[R])(_.provide(_))
  }
}

object DISyntaxZIOEnv extends DISyntaxZIOEnv
