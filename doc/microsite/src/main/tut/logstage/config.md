# Logstage Config

@@@ warning { title='TODO' }
Sorry, this page is not ready yet
@@@

## Sample

```hocon
logstage {
 
  sinks = {
    "default" = {
      path = "foo.bar.baz.CustomSink1"
      params {
        policy {
          path = "foo.bar.baz.CustomPolicy1"
          params {
            options {
              withExceptions = false
              withColors = true
            }
          }
        }
      }
    }
    "sink1" = {
      path = "foo.bar.baz.CustomSink1"
      params {
        policy {
          path = "foo.bar.baz.CustomPolicy1"
          params {
            options {
              withExceptions = false
              withColors = true
            }
          }
        }
      }
    }
  }


  root {
    threshold = "info"
    sinks = [
      "default"
    ]
  }

  entries = {
    "for.bar.*" = "warn"
    "for.baz.*" = {
      threshold = "debug"
      sinks = [
        "sink1"
      ]
    }
  }
}
```

## Detailed info


Section      |   Explanation |
------------ | ------------- |
`sinks`      | Defines used log sinks*. This section should contain `default` log sinks in case of ignore sinks list if you need to use only default sinks** |
`root`       | Defines default settings for logging (e.g., sinks and log threshold) | 
`entries`    | Defines log entries settings (threshold and list of sinks) |

Notes:

- For each sink you need to provide rendering policy mapper. 
  You need to specify case class constructor for defined logsink and implement function for it's instantiating.

- In `sinks` section you can define log sink with label `default` for it's further usage on logentries definition for simplify log entries definition. For example, if you need to define only threshold level but sink can be `default`, you only write next:

```hocon
entries = {
    "target.path" = <your_level>
}
```

instead of common syntax:

```hocon
entries = {
    "target.path" = {
       threshold = <your_level>
       sinks = ["default"]
    }
}
```

## Basic setup

There are two libs for logstage config. The second one depends on distage and config LogstageModule which binds logstage config

So, what you need to do to use logstage declarative config:

1) include `"logstage-reference.conf"` in your application config

2) defined next bootstrap modules :
    
    - `LogstageCodecsModule()` - it will bind rendering policy and logsinks mappers and all necessary logstage runtime codecs
    
    Each `RenderingPolicy` and `LogSink` you may bind by calling follow commands inside LogstageCodecsModule:
        
        bindLogSinkMapper[T <: LogSink : ru.TypeTag, C: ru.TypeTag](f: C => T)
        
        bindRenderingPolicyMapper[T <: RenderingPolicy : ru.TypeTag, C: ru.TypeTag](f: C => T)
    

3) defined application modules: 
    
    - `LoggerConfigModule()` - it makes binding of LoggerConfig and you may inject it from locator using next command:
    
    ```scala
    val logstageConfig = locator.get[LoggerConfig]
    ```
    
    LoggerConfig model is next:
    
    ```scala
    final case class LoggerPathConfig(threshold: Log.Level, sinks: Seq[LogSink])
    
    final case class LoggerConfig(root : LoggerPathConfig, entries : Map[String, LoggerPathConfig])
    ```

## Example 

1. Let's define our sinks and policies

    1.1 DummyLogSink
    
    ```scala
    class DummyLogSink(policy: RenderingPolicy) extends LogSink {
       override def flush(e: Log.Entry): Unit = {
         // do smth
       }
    }
    ```
    
    1.2 DummyRenderingPolicy
    
    ```scala
    class DummyRenderingPolicy(foo: Int, bar: Option[String]) extends RenderingPolicy {
      override def render(entry: Log.Entry): String = entry.toString
    }
    ```

2. Let's define out logstage reference config 

```hocon
logstage {

   // include izumi sdk settings
   include "logstage-reference.conf"

   // custom overrides 
   
   sinks = [
      "default" = {
        path = "path.to.DummyLogSink"
        params {
          policy {
            path = "path.to.DummyRenderingPolicy"
            params {
              foo = 1024
              bar = "your_string_parameter"
            }
          }
        }
      }
   ]
   
    root {
       threshold = "info"
       sinks = [
         "default"
       ]
     }
   
     entries = {
        "path.for.debug.only.*" = debug
     }
}

```

3. So, for now we need to specify case classes constructors for out DummyLogSink and DummyRenderingPolicy for automatic parsing

    3.1 DummyRenderingPolicyConstructor
    
    ```scala
    case class DummyRenderingPolicyConstructor(foo : Int, bar: Option[String])
    ```
    
    3.2 DummyLogSinkConstructor
        
    ```scala
    case class DummyLogSinkConstructor(policy : RenderingPolicy)
    ```

4. now we need to configure out DI for loggerConfigUsage

    4.1 Define bootsrap module. Here you can bind all mappers for custom Policies and Logsinks:
    
    ```scala
    val logstageBootstrapModule = new LogstageCodecsModule {
        
        // bind our rendering policy
        
        bindRenderingPolicyMapper[DummyRenderingPolicy, DummyRenderingPolicyConstructor]{
          c => 
            new DummyRenderingPolicy(c.foo, c.bar)
        }
        
        // bind our logsink
        
        bindLogSinkMapper[DummyLogSink, DummyLogSinkConstructor] {
          c =>  new DummyLogSink(c.policy)
        }
    }
    
    val bootstrapModules = Seq(..., logstageBootstrapModule)

    ```
    
    4.2 Add `LoggerConfigModule` to app modules
    
    ```scala
    val modules = Seq(..., new LoggerConfigModule ())
    ```
    
    4.2 Enjoy!
    
    ```scala
    // Basic locator setup
    
    val injector = Injector(bootstapModules: _*)
    val plan = injector.plan(modules.toList.overrideLeft)
    val locator = injector.produce(plan)
    
    // retrieve binded config
    val cfg = locator.get[LoggerConfig]
    ```
    
