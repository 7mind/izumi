# Rendering policy

Rendering policy is defined in @ref[logstage](config.md) config section. 

Default template: `${level}:${ts} ${thread}${location} ${custom-ctx} ${msg}`

## Log units specification

Unit        | Aliases                | Explanation                                  | Syntax  |
------------| ---------------------- | -------------------------------------------- | -------  
`timestamp` | `ts`                   | Timestamp                                    | `${timestamp}` |
`thread`    | `t`                    | Thread data (contains thread `name` and `id`) | `${thread}` |
`level`     | `lvl`                  | Logging level |  `${lvl}` |
`location`  | `loc`                  | Log message location (hyperlink to filename it's line number) | `${location}` | 
`message`   | `msg`                  | Application-supplied message associated with the logging event | `${message}` |
`custom-ctx`| `context`, `ctx`       | User's context (more info @ref[here](custom_ctx.md))| `${custom-ctx}` |
`exception` | `ex`                   | Outputs the stack trace of the exception associated with the logging event, if any. Full stack trace will be printed by default. | `${ex}` |

### Parameters

- Units can be parameterized (with padding, margins, etc). 
- NOTE! Currently there are only common parameters (padding). Parameters enumerates in `[` `]` braces. For example, `${message[15]}`

Unit        | Parameter | Example                                                           |                    |
------------| --------- | ----------------------------------------------------------------- | ------------------ |
**ALL**     | `padding` | padding for log unit in result string. Can be ellipsed: `[:..14]` | `${timestamp}[14]` |
