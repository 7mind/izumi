package izumi.distage.injector

import distage.{Activation, DIKey, Roots}
import izumi.distage.fixtures.PlanVerifierCases._
import izumi.distage.model.definition.Axis.AxisPoint
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.exceptions.ConflictResolutionException
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.plan.operations.OperationOrigin.UserBinding
import izumi.distage.planning.solver.PlanVerifier
import izumi.distage.planning.solver.PlanVerifier.PlanIssue.{DuplicateActivations, MissingImport, UnsaturatedAxis, UnsolvableConflict}
import izumi.fundamentals.collections.nonempty.{NonEmptyMap, NonEmptySet}
import org.scalatest.exceptions.TestFailedException
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
    assert(result.issues.isEmpty)
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
    assert(result.issues.isEmpty)
  }

  "Verifier handles axis fork with only choice along the only applicable axis" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Fork1].tagged(Axis1.A).from[ImplA]
      make[Fork1].tagged(Axis1.B).from[ImplB]

      make[Fork2].tagged(Axis1.B).from[ImplC]
    }

    val result = PlanVerifier().verify(definition, Roots.target[Fork1])
    assert(result.issues.isEmpty)
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
    assert(result.issues.nonEmpty)
    assert(result.issues == Set(UnsaturatedAxis(DIKey[Fork2], Axis1.name, NonEmptySet(Axis1.B.toAxisPoint))))
  }

  "Verifier flags axis fork with only choice along inapplicable axis" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Fork1].tagged(Axis1.A).from[ImplA]
      make[Fork1].tagged(Axis1.B).from[ImplB]

      make[Fork2].tagged(Axis1.A).from[ImplC]
    }

    val result = PlanVerifier().verify(definition, Roots.target[Fork1])
    assert(result.issues.nonEmpty)
    assert(result.issues == Set(UnsaturatedAxis(DIKey[Fork2], Axis1.name, NonEmptySet(Axis1.B.toAxisPoint))))
  }

  "Verifier flags missing import only for ImplB" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Fork1].tagged(Axis1.A).from[ImplA]
      make[Fork1].tagged(Axis1.B).from[ImplB]
    }

    val implBOrigin = OperationOrigin.UserBinding(definition.iterator.find(_.tags.contains(Axis1.B)).get)

    val result = PlanVerifier().verify(definition, Roots.target[Fork1])
    assert(result.issues.nonEmpty)
    assert(result.issues.size == 1)
    assert(result.issues == Set(MissingImport(DIKey[Fork2], DIKey[Fork1], Set(DIKey[Fork1] -> implBOrigin))))
  }

  "Verifier flags conflicting activations" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Fork1].tagged(Axis1.A).from[ImplA]
      make[Fork1].tagged(Axis1.B).from[ImplA]
      make[Fork1].tagged(Axis2.C).from[ImplA]
      make[Fork1].tagged(Axis2.D).from[ImplA]
    }

    assertThrows[ConflictResolutionException] {
      mkInjector().produceGet[Fork1](definition, Activation(Axis1 -> Axis1.B, Axis2 -> Axis2.D))
    }

    assertThrows[ConflictResolutionException] {
      mkInjector().produceGet[Fork1](definition, Activation(Axis1 -> Axis1.B))
    }

    val result = PlanVerifier().verify(definition, Roots.target[Fork1])
    assert(result.issues.nonEmpty)
    assert(result.issues.size == 1)
    assert(result.issues.head.asInstanceOf[UnsolvableConflict].key == DIKey[Fork1])
    assert(
      result.issues.head.asInstanceOf[UnsolvableConflict].ops.map(_._2) ==
      NonEmptySet(Set(Axis1.A), Set(Axis1.B), Set(Axis2.C), Set(Axis2.D)).map(_.map(_.toAxisPoint))
    )
  }

  "Verifier flags conflicting activations in dependency" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Fork1].tagged(Axis1.A).from[ImplB]
      make[Fork1].tagged(Axis1.B).from[ImplB2]

      make[Fork2].tagged(Axis1.A).from[ImplC]
      make[Fork2].tagged(Axis1.B).from[ImplD]
      make[Fork2].tagged(Axis2.C).from[ImplC2]
      make[Fork2].tagged(Axis2.D).from[ImplD2]
    }

    assertThrows[ConflictResolutionException] {
      mkInjector().produceGet[Fork1](definition, Activation(Axis1.B, Axis2.D)).unsafeGet()
    }

    val result = PlanVerifier().verify(definition, Roots.target[Fork1])

    assert(result.issues.nonEmpty)
    assert(result.issues.size == 2)
    assert(result.issues.map(_.asInstanceOf[UnsolvableConflict].key) == Set(DIKey[Fork2]))
    assert(
      result.issues.map(_.asInstanceOf[UnsolvableConflict].ops.map(_._2)) ==
      Set(
        NonEmptySet(Set(Axis1.A), Set(Axis2.C), Set(Axis2.D)).map(_.map(_.toAxisPoint)),
        NonEmptySet(Set(Axis1.B), Set(Axis2.C), Set(Axis2.D)).map(_.map(_.toAxisPoint)),
      )
    )
  }

  "progression test: can't Verifier flags shadowed activations (overridden by all other activations)" in assertThrows[TestFailedException] {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Fork1].tagged(Axis1.A).from[ImplA] // shadowed activation
      make[Fork1].tagged(Axis1.A, Axis2.C).from[ImplA2]
      make[Fork1].tagged(Axis1.A, Axis2.D).from[ImplA3]
      make[Fork1].tagged(Axis1.B).from[ImplA4]
    }

    assert(
      mkInjector()
        .produceGet[Fork1](definition, Activation(Axis1.A, Axis2.C)).unsafeGet()
        .isInstanceOf[ImplA2]
    )

    assertThrows[ConflictResolutionException] {
      mkInjector().produceGet[Fork1](definition, Activation(Axis1.A)).unsafeGet()
    }

    val result = PlanVerifier().verify(definition, Roots.target[Fork1])
    assert(result.issues.nonEmpty)
