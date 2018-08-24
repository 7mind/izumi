package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.reflection.macros.TagMacro
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.u._
import org.scalatest.WordSpec

import scala.language.higherKinds

class TagTest extends WordSpec {

  "Tag" should {

    "Work for any concrete type" in {
      assert(TagMacro.get[Int].tpe == safe[Int])
      assert(TagMacro.get[Set[String]].tpe == safe[Set[String]])
      assert(TagMacro.get[Map[Boolean, Double]].tpe == safe[Map[Boolean, Double]])
      assert(TagMacro.get[_ => Unit].tpe == safe[_ => Unit])
      assert(TagMacro.get[Unit => _].tpe == safe[Unit => _])
      assert(TagMacro.get[_ => _].tpe == safe[_ => _])

      assert(TagMacro.get[Any].tpe == safe[Any])
      assert(TagMacro.get[Nothing].tpe == safe[Nothing])
      assert(TagMacro.get[Any => Nothing].tpe == safe[Any => Nothing])
      assert(TagMacro.get[Nothing => Any].tpe == safe[Nothing => Any])

      assert(TagMacro.get[Set[Any]].tpe == safe[Set[Any]])
      assert(TagMacro.get[Set[Nothing]].tpe == safe[Set[Nothing]])
      assert(TagMacro.get[Set[_]].tpe == safe[Set[_]])
    }

    "Work for any abstract type with available TypeTag" in {
      def testTag[T: TypeTag] = TagMacro.get[T]

      assert(testTag[String].tpe == safe[String])
    }

    "Work for any abstract type with available Tag" in {
      def testTag[T: Tag] = TagMacro.get[T]

      assert(testTag[String].tpe == safe[String])
    }

    "Work for any abstract type with available TagK" in {
      def testTagK[F[_]: TagK, T: Tag] = TagMacro.get[F[T]]
      // TODO add strange shapes

      assert(testTagK[Set, Int].tpe == safe[Set[Int]])
    }

    "Not work for any abstract type without available TypeTag or Tag or TagK" in {
      def testTag[T] = TagMacro.get[T]
      def testTagK[F[_], T] = TagMacro.get[F[T]]

      assert(testTag[String].tpe == safe[String])
      assert(testTagK[Set, Int].tpe == safe[Set[Int]])
    }
  }


  def safe[T: TypeTag] = SafeType(typeOf[T])

}
