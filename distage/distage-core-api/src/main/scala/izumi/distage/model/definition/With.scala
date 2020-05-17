package izumi.distage.model.definition

/**
  * This annotation lets you choose a more specific implementation
  * for a result of factory method other than its return type.
  *
  * Example:
  *
  * {{{
  *   trait Actor {
  *     def id: UUID
  *   }
  *
  *   trait ActorFactory {
  *     @With[ActorImpl]
  *     def newActor(id: UUID): Actor
  *   }
  *
  *   class ActorImpl(val id: UUID, someDependency: SomeDependency) extends Actor
  *   class SomeDependency
  *
  *   val module = new ModuleDef {
  *     make[ActorFactory]
  *     // generated factory implementation:
  *     //
  *     // make[ActorFactory].from {
  *     //  (someDependency: SomeDependency) =>
  *     //    new ActorFactory {
  *     //      override def newActor(id: UUID): Actor = {
  *     //        new ActorImpl(id, someDependency)
  *     //      }
  *     //    }
  *     // }
  *   }
  * }}}
  *
  * @see [[https://izumi.7mind.io/distage/basics#auto-factories Auto-Factories]]
  */
final class With[T] extends DIStageAnnotation
