package izumi.fundamentals.reflection.test

import izumi.fundamentals.reflection.ReflectionUtil
import org.scalatest.WordSpec

class LightTypeTagTestJvm extends WordSpec {

  type FP1[+T] = List[T]
  type Ap1[+F[+_], +T] = F[T]
  type FP[+T] = FP1[T]

  trait C {
    type A
  }

  "allPartsStrong for Identity typelambda" in {
    val res1 = ReflectionUtil.allPartsStrong(scala.reflect.runtime.universe.typeOf[ID.id[C]].typeConstructor)
    assert(res1)
  }

  "allPartsStrong for eta-expansion typelambda" in {
    val res1 = ReflectionUtil.allPartsStrong(scala.reflect.runtime.universe.typeOf[FP1[C]].typeConstructor)
    assert(res1)
  }

  "allPartsStrong for application typelambda" in {
    val tpe = scala.reflect.runtime.universe.typeOf[Ap1[List, Int]].typeConstructor
    val res1 = ReflectionUtil.allPartsStrong(tpe)
    assert(res1)
  }

  "allPartsStrong for anonymous application typelambda" in {
    val tpe = scala.reflect.runtime.universe.weakTypeOf[{ type l[F[_], A] = F[A] }]
      .asInstanceOf[scala.reflect.runtime.universe.RefinedTypeApi].decl(scala.reflect.runtime.universe.TypeName("l"))
      .asType.typeSignature
      .typeConstructor
    println(tpe)
    val res1 = ReflectionUtil.allPartsStrong(tpe)
    assert(res1)
  }

  "allPartsStrong for x.F[x.Id] typelambda" in {
    val res1 = ReflectionUtil.allPartsStrong({ object x { type F[_[_]]; type Id[A] = A }; scala.reflect.runtime.universe.weakTypeOf[x.F[x.Id]] })
    assert(res1)
  }

}
