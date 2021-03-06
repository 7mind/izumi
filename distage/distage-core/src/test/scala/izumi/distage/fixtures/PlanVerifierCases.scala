package izumi.distage.fixtures

import izumi.distage.model.definition.Axis

object PlanVerifierCases {

  object PlanVerifierCase1 {

    object Axis1 extends Axis {
      case object A extends AxisChoiceDef
      case object B extends AxisChoiceDef
    }

    object Axis2 extends Axis {
      case object C extends AxisChoiceDef
      case object D extends AxisChoiceDef
    }

    object Axis3 extends Axis {
      case object E extends AxisChoiceDef
      case object F extends AxisChoiceDef
    }

    trait Fork1
    class ImplA extends Fork1
    class ImplB(val trait2: Fork2) extends Fork1
    class ImplB2(val trait2: Fork2) extends Fork1
    class ImplA2 extends Fork1
    class ImplA3 extends Fork1
    class ImplA4 extends Fork1
    class ImplA5 extends Fork1
    class ImplA6 extends Fork1

    trait Fork2
    class ImplC extends Fork2
    class ImplC2 extends Fork2
    class ImplD extends Fork2
    class ImplD2 extends Fork2

  }

  object PlanVerifierCase2 {
    trait Dep
    class ExternalDep extends Dep

    class X(
      val external: ExternalDep,
      val fork1: Fork1,
    )

    trait Fork1
    class ImplA(val badDep: BadDep) extends Fork1
    class ImplB() extends Fork1

    trait BadDep
    class BadDepImplB extends BadDep

    object Axis extends Axis {
      case object A extends AxisChoiceDef
      case object B extends AxisChoiceDef
    }
  }

}
