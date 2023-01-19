package izumi.distage.fixtures

import izumi.distage.model.definition.Id
import izumi.fundamentals.platform.build.ExposedTestScope

@ExposedTestScope
object SetCases {

  object SetCase1 {

    trait TypedService[T]
    class ServiceWithTypedSet(val tt: Set[ExampleTypedCaseClass[Int]]) extends TypedService[Int]
    case class ExampleTypedCaseClass[T](t: T)

    trait SetTrait
    class SetImpl1 extends SetTrait
    class SetImpl2 extends SetTrait
    class SetImpl3 extends SetTrait
    class SetImpl4 extends SetTrait
    class SetImpl5 extends SetTrait

    case class Service0(set: Set[SetTrait])

    case class Service1(@Id("n1") set: Set[SetTrait])

    case class Service2(service3: Service3, @Id("n2") set: Set[SetTrait])

    case class Service3(@Id("n3") set: Set[SetTrait])

  }

  object SetCase2 {
    trait Service
    class Service1 extends Service
  }

  object SetCase3 {

    trait Ordered {
      def order: Int
    }

    case class ServiceA() extends Ordered {
      override def toString = "A"
      val order = 1
    }

    case class ServiceB(a: ServiceA) extends Ordered {
      override def toString = "B"
      val order = 2
    }

    case class ServiceC(a: ServiceA, b: ServiceB) extends Ordered {
      override def toString = "C"
      val order = 3
    }

    case class ServiceD(a: ServiceA, b: ServiceB, c: ServiceC) extends Ordered {
      override def toString = "D"
      val order = 4
    }
  }

  object SetCase4 {
    trait Service

    class Service1 extends Service

    class Service2 extends Service
  }

}
