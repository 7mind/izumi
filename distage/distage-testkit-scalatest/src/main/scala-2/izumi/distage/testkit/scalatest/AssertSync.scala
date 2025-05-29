package izumi.distage.testkit.scalatest

import cats.effect.kernel.Sync
import izumi.distage.testkit.scalatest.AssertSync.AssertSyncMacro
import izumi.fundamentals.reflection.ReflectionUtil
import org.scalactic.source.Position
import org.scalactic.Prettifier
import org.scalatest.Assertion
import org.scalatest.distage.DistageAssertionsMacro

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/** scalatest assertion macro for any [[cats.effect.kernel.Sync]] */
trait AssertSync[F[_]] {
  final def assertIO(arg: Boolean)(implicit Sync: Sync[F], prettifier: Prettifier, pos: Position): F[Assertion] = macro AssertSyncMacro.impl[F]

  final def assertIO[T](
    effect: F[T]
  )(predicate: T => Boolean
  )(implicit Sync: Sync[F],
    prettifier: Prettifier,
    pos: Position,
  ): F[Assertion] = macro AssertSyncMacro.shortImpl1[F, T]

  final def assertIO[A, B](
    effectA: F[A],
    effectB: F[B],
  )(predicate: (A, B) => Boolean
  )(implicit Sync: Sync[F],
    prettifier: Prettifier,
    pos: Position,
  ): F[Assertion] = macro AssertSyncMacro.shortImpl2[F, A, B]
}

object AssertSync {
  final def assertIO[F[_]](arg: Boolean)(implicit Sync: Sync[F], prettifier: Prettifier, pos: Position): F[Assertion] = macro AssertSyncMacro.impl[F]

  object AssertSyncMacro {
    def impl[F[_]](
      c: blackbox.Context
    )(arg: c.Expr[Boolean]
    )(Sync: c.Expr[Sync[F]],
      prettifier: c.Expr[Prettifier],
      pos: c.Expr[Position],
    ): c.Expr[F[Assertion]] = {
      import c.universe._
      c.Expr[F[Assertion]](q"$Sync.delay(${DistageAssertionsMacro.assert(c)(arg)(prettifier, pos)})")
    }

    def shortImpl1[F[_], T: c.WeakTypeTag](
      c: blackbox.Context
    )(effect: c.Expr[F[T]]
    )(predicate: c.Expr[T => Boolean]
    )(Sync: c.Expr[Sync[F]],
      prettifier: c.Expr[Prettifier],
      pos: c.Expr[Position],
    ): c.Expr[F[Assertion]] = {
      import c.universe._

      val syncFreshName = TermName(c.freshName("Sync"))
      val resultName = TermName(c.freshName("result"))
      val predicateBody = ReflectionUtil.betaReduceLambda(c)(predicate, List(resultName.toString))

      c.Expr[F[Assertion]](
        q"""{
            val $syncFreshName = $Sync
            $syncFreshName.flatMap($effect){
              ($resultName: ${weakTypeOf[T]}) => _root_.izumi.distage.testkit.scalatest.AssertSync.assertIO($predicateBody)($syncFreshName, $prettifier, $pos)
            }}"""
      )
    }

    def shortImpl2[F[_], A: c.WeakTypeTag, B: c.WeakTypeTag](
      c: blackbox.Context
    )(effectA: c.Expr[F[A]],
      effectB: c.Expr[F[B]],
    )(predicate: c.Expr[(A, B) => Boolean]
    )(Sync: c.Expr[Sync[F]],
      prettifier: c.Expr[Prettifier],
      pos: c.Expr[Position],
    ): c.Expr[F[Assertion]] = {
      import c.universe._

      val syncFreshName = TermName(c.freshName("Sync"))
      val resultAName = TermName(c.freshName("resultA"))
      val resultBName = TermName(c.freshName("resultB"))
      val predicateBody = ReflectionUtil.betaReduceLambda(c)(predicate, List(resultAName.toString, resultBName.toString))

      c.Expr[F[Assertion]](q"""{
          val $syncFreshName = $Sync
          $syncFreshName.flatMap($effectA){
           ($resultAName: ${weakTypeOf[A]}) =>
              $syncFreshName.flatMap($effectB){ ($resultBName: ${weakTypeOf[B]}) =>
                _root_.izumi.distage.testkit.scalatest.AssertSync.assertIO($predicateBody)($syncFreshName, $prettifier, $pos) }
        }
     }""")
    }
  }
}
