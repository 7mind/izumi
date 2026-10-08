package izumi.distage.testkit.runner

private[runner] object JvmSuiteLoader {
  def load(name: String, loader: ClassLoader): TestSuite = {
    try classOf[TestSuite].cast(Class.forName(name, true, loader).getConstructor().newInstance())
    catch { case cause: LinkageError => throw new IllegalStateException("Cannot load suite " + name, cause) }
  }
}
