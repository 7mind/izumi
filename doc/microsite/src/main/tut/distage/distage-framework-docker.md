distage-framework-docker
========================

@@toc { depth=2 }

### Key Features

- Provides docker containers as resources
- Reasonable defaults with flexible configuration
- Excellent for providing docker implementations of services for integration tests

### Dependencies

Add the `distage-framework-docker` library:

@@@vars

```scala
libraryDependencies += "io.7mind.izumi" %% "distage-framework-docker" % "$izumi.version$"
```

@@@


### Overview

Use of `distage-framework-docker` generally follows:

- creating `ContainerDef`s for the containers the application requires
- declaring a `ModuleDef` that declares the container component
- including the `DockerSupportModule` in the application's modules

First, the required imports:

```scala mdoc:to-string
import izumi.distage.docker.ContainerDef
import izumi.distage.docker.Docker.DockerPort
import izumi.distage.model.definition.ModuleDef
import izumi.reflect.TagK
```

#### Create a `ContainerDef`

The `ContainerDef` is a trait that provides a high level interface for defining a docker container resource.

Example nginx container definition:

```scala mdoc:silent
object NginxDocker extends ContainerDef {
  val primaryPort: DockerPort = DockerPort.TCP(80)


  override def config: Config = {
    Config(
      image = "nginx:stable",
      ports = Seq(primaryPort)
    )
  }
}
```

#### Declare Container Components

To use this container a module that declares how to make this component is required:

```scala mdoc:to-string
class NginxDockerModule[F[_]: TagK] extends ModuleDef {
  make[NginxDocker.Container].fromResource {
    NginxDocker.make[F]
  }
}
```

- `modifyConfig`

- `dependOnDocker` - adds a dependency on a docker container. distage ensures the requested container
  is available before the dependent is provided.

#### Include `DockerSupportModule`

```scala mdoc:invisible

val _dockerSupportModuleCheck = {
  import izumi.fundamentals.platform.functional.Identity
  import izumi.distage.docker.modules.DockerSupportModule
  (DockerSupportModule, _: DockerSupportModule[Identity])
}

```

Include the `izumi.distage.docker.modules.DockerSupportModule` module in the application
modules. This module contains required component declarations and loads the `docker-reference.conf`.
Container resources depend on these components.

```scala mdoc:to-string
import cats.effect.IO
import com.typesafe.config.ConfigFactory
import distage.Injector
import izumi.distage.config.AppConfigModule
import izumi.distage.docker.modules.DockerSupportModule
import izumi.distage.effect.modules.CatsDIEffectModule
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.api.routing.StaticLogRouter
import izumi.logstage.distage.LogIOModule

object CorrectlyConfiguredApplication {

  val module = new ModuleDef {
    // our container module
    include(new NginxDockerModule[IO])

    // include the required docker support module
    include(new DockerSupportModule[IO])

    // standard support modules
    include(AppConfigModule(ConfigFactory.defaultApplication))
    include(new CatsDIEffectModule {})
    include(new LogIOModule[IO](StaticLogRouter.instance, false))
  }
  def run() = Injector().produceGetF[IO, NginxDocker.Container](module).use { _ =>
     IO("success")
  }
}

CorrectlyConfiguredApplication.run().unsafeRunSync()
```

If the `DockerSupportModule` is not included in an application then a get of a docker container
dependent resource will fail:

```scala mdoc:to-string
object IncorrectlyConfiguredApplication {
  val module = new ModuleDef {
    // our container module
    include(new NginxDockerModule[IO])

    // Note: missing an include[DockerSupportModule]

    include(AppConfigModule(ConfigFactory.defaultApplication))
    include(new CatsDIEffectModule {})
    include(new LogIOModule[IO](StaticLogRouter.instance, false))
  }
  def run() = Injector().produceGet[NginxDocker.Container](module).use { _ =>
     "impossible: will fail before here"
  }
}
```
```scala mdoc:crash:to-string
// Attempting to produce the NginxDocker.Container will fail
IncorrectlyConfiguredApplication.run()
```

