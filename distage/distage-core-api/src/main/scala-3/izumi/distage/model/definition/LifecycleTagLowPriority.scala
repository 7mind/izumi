package izumi.distage.model.definition

import izumi.distage.model.definition.dsl.AnyKindShim


trait LifecycleTagLowPriority extends AnyKindShim {
  /**
    * The `resourceTag` implicit above works perfectly fine, this macro here is exclusively
    * a workaround for highlighting in Intellij IDEA
    *
    * (it's also used to display error trace from TagK's @implicitNotFound)
    *
    * TODO: report to IJ bug tracker
    */
  implicit final def fakeResourceTagMacroIntellijWorkaround[R <: Lifecycle[LifecycleF, Any]]: LifecycleTagImpl[R] = ???
}

trait TrifunctorHasLifecycleTagLowPriority1 extends AnyKindShim {
  implicit final def fakeResourceTagMacroIntellijWorkaround[R <: Lifecycle[LifecycleF, Any], T]: TrifunctorHasLifecycleTagImpl[R, T] = ???
}
