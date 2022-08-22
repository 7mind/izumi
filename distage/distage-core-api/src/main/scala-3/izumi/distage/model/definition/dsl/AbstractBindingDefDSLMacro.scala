package izumi.distage.model.definition.dsl

trait AbstractBindingDefDSLMacro[BindDSL[_]] {
  final protected[this] def make[T]: BindDSL[T] = ???
}
