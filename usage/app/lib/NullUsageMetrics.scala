package lib

class NullUsageMetrics extends UsageMetrics {
  override def incrementUpdated: Unit = Unit
  override def incrementErrors: Unit = Unit
}
