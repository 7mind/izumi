package izumi.logstage.api.rendering

import izumi.logstage.api.Log

class TransformerChainRenderingPolicy(
  transformers: List[LogEntryTransformer],
  underlying: RenderingPolicy,
) extends RenderingPolicy {

  override def render(entry: Log.Entry): String = {
    underlying.render {
      transformers.foldLeft(entry)((entry, transformer) => transformer.apply(entry))
    }
  }
}
