package izumi.distage.testkit.scalatest

import cats.effect.kernel.Sync
import org.scalactic.{Prettifier, source}
import org.scalatest.Assertion

trait ShorthandAssertionsSync[F[_]] {
  def assertIO[T](effect: F[T])(predicate: T => Boolean)(implicit Sync: Sync[F], prettifier: Prettifier, pos: source.Position): F[Assertion] = {
    Sync.flatMap(effect)(value => AssertSync.assertIO(predicate(value)))
  }

  def assertIO[A, B](effectA: F[A], effectB: F[B])(predicate: (A, B) => Boolean)(implicit Sync: Sync[F], prettifier: Prettifier, pos: source.Position): F[Assertion] = {
    Sync.flatMap(effectA)(valueA => Sync.flatMap(effectB)(valueB => AssertSync.assertIO(predicate(valueA, valueB))))
  }
}
