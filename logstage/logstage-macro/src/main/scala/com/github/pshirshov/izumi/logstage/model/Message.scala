package com.github.pshirshov.izumi.logstage.model


case class Message(template: StringContext, args: List[(String, Any)]) {

  import ListUtils._

  def argsMap: Map[String, Set[Any]] = args.toMultimap
}



