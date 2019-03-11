import com.gu.mediaservice.lib.auth.{Authentication, GrantAllPermissionsHandler, GuardianEditorialPermissionsHandler}
import com.gu.mediaservice.lib.aws.{Kinesis, MessageSender, MessageSenderVersion, SNS}
import com.gu.mediaservice.lib.config.Services
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

  val publishers: Seq[MessageSenderVersion] = Seq(
    config.topicArn.map(topicArn => new SNS(config, topicArn)),
    config.thrallKinesisStream.map(kinesisStreamName => new Kinesis(config, kinesisStreamName))
  ).flatten

  val messageSender = new MessageSender(publishers)

  val metrics = config.cloudWatchNamespace.map{ ns =>
    new CloudWatchMediaApiMetrics(ns, config.withAWSCredentials)
  }.getOrElse{
    Logger.info("CloudWatch metrics are not configured.")
    new NullMediaApiMetrics
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
    es6Config.map { c =>
      Logger.info("Configuring ES6: " + c)
      val es6 = new lib.elasticsearch.impls.elasticsearch6.ElasticSearch(config, metrics, c)
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

  val auth = new Authentication(config, services, actorSystem, defaultBodyParser, wsClient, controllerComponents, executionContext)


  val mediaApi = new MediaApi(auth, messageSender, elasticSearch, imageResponse, config, controllerComponents, imageBucket,
    metrics, services, permissionsHandler)
  val suggestionController = new SuggestionController(auth, elasticSearch, controllerComponents)
  val aggController = new AggregationController(auth, elasticSearch, controllerComponents)
  val usageController = new UsageController(auth, config, elasticSearch, enabledUsageQuota, controllerComponents)
  val healthcheckController = new ManagementWithPermissions(controllerComponents, permissionsHandler)

  override val router = new Routes(httpErrorHandler, mediaApi, suggestionController, aggController, usageController, healthcheckController)
}
