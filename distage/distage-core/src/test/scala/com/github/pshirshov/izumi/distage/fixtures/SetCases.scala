package com.github.pshirshov.izumi.distage.fixtures

import com.github.pshirshov.izumi.distage.model.definition.Id
import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope

@ExposedTestScope
object SetCases {

  object SetCase1 {

    trait SetTrait
    class SetImpl1 extends SetTrait
    class SetImpl2 extends SetTrait
    class SetImpl3 extends SetTrait

    case class Service0(set: Set[SetTrait])

    case class Service1(@Id("n1") set: Set[SetTrait])

    case class Service2(service3: Service3, @Id("n2") set: Set[SetTrait])

    case class Service3(@Id("n3") set: Set[SetTrait])

  }

  object SetCase2 {
    trait Service
    class Service1 extends Service
  }

}
