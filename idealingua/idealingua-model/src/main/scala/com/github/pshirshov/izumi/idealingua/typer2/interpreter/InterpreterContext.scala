package com.github.pshirshov.izumi.idealingua.typer2.interpreter

import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId
import com.github.pshirshov.izumi.idealingua.typer2.model.Typespace2.ProcessedOp
import com.github.pshirshov.izumi.idealingua.typer2.{DomainIndex, RefRecorder, TsProvider, WarnLogger}

trait ContextProducer {
  def remake(ephemerals: Map[IzTypeId, ProcessedOp], newArgs: Interpreter.Args): InterpreterContext
}

class InterpreterContext(val index: DomainIndex, val logger: WarnLogger, val recorder: RefRecorder, provider: TsProvider, val args: Interpreter.Args) extends ContextProducer {
  val resolvers: Resolvers = new ResolversImpl(args, index)
  val typedefSupport: TypedefSupport = new TypedefSupportImpl(index, resolvers, args, recorder, logger, provider)
  val adts = new AdtSupport(typedefSupport, resolvers)
  val clones = new CloneSupport(index, typedefSupport, resolvers, adts, logger, provider)
  val templates = new TemplateSupport(this, args, typedefSupport, resolvers, logger, provider)
  val interfaceSupport = new InterfaceSupport(typedefSupport, adts, resolvers)
  val interpreter: Interpreter = new InterpreterImpl(typedefSupport, adts, templates, clones, interfaceSupport)

  def remake(ephemerals: Map[IzTypeId, ProcessedOp], newArgs: Interpreter.Args): InterpreterContext = {
    val newProvider = new TsProvider {
      override def freeze(): Map[IzTypeId, ProcessedOp] = provider.freeze() ++ ephemerals
    }
    new InterpreterContext(index, logger, recorder, newProvider, newArgs)
  }
}
