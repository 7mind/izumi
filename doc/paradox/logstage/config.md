# Logstage Config

Meta definition of sinks and logentries hash

## Sample logstage config

```
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
------------| ----------- |
`sinks` |Defines list of log sinks for theirs further usage*. This section should contain `default` log sinks in case of ignore sinks list if you need to use only default sinks** |
`root` |  Defines default settings for logging (i.e., sinks and log threshold) | 
`entries` |Defines log entries settings (threshold and list of sinks |

### Please Note!

`*` For each sink you need to provide rendering policy mapper. 
    You need to specify case class constructor for defined logsink and implement function for it's instantiating.

`**` In `sinks` section you can define log sink with label `default` for it's further usage on logentries definition for simplify log entries definition. For example, if you need to define only threshold level but sink can be `default`, you only write next:
```
entries = {
    "target.path" = <your_level>
}
```
instead of common syntax:

```
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
    - for each RenderingPolicy you must define `RenderingPolicyMapperModule()`. You need to implement function to instantiate RenderingPolicy instance from constructor case class
    - for each LogSink you must define `LogSinkMapperModule()`. You need to implement function to instantiate LogSink instance from constructor case class

3) defined application modules: 
    
    - `LoggerConfigModule()` - it makes binding of LoggerConfig and you may inject it from locator using next commnad:
    
    ```
    val logstageConfig = locator.get[LoggerConfig]
    ```
    
    LoggerConfig model is next:
    
    ```
    final case class LoggerPathConfig(threshold: Log.Level, sinks: Seq[LogSink])
    
    final case class LoggerConfig(root : LoggerPathConfig, entries : Map[String, LoggerPathConfig])
    
    ```