//    assert(result.issues.size == 99)
  }

  "Verifier flags shadowed defaults (overridden by all other activations)" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Fork1].from[ImplA] // shadowed activation
      make[Fork1].tagged(Axis1.A).from[ImplA2]
      make[Fork1].tagged(Axis1.B).from[ImplA3]
    }

    assert(
      mkInjector()
        .produceGet[Fork1](definition, Activation(Axis1.B)).unsafeGet()
        .isInstanceOf[ImplA3]
    )

    assertThrows[ConflictResolutionException] {
      mkInjector().produceGet[Fork1](definition, Activation.empty).unsafeGet()
    }

//    val result = PlanVerifier().verify(definition, Roots.target[Fork1])
//    assert(result.issues.nonEmpty)
//    assert(result.issues.size == 99)
  }

  "Verifier flags axis-less unsolvable conflicts" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Fork1].from[ImplA]
      make[Fork1].from[ImplA2]
    }

    val result = PlanVerifier().verify(definition, Roots.target[Fork1])
    assert(result.issues == Set(DuplicateActivations(DIKey[Fork1], NonEmptyMap(Set.empty -> NonEmptySet.unsafeFrom(definition.bindings.map(UserBinding))))))
  }

  "Verifier handles less-specific activations" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Fork1].tagged(Axis1.A).from[ImplA] // less-specific activation
      make[Fork1].tagged(Axis1.A, Axis2.C).from[ImplA2]
      make[Fork1].tagged(Axis1.B).from[ImplB]

      make[Fork2].tagged(Axis2.C).from[ImplC]
      make[Fork2].tagged(Axis2.D).from[ImplD]
    }

    assertThrows[ConflictResolutionException] {
      mkInjector().produceGet[Fork1](definition, Activation(Axis1.A)).unsafeGet()
    }

    val instance1 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.A, Axis2.D)).unsafeGet()
    assert(instance1.isInstanceOf[ImplA])

    val instance2 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.A, Axis2.C)).unsafeGet()
    assert(instance2.isInstanceOf[ImplA2])

    assertThrows[ConflictResolutionException] {
      mkInjector().produceGet[Fork1](definition, Activation(Axis1.B)).unsafeGet()
    }

    val instance3 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.B, Axis2.C)).unsafeGet()
    assert(instance3.asInstanceOf[ImplB].trait2.isInstanceOf[ImplC])

    val instance4 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.B, Axis2.D)).unsafeGet()
    assert(instance4.asInstanceOf[ImplB].trait2.isInstanceOf[ImplD])

    val result = PlanVerifier().verify(definition, Roots.target[Fork1])
    assert(result.issues.isEmpty)
  }

  "progression test: can't Verifier flags shadowed activations in specificity activation chains" in assertThrows[TestFailedException] {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Fork1].tagged(Axis1.A).from[ImplA] // shadowed activation
      make[Fork1].tagged(Axis1.A, Axis2.D).from[ImplA5]
      make[Fork1].tagged(Axis1.A, Axis2.C).from[ImplA2] // shadowed activation
      make[Fork1].tagged(Axis1.A, Axis2.C, Axis3.E).from[ImplA3]
      make[Fork1].tagged(Axis1.A, Axis2.C, Axis3.F).from[ImplA4]
      make[Fork1].tagged(Axis1.B).from[ImplA6]
    }

    assertThrows[ConflictResolutionException] {
      mkInjector().produceGet[Fork1](definition, Activation(Axis1.A)).unsafeGet()
    }

    val instance0 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.A, Axis2.D)).unsafeGet()
    assert(instance0.isInstanceOf[ImplA5])

    val instance1 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.A, Axis2.D, Axis3.F)).unsafeGet()
    assert(instance1.isInstanceOf[ImplA5])

    assertThrows[ConflictResolutionException] {
      mkInjector().produceGet[Fork1](definition, Activation(Axis1.A, Axis2.C)).unsafeGet()
    }

    val instance2 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.A, Axis2.C, Axis3.E)).unsafeGet()
    assert(instance2.isInstanceOf[ImplA3])

    val instance3 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.A, Axis2.C, Axis3.F)).unsafeGet()
    assert(instance3.isInstanceOf[ImplA4])

    val instance4 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.B)).unsafeGet()
    assert(instance4.isInstanceOf[ImplA6])

    val instance5 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.B, Axis2.D)).unsafeGet()
    assert(instance5.isInstanceOf[ImplA6])

    val instance6 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.B, Axis2.C)).unsafeGet()
    assert(instance6.isInstanceOf[ImplA6])

    val instance7 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.B, Axis2.C, Axis3.E)).unsafeGet()
    assert(instance7.isInstanceOf[ImplA6])

    val result = PlanVerifier().verify(definition, Roots.target[Fork1])
    assert(result.issues.nonEmpty)
