package izumi.fundamentals.platform.cli

import izumi.fundamentals.platform.cli.model.{ModalityArgs, MultiModalArgs}

class MultiModalArgsParserImpl extends MultiModalArgsParser {

  trait State {
    def freeze(): Either[Nothing, MultiModalArgs]
    def next(arg: String): State

    final def isId(arg: String): Option[String] = {
      if (arg.length > 1 && arg.startsWith(":")) {
        Some(arg.substring(1))
      } else {
        None
      }

    }
  }

  class StModalityArgs(global: Vector[String], mods: Vector[ModalityArgs], id: String, acc: Vector[String]) extends State {
    override def freeze(): Either[Nothing, MultiModalArgs] = {
      Right(MultiModalArgs(global, mods :+ ModalityArgs(id, acc)))
    }

    override def next(arg: String): State = {
      isId(arg) match {
        case Some(value) =>
          new StModalityArgs(global, mods :+ ModalityArgs(id, acc), value, Vector.empty)
        case None =>
          new StModalityArgs(global, mods, id, acc :+ arg)
      }
    }
  }

  class StPrimaryArgs(acc: Vector[String]) extends State {
    override def freeze(): Either[Nothing, MultiModalArgs] = Right(MultiModalArgs(acc, Vector.empty))

    override def next(arg: String): State = {
      isId(arg) match {
        case Some(value) =>
          new StModalityArgs(acc, Vector.empty, value, Vector.empty)
        case None =>
          new StPrimaryArgs(acc :+ arg)
      }
    }
  }

  override def parse(args: Array[String]): Either[Nothing, MultiModalArgs] = {
    args.foldLeft(new StPrimaryArgs(Vector.empty): State) { case (s, a) => s.next(a) }.freeze()
  }
}