### Docker Client Configuration

The `Docker.ClientConfig` is the configuration of the docker client used. Including the
module `DockerSupportModule` will provide a `Docker.ClientConfig`.

There are two primary mechanisms to change the `Docker.ClientConfig` provided by
`DockerSupportModule`:

1. Provide a `docker` configuration in the `application.conf`:

```hocon
# include the default configuration
include "docker-reference.conf"

# override docker object fields
docker {
  readTimeoutMs = 60000
  allowReuse = false
}
```

2. Override the `DockerSupportModule` using `overridenBy`:

```scala mdoc:to-string
import izumi.distage.docker.Docker

class CustomDockerConfigExampleModule[F[_] : TagK] extends ModuleDef {
  include(new DockerSupportModule[F] overridenBy new ModuleDef {
    make[Docker.ClientConfig].from {
      Docker.ClientConfig(
        readTimeoutMs = 10000,
        connectTimeoutMs = 10000,
        allowReuse = true,
        useRemote = true,
        useRegistry = true,
        remote = Option {
          Docker.RemoteDockerConfig(
            host = "tcp://localhost:2376",
            tlsVerify = true,
            certPath = "/home/user/.docker/certs",
            config = "/home/user/.docker"
          )
        },
        registry = Option {
          Docker.DockerRegistryConfig(
            url = "https://index.docker.io/v1/",
            username = "dockeruser",
            password = "ilovedocker",
            email = "dockeruser@github.com"
          )
        },
      )
    }
  })
}
```

### Usage in Integration Tests

(an example using the above nginx http containerdef)

### Reuse

By default, acquiring a container resource does not always start a fresh container. Likewise, on
releasing the resource the container will not be destroyed.  When a container resource is acquired
the docker system is inspected to determine if an appropriate container is already executing. If a
matching container is already running this container is referenced by the
`ContainerResource`. Otherwise a fresh container is started.  In both cases the acquired
`ContainerResource` will have passed any configured health checks.

#### Configuring Reuse

The `ContainerDef.Config.reuse` should be false to disable reuse for a specific container.  While
`Docker.ClientConfig.allowReuse` should be false to disable reuse throughout the application.

#### Improving Reuse Performance

When using reuse the cost of inspecting the docker system can be avoided using memoization roots.

### Examples

The `distage-example` project uses `distage-framework-docker` to provide a
[PostgresDockerModule](https://github.com/7mind/distage-example/blob/develop/src/test/scala/leaderboard/PostgresDockerModule.scala).

The `distage-framework-docker` project contains example `ContainerDef`s and modules for various
services under
[`izumi.distage.docker.examples`](https://github.com/7mind/izumi/tree/develop/distage/distage-framework-docker/src/main/scala/izumi/distage/docker/examples)
namespace.

```scala mdoc:to-string
import izumi.distage.docker.examples.PostgresDocker
import izumi.distage.model.definition.ModuleDef
import izumi.reflect.TagK

// a hypothetical structure for the postgres configuration used by the application.
case class PostgresServerConfig(
  host: String,
  port: Int
)

// a hypothetical module that binds the implementation of PostgresServerConfig
// to one provided by docker
class PostgresServerConfigDockerModule[F[_]: TagK] extends ModuleDef {
  make[PostgresServerConfig].from {
    container: PostgresDocker.Container => {
      val knownAddress = container.availablePorts.availablePorts(PostgresDocker.primaryPort).head
      PostgresServerConfig(knownAddress.hostV4, knownAddress.port)
    }
  }

  make[PostgresDocker.Container].fromResource {
    PostgresDocker.make[F]
  }
}

```

### Tips

To kill all containers spawned by distage, use the following command:

```shell
docker rm -f $(docker ps -q -a -f 'label=distage.type')
```

### References

- Introduced in [release 0.9.13](https://github.com/7mind/izumi/releases/tag/v0.9.13)
- An [example PR](https://github.com/7mind/distage-livecode/pull/2/files) showing how to use them.
