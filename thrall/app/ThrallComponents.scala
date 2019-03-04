import com.gu.mediaservice.lib.elasticsearch.ElasticSearchConfig
import com.gu.mediaservice.lib.elasticsearch6.ElasticSearch6Config
import com.gu.mediaservice.lib.logging.GridLogger
import com.gu.mediaservice.lib.play.GridComponents
import controllers.{HealthCheck, ThrallController}
import lib._
import play.api.ApplicationLoader.Context
import play.api.Logger
import router.Routes
import com.gu.mediaservice.lib.config.Services

class ThrallComponents(context: Context) extends GridComponents(context) {

  final override lazy val config = new ThrallConfig(configuration)
  final override lazy val services = new Services(config)

  val store = new ThrallStore(config)
  val dynamoNotifications = new DynamoNotifications(config)
  val thrallMetrics = new CloudWatchThrallMetrics(config)

  val es1Config = for {
    h <- config.elasticsearchHost
    p <- config.elasticsearchPort
    c <- config.elasticsearchCluster
  } yield {
    ElasticSearchConfig(
      alias = config.writeAlias,
      host = h,
      port = p,
      cluster = c
    )
  }

  val es6Config =
    for {
      h <- config.elasticsearch6Host
      p <- config.elasticsearch6Port
      c <- config.elasticsearch6Cluster
      s <- config.elasticsearch6Shards
      r <- config.elasticsearch6Replicas
    } yield {
      ElasticSearch6Config(
        alias = config.writeAlias,
        host = h,
        port = p,
        cluster = c,
        shards = s,
        replicas = r
      )
  }

  val elasticSearches = Seq(
    es1Config.map { c =>
      Logger.info("Configuring ES1: " + c)
      val es1 = new ElasticSearch(c, thrallMetrics)
      es1.ensureAliasAssigned()
      es1
    },
    es6Config.map { c =>
      Logger.info("Configuring ES6: " + c)
      val es6 = new ElasticSearch6(c, thrallMetrics)
      es6.ensureAliasAssigned()
      es6
    }
  ).flatten

  GridLogger.info("Creating elasticsearch router with elastics: " + elasticSearches)
  val es = new ElasticSearchRouter(elasticSearches)

  val syndicationOps = new SyndicationRightsOps(es)

  val thrallMessageConsumer = new ThrallMessageConsumer(config, elasticSearches.head, store,
    dynamoNotifications, syndicationOps, thrallMetrics)
  thrallMessageConsumer.startSchedule()
  context.lifecycle.addStopHook {
    () => thrallMessageConsumer.actorSystem.terminate()
  }

  elasticSearches.lift(1).map { es =>
    val thrallKinesisMessageConsumer = new kinesis.ThrallMessageConsumer(config, es, thrallMetrics,
      store, dynamoNotifications, syndicationOps, config.from)
    thrallKinesisMessageConsumer.start()
  }

  val messageConsumerForHealthCheck = thrallMessageConsumer

  val thrallController = new ThrallController(controllerComponents)
  val healthCheckController = new HealthCheck(elasticSearches.head, messageConsumerForHealthCheck, config, controllerComponents)

  override lazy val router = new Routes(httpErrorHandler, thrallController, healthCheckController, management)
}
