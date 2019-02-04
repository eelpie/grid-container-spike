package lib.elasticsearch.impls.elasticsearch1

import java.nio.file.{Path, Paths}

import com.gu.mediaservice.lib.auth.Authentication.Principal
import com.gu.mediaservice.lib.auth.{Internal, ReadOnly, Syndication}
import com.gu.mediaservice.lib.elasticsearch.ElasticSearchConfig
import com.gu.mediaservice.model._
import com.gu.mediaservice.model.usage.PublishedUsageStatus
import com.gu.mediaservice.syntax._
import com.whisk.docker.{DockerContainer, DockerReadyChecker, VolumeMapping}
import lib.elasticsearch.{AggregateSearchParams, ElasticSearchTestBase, SearchParams}
import lib.{MediaApiConfig, MediaApiMetrics}
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.index.query.QueryBuilders
import org.joda.time.DateTime
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import play.api.Configuration
import play.api.libs.json.Json
import play.api.mvc.AnyContent
import play.api.mvc.Security.AuthenticatedRequest

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class MediaApiElasticSearch1Test extends ElasticSearchTestBase with Eventually with MockitoSugar {

  implicit val request  = mock[AuthenticatedRequest[AnyContent, Principal]]

  private val oneHundredMilliseconds = Duration(100, MILLISECONDS)
  private val fiveSeconds = Duration(5, SECONDS)

  private val imageAlias = "readAlias"

  private val mediaApiConfig = new MediaApiConfig(Configuration.from(Map(
    "persistence.identifier" -> "picdarUrn",
  )))

  private val mediaApiMetrics = new MediaApiMetrics(mediaApiConfig)
  val elasticConfig = ElasticSearchConfig(writeAlias = imageAlias, host = "localhost", port = 9301, cluster = "media-service-test")

  val ES = new ElasticSearch(mediaApiConfig, mediaApiMetrics, elasticConfig)

  def findElasticsearchConfig(): Path = {
    // SBT does some monkey stuff and runs the test from the "thrall" folder compared to IntelliJ that runs it from the root
    val sbtsIdeaOfCwd = Paths.get("").toAbsolutePath

    val cwd = if (sbtsIdeaOfCwd.endsWith("media-api")) {
      sbtsIdeaOfCwd.getParent
    } else {
      sbtsIdeaOfCwd
    }

    cwd.resolve("elasticsearch/test/elasticsearch.yml")
  }

  def esContainer = Some(DockerContainer("elasticsearch:1.7.1")
    .withPorts(9200 -> Some(9201), 9300 -> Some(9301))
    .withVolumes(Seq(VolumeMapping(findElasticsearchConfig().toString, "/usr/share/elasticsearch/config/elasticsearch.yml")))
    .withReadyChecker(
      DockerReadyChecker.HttpResponseCode(9200, "/", Some("0.0.0.0")).within(10.minutes).looped(40, 1250.millis)
    )
  )

  override def beforeAll {
    super.beforeAll()

    ES.ensureAliasAssigned()
    Await.ready(saveImages(images), 1.minute)

    // allow the cluster to distribute documents... eventual consistency!
    Thread.sleep(5000)
  }

  describe("get by id") {
    it("can load a single image by id") {
      val expectedImage = images.head
      whenReady(ES.getImageById(expectedImage.id)) { r =>
        r.get.id shouldEqual expectedImage.id
      }
    }
  }

  describe("usages for supplier") {
    it("can count published agency images within the last number of days") {
      val publishedAgencyImages = images.filter(i => i.usageRights.isInstanceOf[Agency] && i.usages.exists(_.status == PublishedUsageStatus))
      publishedAgencyImages.size shouldBe 2

      // Reporting date range is implemented as round down to last full day
      val withinReportedDateRange = publishedAgencyImages.filter(i => i.usages.
        exists(u => u.dateAdded.exists(_.isBefore(DateTime.now.withTimeAtStartOfDay()))))
      withinReportedDateRange.size shouldBe 1

      val results = Await.result(ES.usageForSupplier("ACME", 5), fiveSeconds)

      results.count shouldBe 1
    }
  }

  describe("aggregations") {
    it("can load date aggregations") {
      val aggregateSearchParams = AggregateSearchParams(field = "uploadTime", q = None, structuredQuery = List.empty)

      val results = Await.result(ES.dateHistogramAggregate(aggregateSearchParams), fiveSeconds)

      results.total shouldBe 1
      results.results.head.count shouldBe images.size
    }

    it("can load metadata aggregations") {
      val aggregateSearchParams = AggregateSearchParams(field = "keywords", q = None, structuredQuery = List.empty)

      val results = Await.result(ES.metadataSearch(aggregateSearchParams), fiveSeconds)

      results.total shouldBe 2
      results.results.find(b => b.key == "es").get.count shouldBe images.size
      results.results.find(b => b.key == "test").get.count shouldBe images.size
    }
  }

  describe("Tiered API access") {
    it("ES should return only rights acquired pictures with an allow syndication lease for a syndication tier search") {
      val searchParams = SearchParams(tier = Syndication)
      val searchResult = ES.search(searchParams)
      whenReady(searchResult, timeout, interval) { result =>
        result.total shouldBe 3

        val imageIds = result.hits.map(_._1)
        imageIds.size shouldBe 3
        imageIds.contains("test-image-1") shouldBe true
        imageIds.contains("test-image-2") shouldBe true
        imageIds.contains("test-image-4") shouldBe true
      }
    }

    it("ES should return all pictures for internal tier search") {
      val searchParams = SearchParams(tier = Internal)
      val searchResult = ES.search(searchParams)
      whenReady(searchResult, timeout, interval) { result =>
        result.total shouldBe images.size
      }
    }

    it("ES should return all pictures for readonly tier search") {
      val searchParams = SearchParams(tier = ReadOnly)
      val searchResult = ES.search(searchParams)
      whenReady(searchResult, timeout, interval) { result =>
        result.total shouldBe images.size
      }
    }
  }

  describe("syndicationStatus query on the Syndication tier") {
    it("should return 0 results if a Syndication tier queries for SentForSyndication images") {
      val search = SearchParams(tier = Syndication, syndicationStatus = Some(SentForSyndication))
      val searchResult = ES.search(search)
      whenReady(searchResult, timeout, interval) { result =>
        result.total shouldBe 0
      }
    }

    it("should return 3 results if a Syndication tier queries for QueuedForSyndication images") {
      val search = SearchParams(tier = Syndication, syndicationStatus = Some(QueuedForSyndication))
      val searchResult = ES.search(search)
      whenReady(searchResult, timeout, interval) { result =>
        result.total shouldBe 3

        val imageIds = result.hits.map(_._1)
        imageIds.size shouldBe 3
        imageIds.contains("test-image-1") shouldBe true
        imageIds.contains("test-image-2") shouldBe true
        imageIds.contains("test-image-4") shouldBe true
      }
    }

    it("should return 0 results if a Syndication tier queries for BlockedForSyndication images") {
      val search = SearchParams(tier = Syndication, syndicationStatus = Some(BlockedForSyndication))
      val searchResult = ES.search(search)
      whenReady(searchResult, timeout, interval) { result =>
        result.total shouldBe 0
      }
    }

    it("should return 0 results if a Syndication tier queries for AwaitingReviewForSyndication images") {
      val search = SearchParams(tier = Syndication, syndicationStatus = Some(AwaitingReviewForSyndication))
      val searchResult = ES.search(search)
      whenReady(searchResult, timeout, interval) { result =>
        result.total shouldBe 0
      }
    }
  }

  describe("syndicationStatus query on the internal tier") {
    it("should return 1 image if an Internal tier queries for SentForSyndication images") {
      val search = SearchParams(tier = Internal, syndicationStatus = Some(SentForSyndication))
      val searchResult = ES.search(search)
      whenReady(searchResult, timeout, interval) { result =>
        result.total shouldBe 1
      }
    }

    it("should return 3 images if an Internal tier queries for QueuedForSyndication images") {
      val search = SearchParams(tier = Internal, syndicationStatus = Some(QueuedForSyndication))
      val searchResult = ES.search(search)
      whenReady(searchResult, timeout, interval) { result =>
        result.total shouldBe 3
      }
    }

    it("should return 3 images if an Internal tier queries for BlockedForSyndication images") {
      val search = SearchParams(tier = Internal, syndicationStatus = Some(BlockedForSyndication))
      val searchResult = ES.search(search)
      whenReady(searchResult, timeout, interval) { result =>
        result.hits.forall(h => h._2.leases.leases.nonEmpty) shouldBe true
        result.hits.forall(h => h._2.leases.leases.forall(l => l.access == DenySyndicationLease)) shouldBe true
        result.total shouldBe 3
      }
    }

    it("should return 2 images if an Internal tier queries for AwaitingReviewForSyndication images") {
      val search = SearchParams(tier = Internal, syndicationStatus = Some(AwaitingReviewForSyndication))
      val searchResult = ES.search(search)
      whenReady(searchResult, timeout, interval) { result =>
        result.total shouldBe 2
      }
    }
  }

  private def saveImages(images: Seq[Image]) = {
    Future.sequence(
      images.map(image => {
        ES.client.prepareIndex(imageAlias, "image")
          .setId(image.id)
          .setSource(Json.toJson(image).toString())
          .executeAndLog(s"Saving test image with id ${image.id}")
      })
    )
  }

  private def totalImages: Long = Await.result(ES.totalImages(), oneHundredMilliseconds)

  private def purgeTestImages = {
    def deleteImages() = {
      val sixtySeconds = new TimeValue(60000)
      def scroll = ES.client.prepareSearch(imageAlias)
        .setScroll(sixtySeconds)
        .setQuery(QueryBuilders.matchAllQuery())
        .setSize(20).execute().actionGet()

      var scrollResp = scroll
      while (scrollResp.getHits.getHits.length > 0) {
        scrollResp.getHits.getHits.map(h => ES.client.delete(new DeleteRequest(imageAlias, "image", h.getId)))
        scrollResp =  ES.client.prepareSearchScroll(scrollResp.getScrollId()).setScroll(sixtySeconds).execute().actionGet()
      }
    }

    deleteImages()
    eventually(timeout(fiveSeconds), interval(oneHundredMilliseconds))(totalImages shouldBe 0)
  }

}
