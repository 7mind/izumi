"""Scala public test-interface scaffolding shared by consumer controls."""


def framework_source(*, prefix: str, framework: str, fingerprint: str, superclass: str, label: str,
                     members: str, arguments: str, remote: str, definitions: str, execute: str, done: str,
                     tags: str, empty_tasks: str) -> str:
    return prefix + f'''final class {framework} extends Framework {{
{members}  private val {fingerprint} = new SubclassFingerprint {{
    override def isModule(): Boolean = false
    override def superclassName(): String = {superclass}
    override def requireNoArgConstructor(): Boolean = true
  }}
  override def name(): String = {label}
  override def fingerprints(): Array[Fingerprint] = Array({fingerprint})
  override def runner(arguments: Array[String], {remote.split('.')[0]}: Array[String], loader: ClassLoader): Runner = new Runner {{
    override def args(): Array[String] = {arguments}
    override def remoteArgs(): Array[String] = {remote}
    override def done(): String = {done}
    override def tasks(definitions: Array[TaskDef]): Array[Task] = {definitions}.map {{ definition => new Task {{
      override def taskDef(): TaskDef = definition
      override def tags(): Array[String] = {tags}
      override def execute(handler: EventHandler, loggers: Array[Logger]): Array[Task] = {{
{execute}        {empty_tasks}
      }}
    }} }}
  }}
}}
'''
