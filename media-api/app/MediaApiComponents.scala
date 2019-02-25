import com.gu.mediaservice.lib.auth.{GrantAllPermissionsHandler, GuardianEditorialPermissionsHandler}
import com.gu.mediaservice.lib.aws.MessageSender
import com.gu.mediaservice.lib.config.Services
import com.gu.mediaservice.lib.elasticsearch.ElasticSearchConfig
import com.gu.mediaservice.lib.elasticsearch6.ElasticSearch6Config
import com.gu.mediaservice.lib.imaging.ImageOperations
import com.gu.mediaservice.lib.management.ManagementWithPermissions
import com.gu.mediaservice.lib.play.GridComponents
import controllers._
import lib._
import lib.elasticsearch.ElasticSearchVersion
import lib.imagebuckets.{CloudFrontImageBucket, S3ImageBucket}
import play.api.ApplicationLoader.Context
import play.api.Logger
import router.Routes

class MediaApiComponents(context: Context) extends GridComponents(context) {
  final override lazy val config = new MediaApiConfig(configuration)
  lazy val services = new Services(config)

  val imageOperations = new ImageOperations(context.environment.rootPath.getAbsolutePath)

  val messageSender = new MessageSender(config, config.topicArn)
  val mediaApiMetrics = new MediaApiMetrics(config)

  val es1Config: Option[ElasticSearchConfig] = for {
    h <- config.elasticsearchHost
    p <- config.elasticsearchPort
    c <- config.elasticsearchCluster
  } yield {
    ElasticSearchConfig(alias = config.imagesAlias,
      host = h,
      port = p,
      cluster = c
    )
  }

  val es6Config: Option[ElasticSearch6Config] = for {
    h <- config.elasticsearch6Host
    p <- config.elasticsearch6Port
    c <- config.elasticsearch6Cluster
    s <- config.elasticsearch6Shards
    r <- config.elasticsearch6Replicas
  } yield {
    ElasticSearch6Config(
      alias = config.imagesAlias,
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
      val es1 = new lib.elasticsearch.impls.elasticsearch1.ElasticSearch(config, mediaApiMetrics, c)
      es1.ensureAliasAssigned()
      es1
    },
    es6Config.map { c =>
      Logger.info("Configuring ES6: " + c)
      val es6 = new lib.elasticsearch.impls.elasticsearch6.ElasticSearch(config, mediaApiMetrics, c)
      es6.ensureAliasAssigned()
      es6
    }
  ).flatten

  val elasticSearch: ElasticSearchVersion = new lib.elasticsearch.TogglingElasticSearch(elasticSearches.head, elasticSearches.last)
  elasticSearch.ensureAliasAssigned()

  val imageBucket = new S3ImageBucket(config, config.imageBucket)
  val thumbnailBucket = config.cloudFrontConfiguration.map { c =>
    new CloudFrontImageBucket(config, config.thumbBucket, c.cloudFrontPrivateKeyLocation, c.cloudFrontKeyPairId)
  }.getOrElse{
    new S3ImageBucket(config, config.thumbBucket)
  }

  val enabledUsageQuota = for {
    storeBucket <- config.quotaStoreBucket
    storeFile <- config.quotaStoreFile
    usageStoreBucket <- config.usageStoreBucket
    quotaUpdateEnabled <- config.quotaUpdateEnabled
  } yield {
    val quotaStore = new QuotaStore(storeFile, storeBucket, config, quotaUpdateEnabled)
    val usageStore = new UsageStore(usageStoreBucket, config, quotaStore)
    val usageQuota = new UsageQuota(quotaStore, usageStore, config, elasticSearch, actorSystem.scheduler)
    usageQuota.scheduleUpdates()
    usageQuota
  }

  val imageResponse = new ImageResponse(config, imageBucket, thumbnailBucket, enabledUsageQuota, services)

  val permissionsHandler = (for {
    permissionsBucket <- config.permissionsBucket
    permissionsStage <- config.permissionsStage
  } yield {
    new GuardianEditorialPermissionsHandler(permissionsBucket, permissionsStage, config)
  }).getOrElse{
    Logger.warn("No permissions handler is configured; granting all permissions to all users.")
    new GrantAllPermissionsHandler()
  }

  val mediaApi = new MediaApi(auth, messageSender, elasticSearch, imageResponse, config, controllerComponents, imageBucket,
    mediaApiMetrics, services, permissionsHandler)
  val suggestionController = new SuggestionController(auth, elasticSearch, controllerComponents)
  val aggController = new AggregationController(auth, elasticSearch, controllerComponents)
  val usageController = new UsageController(auth, config, elasticSearch, enabledUsageQuota, controllerComponents)
  val healthcheckController = new ManagementWithPermissions(controllerComponents, permissionsHandler)

  override val router = new Routes(httpErrorHandler, mediaApi, suggestionController, aggController, usageController, healthcheckController)
}
