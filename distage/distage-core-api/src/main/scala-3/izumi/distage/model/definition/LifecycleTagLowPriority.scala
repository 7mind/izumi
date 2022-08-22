package izumi.distage.model.definition


trait LifecycleTagLowPriority {
  /**
    * The `resourceTag` implicit above works perfectly fine, this macro here is exclusively
    * a workaround for highlighting in Intellij IDEA
    *
    * (it's also used to display error trace from TagK's @implicitNotFound)
    *
    * TODO: report to IJ bug tracker
    */
  implicit final def fakeResourceTagMacroIntellijWorkaround[R <: Lifecycle[AnyKind, Any]]: LifecycleTagImpl[R] = ???
}

trait TrifunctorHasLifecycleTagLowPriority1 {
  implicit final def fakeResourceTagMacroIntellijWorkaround[R <: Lifecycle[AnyKind, Any], T]: TrifunctorHasLifecycleTagImpl[R, T] = ???
}
