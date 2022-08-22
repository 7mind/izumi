package izumi.distage.model.definition.dsl

trait AnyKindShim {
  type LifecycleF = [_] =>> Any
}
