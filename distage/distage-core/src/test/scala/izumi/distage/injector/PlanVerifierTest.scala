package izumi.distage.injector

import distage.{Activation, DIKey, Injector, Lifecycle, Roots}
import izumi.distage.fixtures.PlanVerifierCases.*
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.exceptions.planning.InjectorFailed
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.plan.operations.OperationOrigin.UserBinding
import izumi.distage.model.planning.AxisPoint
import izumi.distage.planning.solver.PlanVerifier
import izumi.distage.planning.solver.PlanVerifier.PlanIssue.*
import izumi.fundamentals.collections.nonempty.{NonEmptyMap, NonEmptySet}
import izumi.fundamentals.platform.functional.Identity
import org.scalatest.exceptions.TestFailedException
import org.scalatest.wordspec.AnyWordSpec

class PlanVerifierTest extends AnyWordSpec with MkInjector {

  "Verifier handles resources (uniform case, Roots.Everything, https://github.com/7mind/izumi/issues/1476)" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Int].tagged(Axis1.A).fromResource(Lifecycle.makeSimple(1)(_ => ()))
      make[Int].tagged(Axis1.B).fromResource(Lifecycle.makeSimple(2)(_ => ()))
    }

    val result = PlanVerifier().verify[Identity](definition, Roots.Everything, Injector.providedKeys(), Set.empty)
    assert(result.issues.isEmpty)
  }

  "Progression test: Verifier does not handle resources (non-uniform case, Roots.Everything, https://github.com/7mind/izumi/issues/1476)" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Int].tagged(Axis1.A).fromResource(Lifecycle.makeSimple(1)(_ => ()))
      make[Int].tagged(Axis1.B).fromResource {
        new Lifecycle.Basic[Identity, Int] {
          override def acquire: Identity[Int] = 1

          override def release(resource: Int): Identity[Unit] = ()

        }
      }
    }

    val result = PlanVerifier().verify[Identity](definition, Roots.Everything, Injector.providedKeys(), Set.empty)
    assertThrows[TestFailedException] {
      assert(result.issues.isEmpty)
    }
  }

  "Verifier handles resources (uniform case)" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Int].tagged(Axis1.A).fromResource(Lifecycle.makeSimple(1)(_ => ()))
      make[Int].tagged(Axis1.B).fromResource(Lifecycle.makeSimple(2)(_ => ()))
    }

    val result = PlanVerifier().verify[Identity](definition, Roots.target[Int], Injector.providedKeys(), Set.empty)
    assert(result.issues.isEmpty)
  }

  "Verifier handles resources (non-uniform case)" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Int].tagged(Axis1.A).fromResource(Lifecycle.makeSimple(1)(_ => ()))
      make[Int]
        .tagged(Axis1.B).fromResource(new Lifecycle.Basic[Identity, Int] {
          override def acquire: Identity[Int] = 1

          override def release(resource: Int): Identity[Unit] = ()

        })
    }

    val result = PlanVerifier().verify[Identity](definition, Roots.target[Int], Injector.providedKeys(), Set.empty)
    assert(result.issues.isEmpty)
  }

  "Verifier handles simple axis" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Fork1].tagged(Axis1.A).from[ImplA]
      make[Fork1].tagged(Axis1.B).from[ImplB]

      make[Fork2].tagged(Axis2.C).from[ImplC]
      make[Fork2].tagged(Axis2.D).from[ImplD]
    }

    val result = PlanVerifier().verify[Identity](definition, Roots.target[Fork1], Injector.providedKeys(), Set.empty)
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

    val result = PlanVerifier().verify[Identity](definition, Roots.target[Fork1], Injector.providedKeys(), Set.empty)
    assert(result.issues.isEmpty)
  }

  "Verifier handles axis fork with only choice along the only applicable axis" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Fork1].tagged(Axis1.A).from[ImplA]
      make[Fork1].tagged(Axis1.B).from[ImplB]

      make[Fork2].tagged(Axis1.B).from[ImplC]
    }

    val result = PlanVerifier().verify[Identity](definition, Roots.target[Fork1], Injector.providedKeys(), Set.empty)
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

    val result = PlanVerifier().verify[Identity](definition, Roots.target[Fork1], Injector.providedKeys(), Set.empty)
    assert(result.verificationFailed)
    assert(result.issues.fromNonEmptySet == Set(UnsaturatedAxis(DIKey[Fork2], Axis1.name, NonEmptySet(Axis1.B.toAxisPoint))))
  }

  "Verifier flags axis fork with only choice along inapplicable axis" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Fork1].tagged(Axis1.A).from[ImplA]
      make[Fork1].tagged(Axis1.B).from[ImplB]

      make[Fork2].tagged(Axis1.A).from[ImplC]
    }

    val result = PlanVerifier().verify[Identity](definition, Roots.target[Fork1], Injector.providedKeys(), Set.empty)
    assert(result.verificationFailed)
    assert(result.issues.fromNonEmptySet == Set(UnsaturatedAxis(DIKey[Fork2], Axis1.name, NonEmptySet(Axis1.B.toAxisPoint))))
  }

  "Verifier flags missing import only for ImplB" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Fork1].tagged(Axis1.A).from[ImplA]
      make[Fork1].tagged(Axis1.B).from[ImplB]
    }

    val implBOrigin = OperationOrigin.UserBinding(definition.iterator.find(_.tags.contains(Axis1.B)).get)

    val result = PlanVerifier().verify[Identity](definition, Roots.target[Fork1], Injector.providedKeys(), Set.empty)
    assert(result.verificationFailed)
    assert(result.issues.size == 1)
    assert(result.issues.fromNonEmptySet == Set(MissingImport(DIKey[Fork2], DIKey[Fork1], Set(implBOrigin))))
  }

  "Verifier flags conflicting activations" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Fork1].tagged(Axis1.A).from[ImplA]
      make[Fork1].tagged(Axis1.B).from[ImplA]
      make[Fork1].tagged(Axis2.C).from[ImplA]
      make[Fork1].tagged(Axis2.D).from[ImplA]
    }

    assertThrows[InjectorFailed] {
      mkInjector().produceGet[Fork1](definition, Activation(Axis1 -> Axis1.B, Axis2 -> Axis2.D)).unsafeGet()
    }

    assertThrows[InjectorFailed] {
      mkInjector().produceGet[Fork1](definition, Activation(Axis1 -> Axis1.B)).unsafeGet()
    }

    val result = PlanVerifier().verify[Identity](definition, Roots.target[Fork1], Injector.providedKeys(), Set.empty)
    assert(result.verificationFailed)
    assert(result.issues.get.size == 1)
    assert(result.issues.get.head.asInstanceOf[UnsolvableConflict].key == DIKey[Fork1])
    assert(
      result.issues.get.head.asInstanceOf[UnsolvableConflict].ops.map(_._2) ==
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

    assertThrows[InjectorFailed] {
      mkInjector().produceGet[Fork1](definition, Activation(Axis1.B, Axis2.D)).unsafeGet()
    }

    val result = PlanVerifier().verify[Identity](definition, Roots.target[Fork1], Injector.providedKeys(), Set.empty)

    assert(result.issues.get.size == 2)
    assert(result.issues.get.map(_.asInstanceOf[UnsolvableConflict].key) == NonEmptySet(DIKey[Fork2]))
    assert(
      result.issues.get.map(_.asInstanceOf[UnsolvableConflict].ops.map(_._2)) ==
      NonEmptySet(
        NonEmptySet(Set(Axis1.A), Set(Axis2.C), Set(Axis2.D)).map(_.map(_.toAxisPoint)),
        NonEmptySet(Set(Axis1.B), Set(Axis2.C), Set(Axis2.D)).map(_.map(_.toAxisPoint)),
      )
    )
  }

  "Verifier flags conflicting activations with partially shared axis" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Fork1].tagged(Axis1.A, Axis3.E).from[ImplA]
      make[Fork1].tagged(Axis1.B, Axis3.F).from[ImplA]
      make[Fork1].tagged(Axis1.A, Axis2.C).from[ImplA]
      make[Fork1].tagged(Axis1.B, Axis2.D).from[ImplA]
    }

    assertThrows[InjectorFailed] {
      mkInjector().produceGet[Fork1](definition, Activation(Axis1 -> Axis1.B, Axis2 -> Axis2.D)).unsafeGet()
    }

    assertThrows[InjectorFailed] {
      mkInjector().produceGet[Fork1](definition, Activation(Axis1 -> Axis1.B)).unsafeGet()
    }

    val result = PlanVerifier().verify[Identity](definition, Roots.target[Fork1], Injector.providedKeys(), Set.empty)
    assert(result.verificationFailed)
    assert(result.issues.get.size == 1)
    assert(result.issues.get.head.asInstanceOf[UnsolvableConflict].key == DIKey[Fork1])
    assert(
      result.issues.get.head.asInstanceOf[UnsolvableConflict].ops.map(_._2) ==
      NonEmptySet(Set(Axis2.C), Set(Axis2.D), Set(Axis3.E), Set(Axis3.F)).map(_.map(_.toAxisPoint))
    )
  }

  "Verifier flags shadowed activations (overridden by all other activations)" in {
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

    assertThrows[InjectorFailed] {
      mkInjector().produceGet[Fork1](definition, Activation(Axis1.A)).unsafeGet()
    }

    val result = PlanVerifier().verify[Identity](definition, Roots.target[Fork1], Injector.providedKeys(), Set.empty)
    assert(result.verificationFailed)
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

    assertThrows[InjectorFailed] {
      mkInjector().produceGet[Fork1](definition, Activation.empty).unsafeGet()
    }

    val result = PlanVerifier().verify[Identity](definition, Roots.target[Fork1], Injector.providedKeys(), Set.empty)
    assert(result.issues.get.size == 1)
    assert(result.issues.get.head.key == DIKey[Fork1])
    assert(result.issues.get.head.asInstanceOf[ShadowedActivation].activation == Set.empty)
    assert(
      result.issues.get.head.asInstanceOf[ShadowedActivation].shadowingBindings.keySet == NonEmptySet(
        Set(AxisPoint("axis1", "a")),
        Set(AxisPoint("axis1", "b")),
      )
    )
  }

  "Verifier flags axis-less unsolvable conflicts" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Fork1].from[ImplA]
      make[Fork1].from[ImplA2]
    }

    val result = PlanVerifier().verify[Identity](definition, Roots.target[Fork1], Injector.providedKeys(), Set.empty)
    assert(
      result.issues.contains(
        NonEmptySet(DuplicateActivations(DIKey[Fork1], NonEmptyMap(Set.empty -> NonEmptySet.unsafeFrom(definition.bindings.map(UserBinding.apply)))))
      )
    )
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

    assertThrows[InjectorFailed] {
      mkInjector().produceGet[Fork1](definition, Activation(Axis1.A)).unsafeGet()
    }

    val instance1 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.A, Axis2.D)).unsafeGet()
    assert(instance1.isInstanceOf[ImplA])

    val instance2 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.A, Axis2.C)).unsafeGet()
    assert(instance2.isInstanceOf[ImplA2])

    assertThrows[InjectorFailed] {
      mkInjector().produceGet[Fork1](definition, Activation(Axis1.B)).unsafeGet()
    }

    val instance3 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.B, Axis2.C)).unsafeGet()
    assert(instance3.asInstanceOf[ImplB].trait2.isInstanceOf[ImplC])

    val instance4 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.B, Axis2.D)).unsafeGet()
    assert(instance4.asInstanceOf[ImplB].trait2.isInstanceOf[ImplD])

    val result = PlanVerifier().verify[Identity](definition, Roots.target[Fork1], Injector.providedKeys(), Set.empty)
    assert(result.verificationPassed && !result.verificationFailed)
  }

  "Verifier flags shadowed activations in specificity activation chains" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Fork1].tagged(Axis1.A).from[ImplA] // shadowed activation
      make[Fork1].tagged(Axis1.A, Axis2.D).from[ImplA5]
      make[Fork1].tagged(Axis1.A, Axis2.C).from[ImplA2] // shadowed activation
      make[Fork1].tagged(Axis1.A, Axis2.C, Axis3.E).from[ImplA3]
      make[Fork1].tagged(Axis1.A, Axis2.C, Axis3.F).from[ImplA4]
      make[Fork1].tagged(Axis1.B).from[ImplA6]
    }

    assertThrows[InjectorFailed] {
      mkInjector().produceGet[Fork1](definition, Activation(Axis1.A)).unsafeGet()
    }

    val instance0 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.A, Axis2.D)).unsafeGet()
    assert(instance0.isInstanceOf[ImplA5])

    val instance1 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.A, Axis2.D, Axis3.F)).unsafeGet()
    assert(instance1.isInstanceOf[ImplA5])

    assertThrows[InjectorFailed] {
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

    val result = PlanVerifier().verify[Identity](definition, Roots.target[Fork1], Injector.providedKeys(), Set.empty)
    assert(result.issues.get.size == 2)
    assert(result.issues.get.map(_.asInstanceOf[ShadowedActivation].key) == NonEmptySet(DIKey[Fork1]))
    assert(
      result.issues.get.map(_.asInstanceOf[ShadowedActivation].activation) == NonEmptySet(
        Set(AxisPoint("axis1", "a")),
        Set(AxisPoint("axis1", "a"), AxisPoint("axis2", "c")),
      )
    )
    assert(
      result.issues.get.map(_.asInstanceOf[ShadowedActivation].shadowingBindings.keySet) == NonEmptySet(
        NonEmptySet(
          Set(AxisPoint("axis1", "a"), AxisPoint("axis2", "d")),
          Set(AxisPoint("axis1", "a"), AxisPoint("axis2", "c")),
          Set(AxisPoint("axis1", "a"), AxisPoint("axis2", "c"), AxisPoint("axis3", "e")),
          Set(AxisPoint("axis1", "a"), AxisPoint("axis2", "c"), AxisPoint("axis3", "f")),
        ),
        NonEmptySet(
          Set(AxisPoint("axis1", "a"), AxisPoint("axis2", "c"), AxisPoint("axis3", "e")),
          Set(AxisPoint("axis1", "a"), AxisPoint("axis2", "c"), AxisPoint("axis3", "f")),
        ),
      )
    )
  }

  "Verifier handles specificity activation chains" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      make[Fork1].tagged(Axis1.A).from[ImplA2] // less-specific activation
      make[Fork1].tagged(Axis1.A, Axis2.D).from[ImplA5]
      make[Fork1].tagged(Axis1.A, Axis2.C, Axis3.F).from[ImplA4]
      make[Fork1].tagged(Axis1.B, Axis2.C).from[ImplB]
      make[Fork1].tagged(Axis1.B, Axis2.D).from[ImplA6]

      make[Fork2].tagged(Axis2.C, Axis3.E).from[ImplC]
      make[Fork2].tagged(Axis2.C).from[ImplC]
    }

    assertThrows[InjectorFailed] {
      mkInjector().produceGet[Fork1](definition, Activation(Axis1.A)).unsafeGet()
    }

    val instance = mkInjector().produceGet[Fork1](definition, Activation(Axis1.A, Axis2.D)).unsafeGet()
    assert(instance.isInstanceOf[ImplA5])

    val instance1 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.A, Axis2.D, Axis3.F)).unsafeGet()
    assert(instance1.isInstanceOf[ImplA5])

    assertThrows[InjectorFailed] {
      mkInjector().produceGet[Fork1](definition, Activation(Axis1.A, Axis2.C)).unsafeGet()
    }

    val instance2 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.A, Axis2.C, Axis3.E)).unsafeGet()
    assert(instance2.isInstanceOf[ImplA2])

    val instance3 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.A, Axis2.C, Axis3.F)).unsafeGet()
    assert(instance3.isInstanceOf[ImplA4])

    assertThrows[InjectorFailed] {
      mkInjector().produceGet[Fork1](definition, Activation(Axis1.B)).unsafeGet()
    }

    val instance4 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.B, Axis2.D)).unsafeGet()
    assert(instance4.isInstanceOf[ImplA6])

    assertThrows[InjectorFailed] {
      mkInjector().produceGet[Fork1](definition, Activation(Axis1.B, Axis2.C)).unsafeGet()
    }

    val instance6 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.B, Axis2.C, Axis3.E)).unsafeGet()
    assert(instance6.asInstanceOf[ImplB].trait2.isInstanceOf[ImplC])

    val instance7 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.B, Axis2.C, Axis3.F)).unsafeGet()
    assert(instance7.asInstanceOf[ImplB].trait2.isInstanceOf[ImplC])

    val instance8 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.B, Axis2.D, Axis3.E)).unsafeGet()
    assert(instance8.isInstanceOf[ImplA6])

    val instance9 = mkInjector().produceGet[Fork1](definition, Activation(Axis1.B, Axis2.D, Axis3.F)).unsafeGet()
    assert(instance9.isInstanceOf[ImplA6])

    val result = PlanVerifier().verify[Identity](definition, Roots.target[Fork1], Injector.providedKeys(), Set.empty)
    assert(result.issues.isEmpty)
  }

  "Verifier finds issues around a provided import" in {
    import PlanVerifierCase2._

    val definition = new ModuleDef {
      many[Dep]
        .ref[ExternalDep]

      make[X]
      make[Fork1].tagged(Axis.A).from[ImplA].addDependency[Set[Dep]]
      make[Fork1].tagged(Axis.B).from[ImplB]

      make[BadDep].tagged(Axis.B).from[BadDepImplB]
    }

    val result1 = PlanVerifier().verify[Identity](definition, Roots.target[X], Injector.providedKeys(), Set.empty)
    assert(result1.issues.fromNonEmptySet.map(_.getClass) == Set(classOf[MissingImport], classOf[UnsaturatedAxis]))
    assert(
      result1.issues.fromNonEmptySet == Set(
        MissingImport(DIKey[ExternalDep], DIKey[X], result1.issues.fromNonEmptySet.collect { case MissingImport(_, d, origins) if d == DIKey[X] => origins }.flatten),
        MissingImport(
          DIKey[ExternalDep],
          result1.issues.get.collectFirst { case MissingImport(_, d, _) if d.isInstanceOf[DIKey.SetElementKey] => d }.get,
          result1.issues.fromNonEmptySet.collect { case MissingImport(_, d, origins) if d.isInstanceOf[DIKey.SetElementKey] => origins }.flatten,
        ),
        UnsaturatedAxis(DIKey[BadDep], "axis", NonEmptySet(AxisPoint("axis", "a"))),
      )
    )

    val result2 = PlanVerifier().verify[Identity](definition, Roots.target[X], providedKeys = Set(DIKey[ExternalDep]), Set.empty)
    assert(
      result2.issues.fromNonEmptySet == Set(
        UnsaturatedAxis(DIKey[BadDep], "axis", NonEmptySet(AxisPoint("axis", "a")))
      )
    )
  }

  "Verifier lets unsaturated axis slide if it's substituted by a provided import" in {
    import PlanVerifierCase2._

    val definition = new ModuleDef {
      make[Fork1].tagged(Axis.A).from[ImplA]
      make[Fork1].tagged(Axis.B).from[ImplB]

      make[BadDep].tagged(Axis.B).from[BadDepImplB]
    }

    val result1 = PlanVerifier().verify[Identity](definition, Roots.target[Fork1], Injector.providedKeys(), Set.empty)
    assert(result1.issues.fromNonEmptySet == Set(UnsaturatedAxis(DIKey[BadDep], "axis", NonEmptySet(AxisPoint("axis", "a")))))

    val result2 = PlanVerifier().verify[Identity](definition, Roots.target[Fork1], providedKeys = Set(DIKey[BadDep]), Set.empty)
    assert(result2.issues.isEmpty)
  }

  "Verifier handles sets" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      many[Fork2]
        .add[ImplC].tagged(Axis1.A)
        .add[ImplD].tagged(Axis1.B)
    }

    val result = PlanVerifier().verify[Identity](definition, Roots.target[Set[Fork2]], Injector.providedKeys(), Set.empty)
    assert(result.issues.isEmpty)
  }

  "Verifier handles weak sets: basic case" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      many[Fork2]
        .weak[ImplC].tagged(Axis1.A)
        .weak[ImplD].tagged(Axis1.B)
      make[ImplC]
      make[ImplD]
    }

    val result = PlanVerifier().verify[Identity](definition, Roots.target[Set[Fork2]], Injector.providedKeys(), Set.empty)
    assert(result.issues.isEmpty)
  }

  "Verifier handles weak sets: tagged referenced members" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      many[Fork2]
        .weak[ImplC].tagged(Axis1.A)
        .weak[ImplD].tagged(Axis1.B)
      make[ImplC].tagged(Axis1.A)
      make[ImplD].tagged(Axis1.B)
    }

    val result = PlanVerifier().verify[Identity](definition, Roots.target[Set[Fork2]], Injector.providedKeys(), Set.empty)
    assert(result.issues.isEmpty)
  }

  "Verifier handles weak sets: missing original member" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      many[Fork2]
        .weak[ImplC].tagged(Axis1.A)
        .weak[ImplD].tagged(Axis1.B)
      make[ImplD]
    }

    val result = PlanVerifier().verify[Identity](definition, Roots.target[Set[Fork2]], Injector.providedKeys(), Set.empty)
    assert(result.issues.isEmpty)
  }

  "Verifier handles weak sets: named weak references are still weak" in {
    import PlanVerifierCase1._

    val definition = new ModuleDef {
      many[Fork1]
        .weak[ImplA]
        .weak[ImplB]("missing")
    }

    val result = PlanVerifier().verify[Identity](definition, Roots.target[Set[Fork1]], Injector.providedKeys(), Set.empty)
    assert(result.issues.isEmpty)
  }

}
