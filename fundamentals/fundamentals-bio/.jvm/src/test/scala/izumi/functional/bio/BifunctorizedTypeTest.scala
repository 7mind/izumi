package izumi.functional.bio

import org.scalatest.wordspec.AnyWordSpec

final class BifunctorizedTypeTest extends AnyWordSpec {

  private trait DummyF[A]
  private final class DummyBox[A](val a: A) extends DummyF[A]

  // DummyF is a parameterized trait; the materializer cannot synthesize a ClassTag for it on
  // Scala 2, so we supply one explicitly.
  private implicit def dummyFClassTag[A]: scala.reflect.ClassTag[DummyF[A]] =
    scala.reflect.ClassTag(classOf[DummyF[Any]])

  "Bifunctorized" should {
    "preserve runtime identity through bifunctorize (Goal 4: no-op for actual bifunctors)" in {
      val raw: DummyF[Int] = new DummyBox(42)
      val wrapped: Bifunctorized[DummyF, Throwable, Int] = Bifunctorized.bifunctorize(raw)
      assert(wrapped.asInstanceOf[AnyRef] eq raw.asInstanceOf[AnyRef])
    }

    "preserve runtime identity through debifunctorize" in {
      val raw: DummyF[Int] = new DummyBox(42)
      val wrapped: Bifunctorized[DummyF, Throwable, Int] = Bifunctorized.bifunctorize(raw)
      val unwrapped: DummyF[Int] = Bifunctorized.debifunctorize(wrapped)
      assert(unwrapped.asInstanceOf[AnyRef] eq raw.asInstanceOf[AnyRef])
    }

    "round-trip bifunctorize/debifunctorize returns the same instance" in {
      val raw: DummyF[String] = new DummyBox("hello")
      val out: DummyF[String] = Bifunctorized.debifunctorize(Bifunctorized.bifunctorize(raw))
      assert(out eq raw)
    }

    "preserve covariance on E and A" in {
      trait Animal; class Cat extends Animal
      val raw: DummyF[Cat] = new DummyBox(new Cat)
      val narrow: Bifunctorized[DummyF, RuntimeException, Cat] = Bifunctorized.assert(raw)
      val widened: Bifunctorized[DummyF, Throwable, Animal] = narrow  // must compile
      assert(widened.asInstanceOf[AnyRef] eq raw.asInstanceOf[AnyRef])
    }

    "auto-convert F[A] to Bifunctorized[F, Throwable, A] via implicit conversion" in {
      val raw: DummyF[Int] = new DummyBox(7)
      val wrapped: Bifunctorized[DummyF, Throwable, Int] = raw
      assert(wrapped.asInstanceOf[AnyRef] eq raw.asInstanceOf[AnyRef])
    }

    "auto-project Bifunctorized[F, Throwable, A] to F[A] via implicit conversion" in {
      val raw: DummyF[Int] = new DummyBox(7)
      val wrapped: Bifunctorized[DummyF, Throwable, Int] = Bifunctorized.bifunctorize(raw)
      val unwrapped: DummyF[Int] = wrapped
      assert(unwrapped eq raw)
    }

    "expose .toMonofunctor syntax on Bifunctorized[F, Throwable, A]" in {
      val raw: DummyF[Int] = new DummyBox(9)
      val wrapped: Bifunctorized[DummyF, Throwable, Int] = Bifunctorized.bifunctorize(raw)
      val recovered: DummyF[Int] = wrapped.toMonofunctor
      assert(recovered eq raw)
    }

    "expose .unwrap syntax on Bifunctorized[F, E, A] for any E" in {
      val raw: DummyF[Int] = new DummyBox(11)
      val wrapped: Bifunctorized[DummyF, RuntimeException, Int] = Bifunctorized.assert(raw)
      val recovered: DummyF[Int] = wrapped.unwrap
      assert(recovered eq raw)
    }

    "implicitly summon ClassTag[Bifunctorized[F, E, A]] with the underlying F[A]'s runtime class" in {
      val ct = implicitly[scala.reflect.ClassTag[Bifunctorized[DummyF, Throwable, Int]]]
      // Goal: ClassTag reflects the underlying F[A] runtime class, not Object.
      // DummyF is a generic class so its erasure is the DummyF interface itself.
      assert(ct.runtimeClass eq classOf[DummyF[Any]])
    }

    "derive correct ClassTag for primitive F[A] (Identity-style)" in {
      type Id[A] = A
      // Scala 2.13's ClassTag macro does not expand local type aliases, so we supply the
      // evidence (Id[Int] = Int) explicitly.
      implicit val idIntClassTag: scala.reflect.ClassTag[Id[Int]] =
        scala.reflect.ClassTag.Int.asInstanceOf[scala.reflect.ClassTag[Id[Int]]]
      val ct = implicitly[scala.reflect.ClassTag[Bifunctorized[Id, Throwable, Int]]]
      // For F = Id, F[Int] = Int (primitive). The derived ClassTag must carry the primitive.
      assert(ct.runtimeClass eq java.lang.Integer.TYPE)
    }

    "preserve runtime identity through bifunctorize for a real bifunctor (ZIO)" in {
      val raw: zio.ZIO[Any, Throwable, Int] = zio.ZIO.succeed(42)
      val wrapped: Bifunctorized[zio.ZIO[Any, Throwable, *], Throwable, Int] = Bifunctorized.bifunctorize(raw)
      assert(wrapped.asInstanceOf[AnyRef] eq raw.asInstanceOf[AnyRef])
    }
  }

}
