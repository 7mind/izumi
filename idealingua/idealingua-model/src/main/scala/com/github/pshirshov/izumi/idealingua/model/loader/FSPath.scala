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

  final case class Full(segments: Seq[String] , name: String) extends FSPath {
    def location: String = segments.mkString("/")

    override def asString: String = (segments :+ name).mkString("/")

    override def rename(update: String => String): FSPath = Full(segments, update(name))

    override def toString: String = s"/$location/$name"
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
}
