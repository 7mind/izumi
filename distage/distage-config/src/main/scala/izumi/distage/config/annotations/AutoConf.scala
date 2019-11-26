package izumi.distage.config.annotations

/**
  * Inject parameter from configuration.
  *
  * The config path is derived automatically from the context.
  *
  * For example, given a class:
  * {{{
  * class Service(@AutoConf hostPort: HostPort)
  * }}}
  *
  * where:
  * {{{
  * case class HostPort(host: String, port: Int)
  * }}}
  *
  * you'll be able to define configuration like this:
  * {{{
  * Service {
  *   HostPort {
  *     host = "example.com"
  *     port = 8080
  *   }
  * }
  * }}}
  *
  * If the requesting class is bound as named binding, you can disambiguate
  * the config path to uniquely configure that class:
  * {{{
  * new ModuleDef {
  *   make[Service].named("namedService")
  * }
  * }}}
  *
  * Config:
  * {{{
  * Service {
  *   namedService {
  *     HostPort {
  *       host = "example.com"
  *       port = 8080
  *     }
  *   }
  * }
  * }}}
  *
  * If a class requests several values of the same type from config, you can
  * disambiguate the config path using parameter names:
  *
  * {{{
  *   class Service(@AutoConf param1: HostPort, @AutoConf param2: HostPort)
  * }}}
  *
  * Config:
  *
  * {{{
  * Service {
  *   HostPort {
  *     param1 {
  *       host = "example.com"
  *       port = 8080
  *     }
  *
  *     param2 {
  *       host = "google.com"
  *       port = 80
  *     }
  *   }
  * }
  * }}}
  *
  * Specifically, the path is defined by segments:
  *
  * {{{
  * {binding_type}.{binding_id_key?}.{config_type}.{parameter_name?}
  * }}}
  *
  * Where segments marked with ? at the end are optional.
  *
  * Use this annotation when you need to inject one config section into <i>one</i> component
  *
  */
final class AutoConf extends ConfiguratorAnnotation
