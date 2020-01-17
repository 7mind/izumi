package izumi.distage.model.definition

/**
  * An optional, documentation-only annotation conveying that
  * an abstract class or a trait is the 'actual' implementation
  * of its supertypes and will be bound later in DI with
  * [[izumi.distage.constructors.TraitConstructor]] or
  * [[izumi.distage.constructors.FactoryConstructor]]
  *
  * Abstract classes or traits without obvious concrete subclasses
  * may hinder the readability of a codebase, if you still want to use them
  * to avoid writing the full constructor, you may use this annotation
  * to aid the reader in understanding your intentions.
  *
  * {{{
  *   @impl abstract class Impl(
  *     pluser: Pluser
  *   ) extends PlusedInt
  * }}}
  *
  * @see [[https://izumi.7mind.io/latest/release/doc/distage/basics.html#auto-traits Auto-Traits]]
  * @see [[https://izumi.7mind.io/latest/release/doc/distage/basics.html#auto-factories Auto-Factories]]
  */
final class impl extends DIStageAnnotation
