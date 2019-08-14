package izumi.distage.config.annotations

/**
  * Inject parameter from config value at the specified fully qualified path.
  *
  * For example, given a class:
  * {{{
  * class Service(@ConfPath("service.conf" hostPort: HostPort)
  * }}}
  *
  * where:
  * {{{
  * case class HostPort(host: String, port: Int)
  * }}}
  *
  * you'll be able to define configuration like this:
  * {{{
  * service {
  *   conf {
  *     host = "example.com"
  *     port = 8080
  *   }
  * }
  * }}}
  *
  * Note, you will NOT be able to specify the type of the case class:
  *
  * {{{
  * service {
  *   conf {
  *     HostPort /* ILLEGAL */ {
  *       host = "example.com"
  *       port = 8080
  *     }
  *   }
  * }
  * }}}
  *
  * Use this annotation if you are too lazy to learn how [[AutoConf]] or [[Conf]] work
  *
  */
final class ConfPath(val path: String) extends ConfiguratorAnnotation
