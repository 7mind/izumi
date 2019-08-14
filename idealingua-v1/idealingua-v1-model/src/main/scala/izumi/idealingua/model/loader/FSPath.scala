package izumi.idealingua.model.loader

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

  final case class Full private(location: Seq[String], name: String) extends FSPath {
    override def segments: Seq[String] = location :+ name

    override def asString: String = (location :+ name).mkString("/", "/", "")

    override def rename(update: String => String): FSPath = Full(location, update(name))

    override def toString: String = asString
  }

  final case class Name private(name: String) extends FSPath {
    override def asString: String = name

    override def segments: Seq[String] = Seq(name)

    override def rename(update: String => String): FSPath = Name(update(name))

    override def toString: String = s"?/$name"
  }

  def apply(pkg: Seq[String]): FSPath = {
    val path = pkg.init.dropWhile(_.isEmpty)
    val name = pkg.last
    assert(path.forall(p => p.nonEmpty && !p.contains(separator)))
    assert(name.nonEmpty)
    if (path.nonEmpty) {
      FSPath.Full(path, name)
    } else {
      FSPath.Name(name)
    }
  }

  def parse(path: String): FSPath = apply(path.split(separator))
}
