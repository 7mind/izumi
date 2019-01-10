package com.github.pshirshov.izumi.idealingua.model.loader

sealed trait FSPath {
  def name: String

  def asString: String

  def segments: Seq[String]

  def rename(update: String => String): FSPath

  def move(update: Seq[String] => Seq[String]): FSPath = {
    this match {
      case FSPath.Full(location, name) =>
        FSPath(update(location) :+ name)
      case FSPath.Name(name) =>
        FSPath(update(Seq.empty) :+ name)
    }
  }
}

object FSPath {
  final val separator = '/'

  final case class Full(location: Seq[String], name: String) extends FSPath {
    override def segments: Seq[String] = location :+ name

    override def asString: String = (location :+ name).mkString("/", "/", "")

    override def rename(update: String => String): FSPath = Full(location, update(name))

    override def toString: String = asString
  }

  final case class Name(name: String) extends FSPath {

    override def asString: String = name

    override def segments: Seq[String] = Seq(name)

    override def rename(update: String => String): FSPath = Name(update(name))

    override def toString: String = s"?/$name"
  }

  def apply(pkg: Seq[String]): FSPath = {
    if (pkg.init.nonEmpty) {
      FSPath.Full(pkg.init, pkg.last)
    } else {
      FSPath.Name(pkg.last)
    }
  }

  def parse(path: String): FSPath = apply(path.split(separator))
}
