package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.definition.With
import com.github.pshirshov.izumi.distage.model.reflection.macros.TagMacro
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.u._
import distage.Tag
import org.scalatest.WordSpec

trait X[Y] {
  type Z = Y

  implicit def tagZ: Tag[Z]
}

class TagTest extends WordSpec with X[String] {

  override final val tagZ = Tag[String]
  final val str = "str"

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


      assert(TagMacro.get[With[Any]].tpe == safe[With[Any]])
      assert(TagMacro.get[With[Nothing]].tpe == safe[With[Nothing]])
      assert(TagMacro.get[With[_]].tpe == safe[With[_]])

      assert(TagMacro.get[{ def a: Int ; def g: Boolean }].tpe == safe[{ def a: Int ; def g: Boolean }])
      assert(TagMacro.get[Int with String].tpe == safe[Int with String])
      assert(TagMacro.get[Int { def a: Int }].tpe == safe[Int { def a: Int} ])

      assert(TagMacro.get[str.type].tpe == safe[str.type])
      assert(TagMacro.get[With[str.type] with ({ type T = str.type with Int})].tpe == safe[With[str.type] with ({ type T = str.type with Int})])
      assert(TagMacro.get[this.Z].tpe == safe[this.Z])
      assert(TagMacro.get[this.Z].tpe.tpe == typeOf[this.Z])
      assert(TagMacro.get[TagTest#Z].tpe == safe[TagTest#Z])
    }

    "Work for a concrete type with available TypeTag" in {
      val t_ = typeTag[Unit]

      {
        implicit val t: TypeTag[Unit] =  t_

        assert(TagMacro.get[Unit].tpe == safe[Unit])
      }
    }

    "Work for any abstract type with available TypeTag when obscured by empty refinement" in {
      def testTag[T: TypeTag] = TagMacro.get[T {}]

      assert(testTag[String].tpe == safe[String])
    }

    "Work for any abstract type with available Tag when obscured by empty refinement" in {
      def testTag[T: Tag] = TagMacro.get[T {}]

      assert(testTag[String].tpe == safe[String])
    }

    "handle function local type aliases" in {
      def testTag[T: Tag]= {
        type X[A] = Either[Int, A]

        TagMacro.get[X[T]]
      }

      assert(testTag[String].tpe == safe[Either[Int, String]])
    }
    // Work for dispositioned arguments [_A_A_]

    // add test: how to do: [F[Int, ?, ?]: TagKK] = make[F[Int, A, B]

    "Work for any abstract type with available TagK" in {
      def testTagK[F[_]: TagK, T: Tag] = TagMacro.get[F[T]]
      // TODO add strange shapes

      assert(testTagK[Set, Int].tpe == safe[Set[Int]])
    }

     "Work for any abstract type with available TagKK" in {
      def testTagKK[F[_, _]: TagKK, T: Tag, G: Tag] = TagMacro.get[F[T, G]]
      // TODO add strange shapes

      assert(testTagKK[Either, Int, String].tpe == safe[Either[Int, String]])
    }

    "Shouldn't work for any abstract type without available TypeTag or Tag or TagK" in {

      def testTag[T] = TagMacro.get[T]
      def testTagK[F[_], T] = TagMacro.get[F[T]]

      assert(testTag[String].tpe == safe[String])
      assert(testTagK[Set, Int].tpe == safe[Set[Int]])
    }
  }


  def safe[T: TypeTag] = SafeType(typeOf[T])

}
