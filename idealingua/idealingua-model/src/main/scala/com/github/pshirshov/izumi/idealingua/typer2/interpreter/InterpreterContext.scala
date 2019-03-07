package com.github.pshirshov.izumi.idealingua.typer2.interpreter

import com.github.pshirshov.izumi.idealingua.typer2.{DomainIndex, RefRecorder, WarnLogger}

trait ContextProducer {
  def remake(newArgs: Interpreter.Args): InterpreterContext
}

class InterpreterContext(val index: DomainIndex, val logger: WarnLogger, val recorder: RefRecorder, val args: Interpreter.Args) extends ContextProducer {
  val resolvers: Resolvers = new ResolversImpl(args, index)
  val typedefSupport: TypedefSupport = new TypedefSupportImpl(index, resolvers, args, recorder, logger)
  val adts = new AdtSupport(typedefSupport, resolvers)
  val clones = new CloneSupport(index, args, typedefSupport, resolvers, adts, logger)
  val templates = new TemplateSupport(this, args, typedefSupport, resolvers, logger)
  val interpreter: Interpreter = new InterpreterImpl(typedefSupport, adts, templates, clones)

  def remake(newArgs: Interpreter.Args): InterpreterContext = {
    new InterpreterContext(index, logger, recorder, newArgs)
  }
}
