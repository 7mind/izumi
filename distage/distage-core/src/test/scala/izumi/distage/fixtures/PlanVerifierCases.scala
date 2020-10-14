package izumi.distage.fixtures

import izumi.distage.model.definition.Axis

object PlanVerifierCases {

  object PlanVerifierCase1 {

    object Axis1 extends Axis {
      case object A extends AxisValueDef
      case object B extends AxisValueDef
    }

    object Axis2 extends Axis {
      case object C extends AxisValueDef
      case object D extends AxisValueDef
    }

    trait Fork1
    class ImplA extends Fork1
    class ImplB(val trait2: Fork2) extends Fork1

    trait Fork2
    class ImplC extends Fork2
    class ImplD extends Fork2

  }

  // unsaturated on multiple-axises

  // [specificity?]

}
