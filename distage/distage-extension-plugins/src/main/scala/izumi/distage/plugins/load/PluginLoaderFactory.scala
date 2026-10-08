package izumi.distage.plugins.load

/** Constructs a loader using an owner-local cache or an explicitly compatible owner-local policy.
  * New test sessions retain one creation attempt per factory reference, including NonFatal failures.
  * Bind every mutable delegate before loading; prebuilt shared state and incompatible scanner domains remain caller-owned.
  */
trait PluginLoaderFactory {
  def create(packageCache: PluginPackageCache): PluginLoader
}
