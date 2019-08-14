package com.github.pshirshov.izumi.distage.config.annotations

/**
  * Inject parameter from config value at the specified path.
  *
  * For example, given a class:
  * {{{
  * class Service(@Conf("cassandra") cassandraHost: HostPort)
  * }}}
  *
  * where:
  * {{{
  * case class HostPort(host: String, port: Int)
  * }}}
  *
  * you'll be able to define configuration like this:
  * {{{
  * cassandra {
  *   HostPort {
  *     host = "cassandra"
  *     port = 9003
  *   }
  * }
  * }}}
  *
  * Note that you may also omit the name of the case class:
  * {{{
  * cassandra {
  *   host = "cassandra"
  *   port = 9003
  * }
  * }}}
  *
  * Specifically, this annotation differs from [[AutoConf]] only
  * in that the binding type and binding id components are replaced
  * by explicitly defined path, so that the whole path is defined by segments:
  *
  * {{{
  * {path}.{config_type?}.{parameter_name?}
  * }}}
  *
  * Where segments marked with ? at the end are optional.
  *
  * Use this annotation when you want to share one config section between <i>several</i> components
  *
  */
final class Conf(val name: String) extends ConfiguratorAnnotation


