package org.bitbucket.pshirshov.izumi.di.definition

import org.bitbucket.pshirshov.izumi.di.{Tag, TypeFull}
import org.bitbucket.pshirshov.izumi.di.definition.Binding.{EmptySetBinding, SetBinding, SingletonBinding}
import org.bitbucket.pshirshov.izumi.di.model.{DIKey, EqualitySafeType}

case class TrivialDIDef(bindings: Seq[Binding]) extends ContextDefinition

sealed trait WrappedFunction {
  def ret: TypeFull

  def args: Seq[TypeFull]

  //def f: Any
  def call(args: Seq[Any]): Any
}

object WrappedFunction {

  case class W0[R: Tag, T1: Tag](f: () => R) extends WrappedFunction {
    def ret: TypeFull = EqualitySafeType.get[R]

    def args: Seq[TypeFull] = Seq.empty

    override def call(args: Seq[Any]): R = f()
  }

  case class W1[R: Tag, T1: Tag](f: (T1) => R) extends WrappedFunction {
    def ret: TypeFull = EqualitySafeType.get[R]

    def args: Seq[TypeFull] = Seq(EqualitySafeType.get[T1])

    override def call(args: Seq[Any]): R = f(args.head.asInstanceOf[T1])
  }

  case class W2[R: Tag, T1: Tag, T2: Tag](f: (T1, T2) => R) extends WrappedFunction {
    def ret: TypeFull = EqualitySafeType.get[R]

    def args: Seq[TypeFull] = Seq(EqualitySafeType.get[T1], EqualitySafeType.get[T2])

    override def call(args: Seq[Any]): R = f(
      args.head.asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
    )
  }

  case class W3[R: Tag, T1: Tag, T2: Tag, T3: Tag](f: (T1, T2, T3) => R) extends WrappedFunction {
    def ret: TypeFull = EqualitySafeType.get[R]

    def args: Seq[TypeFull] = Seq(EqualitySafeType.get[T1], EqualitySafeType.get[T2], EqualitySafeType.get[T3])

    override def call(args: Seq[Any]): R = f(
      args.head.asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
      , args(2).asInstanceOf[T3]
    )
  }

}

object TrivialDIDef {

  def symbolDef[T: Tag]: ImplDef = ImplDef.TypeImpl(EqualitySafeType.get[T])

  class BindingSupport(bindings: Seq[Binding]) {
    def nameless[T: Tag]: BindingSupport = {
      new BindingSupport(bindings :+ SingletonBinding(DIKey.get[T], symbolDef[T]))
    }

    def namelessProvider[T: Tag](f: WrappedFunction): BindingSupport = {
      new BindingSupport(bindings :+ SingletonBinding(DIKey.get[T], ImplDef.ProviderImpl(f.ret, f)))
    }


    def nameless[T: Tag, I <: T : Tag]: BindingSupport = {
      new BindingSupport(bindings :+ SingletonBinding(DIKey.get[T], symbolDef[I]))
    }

    def nameless[T: Tag](instance: T): BindingSupport = {
      new BindingSupport(bindings :+ SingletonBinding(DIKey.get[T], ImplDef.InstanceImpl(EqualitySafeType.get[T], instance)))
    }

    def named[T: Tag](name: String): BindingSupport = {
      new BindingSupport(bindings :+ SingletonBinding(DIKey.get[T].named(name), symbolDef[T]))
    }

    def namedProvider[T: Tag](name: String)(f: WrappedFunction): BindingSupport = {
      new BindingSupport(bindings :+ SingletonBinding(DIKey.get[T].named(name), ImplDef.ProviderImpl(f.ret, f)))
    }

    def named[T: Tag, I <: T : Tag](name: String): BindingSupport = {
      new BindingSupport(bindings :+ SingletonBinding(DIKey.get[T].named(name), symbolDef[I]))
    }

    def named[T: Tag](instance: T, name: String): BindingSupport = {
      new BindingSupport(bindings :+ SingletonBinding(DIKey.get[T].named(name), ImplDef.InstanceImpl(EqualitySafeType.get[T], instance)))
    }

    // sets
    def namelessEmptySet[T: Tag]: BindingSupport = {
      new BindingSupport(bindings :+ EmptySetBinding(DIKey.get[Set[T]]))
    }

    def namedEmptySet[T: Tag](name: String): BindingSupport = {
      new BindingSupport(bindings :+ EmptySetBinding(DIKey.get[Set[T]].named(name)))
    }

    def namelessSet[T: Tag, I <: T : Tag]: BindingSupport = {
      new BindingSupport(bindings :+ SetBinding(DIKey.get[Set[T]], symbolDef[I]))
    }

    def namelessSet[T: Tag](instance: T): BindingSupport = {
      new BindingSupport(bindings :+ SetBinding(DIKey.get[Set[T]], ImplDef.InstanceImpl(EqualitySafeType.get[T], instance)))
    }

    def namedSet[T: Tag, I <: T : Tag](name: String): BindingSupport = {
      new BindingSupport(bindings :+ SetBinding(DIKey.get[Set[T]].named(name), symbolDef[I]))
    }

    def namedSet[T: Tag](instance: T, name: String): BindingSupport = {
      new BindingSupport(bindings :+ SetBinding(DIKey.get[Set[T]].named(name), ImplDef.InstanceImpl(EqualitySafeType.get[T], instance)))
    }

    def finish: ContextDefinition = TrivialDIDef(bindings)
  }

  final val empty: BindingSupport = new BindingSupport(Seq.empty)
}
