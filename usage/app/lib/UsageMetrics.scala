package lib

trait UsageMetrics {
  def incrementUpdated: Unit
  def incrementErrors: Unit
}
