package izumi.distage.model.definition

/**
  * Annotation for summoning named instances.
  *
  * Example:
  * {{{
  *   val module = new ModuleDef {
  *     make[Int].named("three").from(3)
  *     make[Int].named("five").from(5)
  *   }
  *
  *   Injector().produce(module).run {
  *     (five: Int @Id("five"), three: Int @Id("three")) =>
  *       assert(5 == five)
  *       assert(3 == three)
  *   }
  * }}}
  */
final class Id(val name: String) extends DIStageAnnotation
