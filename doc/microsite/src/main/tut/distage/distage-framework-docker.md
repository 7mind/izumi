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

- Create @scaladoc[`ContainerDef`](izumi.distage.docker.ContainerDef)s for the containers the
  application requires
- Create a module that declares the container component
- Include the @scaladoc[`DockerSupportModule`](izumi.distage.docker.modules.DockerSupportModule) in
  the application's modules

First, the required imports:

```scala mdoc:silent
import izumi.distage.docker.ContainerDef
import izumi.distage.docker.Docker
import izumi.distage.model.definition.ModuleDef
import izumi.reflect.TagK
```

#### Create a `ContainerDef`

The @scaladoc[`ContainerDef`](izumi.distage.docker.ContainerDef) is a trait used to define a docker
container. Extend this trait and provide and implementation of the `config` method.

The required parameters of `Config` are:

- `image` - the docker image to use
- `ports` - ports to map on the docker container

See @scaladoc[`Docker.ContainerConfig[T]`](izumi.distage.docker.Docker.ContainerConfig) for
additional parameters.

Example nginx container definition:

```scala mdoc:silent
object NginxDocker extends ContainerDef {
  val primaryPort: Docker.DockerPort = Docker.DockerPort.TCP(80)

  override def config: Config = {
    Config(
      image = "nginx:stable",
      ports = Seq(primaryPort)
    )
  }
}
```

#### Declare Container Components

To use this container a module that declares this component is required:

##### `make`

```scala mdoc:silent
class NginxDockerModule[F[_]: TagK] extends ModuleDef {
  make[NginxDocker.Container].fromResource {
    NginxDocker.make[F]
  }
}
```

##### `modifyConfig`

The @scaladoc[`DockerProviderExtensions`](izumi.distage.docker.DockerContainer.DockerProviderExtensions)
provides additional APIs for modiying the container definition.

Use `modifyConfig` to modify the configuration of a container. The modifier is instantiated to a
`ProviderMagnet` which will summon any additional dependencies.

For example, To change the user of the nginx container:

```scala mdoc:silent
class NginxRunAsAdminModule[F[_]: TagK] extends ModuleDef {
  make[NginxDocker.Container].fromResource {
    NginxDocker.make[F].modifyConfig { () => (old: NginxDocker.Config) =>
      old.copy(user = Option("admin"))
    }
  }
}
```

Suppose the `HostHtmlRoot` is a component provided by the application modules. This path
can be added to the nginx containers mounts by adding this to the additional dependencies of
the provider magnet:

```scala mdoc:silent
case class HostHtmlRoot(path: String)

class NginxWithMountsDockerModule[F[_]: TagK] extends ModuleDef {
  make[NginxDocker.Container].fromResource {
    NginxDocker.make[F].modifyConfig {
      (hostRoot: HostHtmlRoot) =>
        (old: NginxDocker.Config) =>
          val htmlMount = Docker.Mount(hostRoot.path, "/usr/share/nginx/html")
          old.copy(mounts = old.mounts :+ htmlMount)
    }
  }
}
```

##### `dependOnDocker`

`dependOnDocker` adds a dependency on a given docker container. distage ensures the requested
  container is available before the dependent is provided.

Suppose an integration under test requires a nginx plus memcached system. One option is to
use `dependOnDocker` to declare the nginx container depends on the memcached container:

```scala mdoc:silent

object MemcachedDocker extends ContainerDef {
  val primaryPort: Docker.DockerPort = Docker.DockerPort.TCP(11211)

  override def config: Config = {
    Config(
      image = "memcached",
      ports = Seq(primaryPort)
    )
  }
}

class NginxWithMemcachedModule[F[_]: TagK] extends ModuleDef {
  make[NginxDocker.Container].fromResource {
    NginxDocker.make[F].dependOnDocker(MemcachedDocker)
  }
}
```

Another example of dependencies between containers is in "Docker Container Networks" below.

#### Include `DockerSupportModule`

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
dependent resource will fail with a `izumi.distage.model.exceptions.ProvisioningException`.

### Docker Container Environment

The docker container's ports are remapped to fresh ports on the docker host. The allocated
ports are:

- Added to the container as the environment variables of the pattern `DISTAGE_PORT_<proto>_<originalPort>`. The
  value is the integer port number.

- Properties of `DockerContainer`:
    - `connectivity.dockerPorts` - the port information prior to any health checks
    - `availablePorts.availablePorts` - the port information that the health checks verified

- Added to the docker system as labels of the pattern `distage.port.<proto>.<originalPort>`. The
  value is the integer port number.

### Docker Container Networks

