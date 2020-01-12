---
out: index.html
---

distage: Staged Dependency Injection
====================================

`distage` is a pragmatic module system for functional Scala, it aims to combine simplicity and expressivity of pure FP
with the flexibility and extreme late-binding that's traditionally associated with object-oriented dependency injection frameworks, such as Guice.

`distage` is suitable for structuring applications in @ref[Tagless Final Style](basics.md#tagless-final-style),
with @ref[ZIO Environment](basics.md#auto-traits), or in imperative Scala style.

Why use distage?
-------------------

1. **Faster applications and tests**:
    `distage` guarantees that no unnecessary instantiations will happen during your tests or application startup.
2. **Quick failure detection**:
    `distage` performs all the integration checks for your application and tests even before any instantiations happened.    
3. **Simple tests**:
    `distage` eliminates all the hard work of setting up your test environments, especially configurable ones.
4. **Better integration tests**:
    `distage` provides you great memoization support for your tests so you may reuse expensive resources (like database connections) across multiple
    integration tests, gaining performance and without sacrificing correctness.
5. **Simple development workflow**:
    @ref[distage-framework](distage-framework.md) module allows you to develop Role-Based Applications -- a fusion of Microservices and Monoliths.
     You may run all your services in one process for development purposes (and even switch to mock implementations with a single commandline argument).
6. **Fast compile times and low mental overhead**:
    Unlike fully compile-time DIs, `distage` does not impose a compile time penalty.
    `distage` extensions are simple to write and do not require type-level programming.
7. **Simple debugging**:
    `distage` provides you important insights about your application and allows you to introspect and modify it on the fly, 
    before any instantiations happen.
8. **High Correctness**:
    `distage` supports resources and lifecycle natively and guarantees proper cleanups even when something went wrong.
9. **No reflection**:
    `distage` generates all constructors and [type info](https://blog.7mind.io/lightweight-reflection.html) at compile-time and does not use Scala reflection.
    As such, it's compatible with Graal Native Image and Scala.js.
10. **Non-invasive**:
    `distage` is designed to not impact the way your Scala code is written. 
    It just removes all the initialization boilerplate.
    You don't need to learn magic tricks to write components in a distage application.

`distage` is recommended by industry leaders:

> Given its native support for type classes and higher-kinded types -- both features indispensable to functional programming -- distage is one of the leading dependency injection libraries out there. Bonus points for being built by a wicked-smart team that contributes to ZIO! 
> 
> -– *John A. De Goes*

FAQ
---

**Q**: How to pronounce `distage`?

**A**: 'Dee-stage'

**Q**: Isn't it unsafe to use runtime dependency injection?

**A**: `distage` is split into two stages, first a wiring `plan` is calculated, only afterwards it is executed. This `Plan`
value can be @ref[tested](debugging.md#testing-plans) for errors in an ordinary test suite – if it is well-formed, the wiring
will succeed at runtime. This test can also be performed at compile-time – `distage-framework` module contains
@ref[macros](distage-framework.md#compile-time-checks) for aborting compilation in case of erroneous wiring.

Further reading
---------------

- [Example Project](https://github.com/7mind/distage-livecode)
- [ScalaUA Presentation](https://www.slideshare.net/7mind/scalaua-distage-staged-dependency-injection)
- @ref[Basics](basics.md)
- @ref[Advanced features](advanced-features.md)
- @ref[Debugging](debugging.md)
- @ref[distage-framework](distage-framework.md)
- @ref[distage-framework-docker](distage-framework-docker.md)
- @ref[distage-testkit](distage-testkit.md)
- @ref[Syntax summary](reference.md)

@@@ index

* [Basics](basics.md)
* [Advanced features](advanced-features.md)
* [Debugging](debugging.md)
* [distage-framework](distage-framework.md)
* [distage-framework-docker](distage-framework-docker.md)
* [distage-testkit](distage-testkit.md)
* [Syntax summary](reference.md)

@@@
