---
out: index.html
---

# distage: Staged Dependency Injection

```scala mdoc:reset:invisible:to-string
System.setProperty(izumi.fundamentals.platform.PlatformProperties.`izumi.app.disable-terminal-colors`.name, "true")
```

`distage` is a pragmatic module system for Scala and Scala.js. It combines the simplicity and expressiveness of pure FP with the flexibility and extreme late-binding, traditionally associated with Java dependency injection frameworks, such as Guice.

`distage` supports any Scala style, whether it's @ref[Tagless Final Style](basics.md#tagless-final-style), @ref[ZIO Layer](basics.md#zio-has-bindings), ordinary FP, actor-based or imperative Scala.

## Getting started

The best way get started is to clone [`distage-example`](https://github.com/7mind/distage-example) sample project and play around with it.

It shows how to write an idiomatic `distage` application from scratch and how to:

- write tests using @ref[`distage-testkit`](distage-testkit.md)
- setup portable test environments using @ref[`distage-framework-docker`](distage-framework-docker.md)
- create @ref[role-based applications](distage-framework.md#roles)
- enable @ref[compile-time checks](distage-framework.md#compile-time-checks) for fast feedback on wiring errors

```scala mdoc:invisible
/**
add to distage-example

- how to setup graalvm native image with distage
- how to debug dump graphs and render to graphviz [Actually, we have a GUI component now, can we show em there???]
*/
```

## Why distage?

1. **Faster applications and tests**:
    `distage` guarantees that no unnecessary instantiations will happen during your tests or application startup. `distage` itself is very fast, in part due to not using Java reflection.

2. **Faster integration tests**:
    @ref[distage-testkit](distage-testkit.md) allows you to reuse expensive resources (such as database connections and docker containers)
    across multiple test suites, gaining performance without sacrificing correctness.

3. **Managed test environments**:
    @ref[distage-testkit](distage-testkit.md) eliminates all the hard work of setting up test environments, especially configurable ones. Easily describe tests environments, share heavy resources across all the test suites in the environment, use the power of DI to override components and run your tests under different scenarios.

4. **Compile-time error detection**:
    `distage` can detect wiring errors @ref[at compile-time](distage-framework.md#compile-time-checks) for fast feedback during development.
    Despite that, `distage` extensions are simple to write and do not require type-level programming.

5. **Early failure detection**:
    `distage` performs all the integration checks for your application and tests even before any instantiations happen.

6. **Simplify development workflow**:
    @ref[distage-framework](distage-framework.md) allows you to develop Role-based applications, letting you run all your services in one process for development purposes (and even switch to mock implementations with a single commandline argument).

7. **Easy deployment**:
    Role-based applications allow you to deploy and orchestrate fewer containers and achieve a higher computation density.

8. **Simple debugging**:
    `distage` provides you insights about your application structure and allows you to introspect and modify it on the fly, before any instantiations happen.

9. **Lifecycle management**:
    `distage` supports resources and lifecycle natively and guarantees proper cleanups even when something went wrong.

10. **Non-invasive**:
    `distage` is designed to not impact the way your Scala code is written, it just removes all the initialization boilerplate.
      You don't need to learn magic tricks to write components in a distage application.

11. **Cross-platform**:
    `distage` is available for JVM, Graal Native Image and Scala.js.

> Given its native support for type classes and higher-kinded types -- both features indispensable to functional programming -- distage is one of the leading dependency injection libraries out there. Bonus points for being built by a wicked-smart team that contributes to ZIO!
>
> — *John A. De Goes*

## FAQ

**Q**: How to pronounce `distage`?

**A**: 'Dee-stage'

**Q**: How do I switch between production and test implementations of components?

**A**: Use @ref[Activation Axis](basics.md#activation-axis)

## Documentation

- @ref[Overview](basics.md)
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