`distage-framework-docker` can also automatically manage
[docker networks](https://docs.docker.com/engine/reference/commandline/network/).

To connect containers to the same docker network use a
@scaladoc[`ContainerNetworkDef`](izumi.distage.docker.ContainerNetworkDef):

1. Create a `ContainerNetworkDef`
2. Add the network to each container's config.

This will ensure the containers are all connected to the network. Assuming no reuse, distage will
create the required network and add each container configured to that network.

#### Create a `ContainerNetworkDef`

A minimal @scaladoc[`ContainerNetworkDef`](izumi.distage.docker.ContainerNetworkDef) uses the default
configuration.

```mdoc:silent
object ClusterNetwork extends ContainerNetworkDef {
  override def config: Config = Config()
}
```

By default, this object identifies the network. The tag type uniquely identifies the network within
the application. Any created network will be labeled with this name as well.

#### Add to Container Config

A container will be connected to all networks in the `networks` of the `config`. The method
`connectToNetwork` adds a dependency on the network defined by a `ContainerNetworkDef`.

For example:

```mdoc:silent
class NginxWithMemcachedOnNetworkModule[F[_]: TagK] extends ModuleDef {
  make[ClusterNetwork.Network].fromResource {
    ClusterNetwork.make[F]
  }
  make[MemcachedDocker.Container].fromResource {
    NginxDocker.make[F].connectToNetwork(ClusterNetwork)
  }
  make[NginxDocker.Container].fromResource {
    NginxDocker.make[F].dependOnDocker(MemcachedDocker).connectToNetwork(ClusterNetwork)
  }
}
```

The use of `connectToNetwork` automatically adds a dependency on `ClusterNetwork.Network` to each
container.

#### Container Network Reuse

Container networks, like containers, will be reused by default. If there is an existing network that
matches a definition then that network will be used. This can be disabled by setting the `reuse`
configuration to false:

```mdoc:silent
object FreshClusterNetwork extends ContainerNetworkDef {
  override def config: Config = Config(reuse = false)
}
```

For an existing network to be reused the config and object name at time of creation must be the same
as the current config and object.

### Docker Client Configuration

The @scaladoc[`Docker.ClientConfig`](izumi.distage.docker.Docker.ClientConfig) is the configuration
of the docker client used. Including the module `DockerSupportModule` will provide a
`Docker.ClientConfig`.

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
        allowReuse = false,
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

### Container Reuse

By default, acquiring a container resource does not always start a fresh container. Likewise, on
releasing the resource the container will not be destroyed.  When a container resource is acquired
the docker system is inspected to determine if a matching container is already executing. If a
matching container is already running this container is referenced by the
`ContainerResource`. Otherwise a fresh container is started.  In both cases the acquired
`ContainerResource` will have passed configured health checks.

#### Matching Containers for Reuse

For an existing container to be reused, all the following must be true:

- The current client config has `allowReuse == true`
- The container config has `reuse == true`
- The running container was created for reuse
- The running container uses the same image as the container config.
- All ports requested in the container config must be mapped for the running container

#### Configuring Reuse

The `ContainerDef.Config.reuse` should be false to disable reuse for a specific container.  While
`Docker.ClientConfig.allowReuse` should be false to disable reuse throughout the application.

#### Improving Reuse Performance

When using reuse the cost of inspecting the docker system can be avoided using memoization roots.

### Usage in Integration Tests

### Examples

The `distage-example` project uses `distage-framework-docker` to provide a
[PostgresDockerModule](https://github.com/7mind/distage-example/blob/develop/src/test/scala/leaderboard/PostgresDockerModule.scala).

The `distage-framework-docker` project contains example `ContainerDef`s and modules for various
services under
[`izumi.distage.docker.examples`](https://github.com/7mind/izumi/tree/develop/distage/distage-framework-docker/src/main/scala/izumi/distage/docker/examples)
namespace.

```scala mdoc:silent
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

### Troubleshooting

```
// izumi.distage.model.exceptions.ProvisioningException: Provisioner stopped after 1 instances, 2/14 operations failed:
//  - {type.izumi.distage.docker.DockerClientWrapper[=λ %0 → IO[+0]]} (distage-framework-docker.md:40), MissingInstanceException: Instance is not available in the object graph: {type.izumi.distage.docker.DockerClientWrapper[=λ %0 → IO[+0]]}.
```

The `DockerSupportModule` was not included in the application modules. The component
`DockerClientWrapper` is provided by `izumi.distage.docker.modules.DockerSupportModule`.

### References

- Introduced in [release 0.9.13](https://github.com/7mind/izumi/releases/tag/v0.9.13)
- An [example PR](https://github.com/7mind/distage-livecode/pull/2/files) showing how to use them.
- The `distage-example` [PostgresDockerModule](https://github.com/7mind/distage-example/blob/develop/src/test/scala/leaderboard/PostgresDockerModule.scala).