//    assert(result.issues.size == 99)
  }

  "progression test: can't Verifier handles specificity activation chains" in assertThrows[TestFailedException] {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Fork1].tagged(Axis1.A).from[ImplA2] // less-specific activation
      make[Fork1].tagged(Axis1.A, Axis2.D).from[ImplA5]
      make[Fork1].tagged(Axis1.A, Axis2.C, Axis3.F).from[ImplA4]
      make[Fork1].tagged(Axis1.B).from[ImplB]

      make[Fork2].tagged(Axis3.E).from[ImplC]
    }

    assertThrows[ConflictResolutionException] {
      mkInjector().produceGet[Fork1](definition, Activation(Axis1.A)).unsafeGet()
    }

    val instance = mkInjector().produceGet[Fork1](definition, Activation(Axis1.A, Axis2.D)).unsafeGet()
    assert(instance.isInstanceOf[ImplA5])

    val instance1 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.A, Axis2.D, Axis3.F)).unsafeGet()
    assert(instance1.isInstanceOf[ImplA5])

    assertThrows[ConflictResolutionException] {
      mkInjector().produceGet[Fork1](definition, Activation(Axis1.A, Axis2.C)).unsafeGet()
    }

    val instance2 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.A, Axis2.C, Axis3.E)).unsafeGet()
    assert(instance2.isInstanceOf[ImplA2])

    val instance3 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.A, Axis2.C, Axis3.F)).unsafeGet()
    assert(instance3.isInstanceOf[ImplA4])

    val instance4 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.B)).unsafeGet()
    assert(instance4.asInstanceOf[ImplB].trait2.isInstanceOf[ImplC])

    val instance5 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.B, Axis2.D)).unsafeGet()
    assert(instance5.asInstanceOf[ImplB].trait2.isInstanceOf[ImplC])

    val instance6 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.B, Axis2.C)).unsafeGet()
    assert(instance6.asInstanceOf[ImplB].trait2.isInstanceOf[ImplC])

    val instance7 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.B, Axis2.C, Axis3.E)).unsafeGet()
    assert(instance7.asInstanceOf[ImplB].trait2.isInstanceOf[ImplC])

    val result = PlanVerifier().verify(definition, Roots.target[Fork1])
    assert(result.issues.isEmpty)
  }

  "Verifier finds issues around a provided import" in {
    import PlanVerifierCase2._

    val definition = new ModuleDef {
      many[Dep]
        .ref[ExternalDep]

      make[X]
      make[Fork1].tagged(Axis.A).from[ImplA]
      make[Fork1].tagged(Axis.B).from[ImplB]

      make[BadDep].tagged(Axis.B).from[BadDepImplB]
    }

    val result1 = PlanVerifier().verify(definition, Roots.target[X])
    assert(result1.issues.map(_.getClass) == Set(classOf[MissingImport], classOf[UnsaturatedAxis]))

    val result2 = PlanVerifier().verify(definition, Roots.target[X], providedKeys = Set(DIKey[ExternalDep]))
    assert(result2.issues == Set(UnsaturatedAxis(DIKey[BadDep], "axis", NonEmptySet(AxisPoint("axis", "a")))))
  }

  "Verifier lets unsaturated axis slide if it's substituted by a provided import" in {
    import PlanVerifierCase2._

    val definition = new ModuleDef {
      make[Fork1].tagged(Axis.A).from[ImplA]
      make[Fork1].tagged(Axis.B).from[ImplB]

      make[BadDep].tagged(Axis.B).from[BadDepImplB]
    }

    val result1 = PlanVerifier().verify(definition, Roots.target[Fork1])
    assert(result1.issues == Set(UnsaturatedAxis(DIKey[BadDep], "axis", NonEmptySet(AxisPoint("axis", "a")))))

    val result2 = PlanVerifier().verify(definition, Roots.target[Fork1], providedKeys = Set(DIKey[BadDep]))
    assert(result2.issues.isEmpty)
  }

}
