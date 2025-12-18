---
out: index.html
---

# distage

```scala mdoc:reset:invisible:to-string
System.setProperty(izumi.fundamentals.platform.PlatformProperties.`izumi.app.disable-terminal-colors`.name, "true")
```

`distage` is a pragmatic dependency injection library for Scala and Scala.js. It combines the simplicity and expressiveness of pure FP with the flexibility and extreme late-binding, traditionally associated with Java dependency injection frameworks, such as Guice.

`distage` supports any Scala style, whether it's @ref[Tagless Final Style](basics.md#tagless-final-style), @ref[ZIO Layer](basics.md#zio-environment-bindings), ordinary FP, actor-based or imperative Scala.

## Getting started

The best way to get started is to clone [`distage-example`](https://github.com/7mind/distage-example) project and play around with it.

It shows how to write an idiomatic `distage` application from scratch and how to:

- write tests using @ref[`distage-testkit`](distage-testkit.md)
- setup portable test environments using @ref[`distage-framework-docker`](distage-framework-docker.md)
- create @ref[role-based applications](distage-framework.md#roles)
- enable @ref[compile-time checks](distage-framework.md#compile-time-checks) for fast feedback on wiring errors

```scala mdoc:invisible
/**
add to distage-example

- [done] how to setup graalvm native image with distage
- how to debug dump graphs and render to graphviz [Actually, we have a GUI component now, can we show em there???]
*/
```

## Why distage?

1. **Fast startup and tests**:

    `distage` guarantees that no unnecessary instantiations will happen during your tests or application startup. `distage` itself is very fast, in part due to not using any runtime reflection.

2. **Cross-platform**:

    `distage` is available for JVM, Scala.js and GraalVM Native Image.

3. **Compile-time error detection**:

    `distage` can detect wiring errors @ref[at compile-time](distage-framework.md#compile-time-checks) for fast feedback during development.

4. **Effect-type support**:

    `distage` is polymorphic in effect type. Whether you use cats-effect `IO`, `ZIO`, a custom `F[_]` type or direct style with no effect type, all of distage's lifecycle management and test utilities work seamlessly. No effect type is privileged; distage adapts to your stack, not the other way around.

5. **Lifecycle management**:

    `distage` supports component lifecycle via native `Lifecycle`, cats-effect `Resource`, or ZIO `Scope`/`ZLayer`/`ZManaged`. Startup and cleanup follow dependency order, with cleanup guaranteed even on failure.

6. **Fast integration tests**:

    @ref[distage-testkit](distage-testkit.md) allows you to reuse expensive resources (such as database connections and docker containers) across multiple test suites, gaining performance without sacrificing correctness. Easily describe test environments, share heavy resources across all the test suites in the environment, use the power of DI to override components and run your tests under different scenarios.

7. **Fail-fast integration checks**:

    The @ref[Integration checks](distage-testkit.md#using-integrationcheck) feature dynamically skips tests when external dependencies are unavailable (e.g. no Docker daemon), and in production ensures applications fail fast with clear diagnostics.

8. **Portable Docker test environments**:

    @ref[distage-framework-docker](distage-framework-docker.md) turns Docker containers into managed resources with automatic health checks, port discovery, and cross-test reuse. Define containers once, inject them anywhere, and get reproducible integration tests on any machine with Docker. Or use them without DI, via monadic `Lifecycle` type.

9. **Simplify development workflow**:

    @ref[distage-framework](distage-framework.md) allows you to develop Role-based applications, letting you run all your services in one process for development or test purposes (and even switch to mock implementations with a single commandline argument). In production, Role-based applications allow you to deploy and orchestrate fewer containers and achieve a higher computation density.

10. **Simple debugging**:

    Your wiring is just data. `Plan` is an immutable value you can inspect, print, render @ref[to GraphViz](debugging.md#graphviz-rendering), or rewrite it entirely - all before any instantiations happen.

11. **Non-invasive**:

    `distage` is designed to not impact the way your Scala code is written, it just removes all the initialization boilerplate. You don't need to learn magic tricks to write components in a distage application.

> Given its native support for type classes and higher-kinded types -- both features indispensable to functional programming -- distage is one of the leading dependency injection libraries out there. Bonus points for being built by a wicked-smart team that contributes to ZIO!
>
> — *John A. De Goes*

## FAQ

**Q**: How to pronounce `distage`?

**A**: 'Dee-stage'

**Q**: How do I switch between production and test implementations of components?

**A**: Use @ref[Activation Axis](basics.md#activation-axis)

## Documentation

- @ref[Basics](basics.md)
- @ref[Debugging](debugging.md)
- @ref[Advanced Features](advanced-features.md)
- @ref[distage-framework](distage-framework.md)
- @ref[distage-framework-docker](distage-framework-docker.md)
- @ref[distage-testkit](distage-testkit.md)
- @ref[Syntax Reference](reference.md)

## Further reading

Example projects:

* [DIStage Example Project](https://github.com/7mind/distage-example)
* [Idealingua Example Project with TypeScript and Scala](https://github.com/7mind/idealingua-example)

Support Chats:

* [Izumi on Gitter](https://gitter.im/7mind/izumi)
* [Izumi User Group [RU] on Telegram](https://t.me/scala_any/708)
* [Izumi User Group [EN] on Telegram](https://t.me/izumi_en)
* [Discussions on Github](https://github.com/7mind/izumi/discussions)

Videos:

* [Izumi 1.0: Your Next Scala Stack](https://www.youtube.com/watch?v=o65sKWnFyk0)
* [Scala, Functional Programming and Team Productivity](https://www.youtube.com/watch?v=QbdeVoL4hBk)
* [Hyper-pragmatic Pure FP Testing with distage-testkit](https://www.youtube.com/watch?v=CzpvjkUukAs)
* [Livecoding: DIStage & Bifunctor Tagless Final](https://www.youtube.com/watch?v=C0srg5T0E4o&t=4971)
* [DevInsideYou — Tagless Final with BIO](https://www.youtube.com/watch?v=ZdGK1uedAE0&t=580s)

Slides:

* [Izumi 1.0: Your Next Scala Stack](https://www.slideshare.net/7mind/izumi-10-your-next-scala-stack)
* [Scala, Functional Programming and Team Productivity](https://www.slideshare.net/7mind/scala-functional-programming-and-team-productivity)
* [Hyper-pragmatic Pure FP Testing with distage-testkit](https://www.slideshare.net/7mind/hyperpragmatic-pure-fp-testing-with-distagetestkit)
* [distage: Staged Dependency Injection](https://www.slideshare.net/7mind/scalaua-distage-staged-dependency-injection)
* [LogStage: Zero-cost Structured Logging](https://www.slideshare.net/7mind/logstage-zerocosttructuredlogging)
* [More slides](https://github.com/7mind/slides)

@@@ index

* [Overview](basics.md)
* [Debugging](debugging.md)
* [Advanced Features](advanced-features.md)
* [distage-framework](distage-framework.md)
* [distage-framework-docker](distage-framework-docker.md)
* [distage-testkit](distage-testkit.md)
* [Syntax Reference](reference.md)

@@@
