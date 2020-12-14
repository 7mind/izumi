---
out: index.html
---

# distage: Staged Dependency Injection

`distage` is a pragmatic module system for Scala and Scala.js. It combines the simplicity and expressiveness of pure FP with the flexibility and extreme late-binding, traditionally associated with object-oriented dependency injection frameworks, such as Guice.

`distage` is suitable for wiring @ref[Tagless Final Style](basics.md#tagless-final-style),
@ref[ZIO ZLayer-based applications](basics.md#zio-has-bindings), and ordinary FP, actor-based or imperative Scala applications.

## Getting started

The best way get started is to clone [`distage-example`](https://github.com/7mind/distage-example) sample project and play around with it.

It shows how to write an idiomatic `distage` application from scratch and how to:

- write tests using @ref[`distage-testkit`](distage-testkit.md)
- setup portable test environments using @ref[`distage-framework-docker`](distage-framework-docker.md)
- create @ref[role-based applications](distage-framework.md#roles)
- enable @ref[compile-time checks](distage-framework.md#compile-time-checks) for fast-feedback on wiring errors

```scala mdoc:invisible
/**
add to distage-example

- how to setup graalvm native image with distage
- how to debug dump graphs and render to graphviz [Actually, we have a GUI component now, can we show em there???]
*/
```

## Why distage?

1. **Faster applications and tests**:
    `distage` guarantees that no unnecessary instantiations will happen during your tests or application startup. `distage` itself is very fast, in part due to not relying on any sort of runtime reflection.
2. **Quick failure detection**:
    `distage` performs all the integration checks for your application and tests even before any instantiations happened.    
3. **Simple tests**:
    `distage` eliminates all the hard work of setting up your test environments, especially configurable ones.
4. **Better integration tests**:
    @ref[distage-testkit](distage-testkit.md) allows you to reuse expensive resources (like database connections and docker containers)
    across multiple integration tests, gaining performance and without sacrificing correctness.
5. **Simple development workflow**:
    @ref[distage-framework](distage-framework.md) allows you to develop Role-Based Applications, a fusion of Microservices and Monoliths,
     letting you run all your services in one process for development purposes (and even switch to mock implementations with a single commandline argument).
6. **Easier deployments**:
   Role-based applications allow you to deploy and orchestrate less components and achieve higher computational density.
7. **Fast compile times and low mental overhead**:
    Unlike fully compile-time DIs, `distage` does not impose a compile time penalty.
    `distage` extensions are simple to write and do not require type-level programming.
8. **Simple debugging**:
    `distage` provides you important insights about your application and allows you to introspect and modify it on the fly, 
    before any instantiations happen.
9. **High Correctness**:
    `distage` supports resources and lifecycle natively and guarantees proper cleanups even when something went wrong.
10. **No reflection**:
    `distage` generates constructors and [type information](https://blog.7mind.io/lightweight-reflection.html) at compile-time and does not use Scala reflection. As such, it's compatible with GraalVM Native Image and Scala.js.
11. **Non-invasive**:
    `distage` is designed to not impact the way your Scala code is written, it just removes all the initialization boilerplate.
    You don't need to learn magic tricks to write components in a distage application.

`distage` is recommended by industry leaders:

> Given its native support for type classes and higher-kinded types -- both features indispensable to functional programming -- distage is one of the leading dependency injection libraries out there. Bonus points for being built by a wicked-smart team that contributes to ZIO! 
> 
> — *John A. De Goes*

FAQ
---

**Q**: How to pronounce `distage`?

**A**: 'Dee-stage'

**Q**: Isn't it unsafe to use runtime dependency injection?

**A**: `distage` is split into two stages, first a wiring `plan` is calculated, only afterwards it is executed. Because of this,
you can @ref[test](debugging.md#testing-plans) the `Plan` for errors very fast, without executing any effects of your wiring –
if tests pass, the wiring will succeed at runtime. Testing can also be performed at compile-time – `distage-framework` module
a few experimental @ref[macros](distage-framework.md#compile-time-checks) for aborting compilation on planning errors.

**Q**: How do I switch between production and test implementations of components?

**A**: Use @ref[Activation Axis](basics.md#activation-axis)

Documentation
-------------

- @ref[Overview](basics.md)
- @ref[Debugging](debugging.md)
- @ref[Advanced Features](advanced-features.md)
- @ref[distage-framework](distage-framework.md)
- @ref[distage-framework-docker](distage-framework-docker.md)
- @ref[distage-testkit](distage-testkit.md)
- @ref[Syntax Reference](reference.md)

Further reading
---------------

Example projects:

* [DIStage Example Project](https://github.com/7mind/distage-example)
* [Idealingua Example Project with TypeScript and Scala](https://github.com/7mind/idealingua-example)

Support Chats:

* [Izumi on Gitter](https://gitter.im/7mind/izumi)
* [Izumi User Group [RU] on Telegram](https://t.me/izumi_ru)
* [Izumi User Group [EN] on Telegram](https://t.me/izumi_en)

Slides:

* [Hyper-pragmatic Pure FP Testing with distage-testkit](https://www.slideshare.net/7mind/hyperpragmatic-pure-fp-testing-with-distagetestkit)
* [distage: Staged Dependency Injection](https://www.slideshare.net/7mind/scalaua-distage-staged-dependency-injection)
* [logstage: Zero-cost Structured Logging](https://www.slideshare.net/7mind/logstage-zerocosttructuredlogging)
* [More slides](https://github.com/7mind/slides)

Videos:

* [Hyper-pragmatic Pure FP Testing with distage-testkit, Functional Scala, London](https://www.youtube.com/watch?v=CzpvjkUukAs)
* [Livecoding: DIStage & Bifunctor Tagless Final on Youtube](https://www.youtube.com/watch?v=C0srg5T0E4o&t=4971)

@@@ index

* [Overview](basics.md)
* [Debugging](debugging.md)
* [Advanced Features](advanced-features.md)
* [distage-framework](distage-framework.md)
* [distage-framework-docker](distage-framework-docker.md)
* [distage-testkit](distage-testkit.md)
* [Syntax Reference](reference.md)

@@@
