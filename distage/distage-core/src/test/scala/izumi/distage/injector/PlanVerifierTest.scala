package izumi.distage.injector

import distage.{DIKey, Roots}
import izumi.distage.fixtures.PlanVerifierCases._
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.planning.solver.PlanVerifier
import izumi.distage.planning.solver.PlanVerifier.PlanIssue.{MissingImport, UnsaturatedAxis}
import org.scalatest.wordspec.AnyWordSpec

class PlanVerifierTest extends AnyWordSpec with MkInjector {

  "Verifier handles simple axis" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Fork1].tagged(Axis1.A).from[ImplA]
      make[Fork1].tagged(Axis1.B).from[ImplB]

      make[Fork2].tagged(Axis2.C).from[ImplC]
      make[Fork2].tagged(Axis2.D).from[ImplD]
    }

    val result = PlanVerifier().verify(definition, Roots.target[Fork1])
    assert(result == Right(()))
  }

  "Verifier handles axis fork that mentions the only applicable axis" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Fork1].tagged(Axis1.A).from[ImplA]
      make[Fork1].tagged(Axis1.B).from[ImplB]

      make[Fork2].tagged(Axis1.B, Axis2.C).from[ImplC]
      make[Fork2].tagged(Axis1.B, Axis2.D).from[ImplD]
    }

    val result = PlanVerifier().verify(definition, Roots.target[Fork1])
    assert(result == Right(()))
  }

  "Verifier handles axis fork with only choice along the only applicable axis" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Fork1].tagged(Axis1.A).from[ImplA]
      make[Fork1].tagged(Axis1.B).from[ImplB]

      make[Fork2].tagged(Axis1.B).from[ImplC]
    }

    val result = PlanVerifier().verify(definition, Roots.target[Fork1])
    assert(result == Right(()))
  }

  "Verifier flags axis fork with inapplicable axes" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Fork1].tagged(Axis1.A).from[ImplA]
      make[Fork1].tagged(Axis1.B).from[ImplB]

      make[Fork2].tagged(Axis1.A, Axis2.C).from[ImplC]
      make[Fork2].tagged(Axis1.A, Axis2.D).from[ImplD]
    }

    val result = PlanVerifier().verify(definition, Roots.target[Fork1])
    println(result)
    assert(result.issues.nonEmpty)
    assert(result.issues == Set(UnsaturatedAxis(DIKey[Fork2], Axis1.name, Set(Axis1.B.toAxisPoint))))
  }

  "Verifier flags axis fork with only choice along inapplicable axis" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Fork1].tagged(Axis1.A).from[ImplA]
      make[Fork1].tagged(Axis1.B).from[ImplB]

      make[Fork2].tagged(Axis1.A).from[ImplC]
    }

    val result = PlanVerifier().verify(definition, Roots.target[Fork1])
    println(result)
    assert(result.issues.nonEmpty)
    assert(result.issues == Set(UnsaturatedAxis(DIKey[Fork2], Axis1.name, Set(Axis1.B.toAxisPoint))))
  }

  "Verifier flags missing import only for ImplB" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Fork1].tagged(Axis1.A).from[ImplA]
      make[Fork1].tagged(Axis1.B).from[ImplB]
    }

    val implBOrigin = OperationOrigin.UserBinding(definition.iterator.find(_.tags.contains(Axis1.B)).get)

    val result = PlanVerifier().verify(definition, Roots.target[Fork1])
    println(result)
    assert(result.issues.nonEmpty)
    assert(result.issues.size == 1)
    assert(result.issues == Set(MissingImport(DIKey[Fork2], DIKey[Fork1], Set(DIKey[Fork1] -> implBOrigin))))
  }

}
