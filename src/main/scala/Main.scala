import com.amazonaws.services.lambda.runtime.events.ScheduledEvent
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.danielasfregola.twitter4s.TwitterRestClient
import com.danielasfregola.twitter4s.entities.Tweet
import com.danielasfregola.twitter4s.entities.enums.ResultType
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

case class Workflow(
                     searches: List[String],
                     result_type: String,
                     actions: List[String],
                     comments: List[String],
                     max_age: Option[Int] = None)

case class BastionConfig(
                          user: String,
                          workflows: List[Workflow]
                        )

object Main extends Main {
  def main(args: Array[String]): Unit = {
    handleRequest(null, null)
  }
}

class Main extends RequestHandler[ScheduledEvent, Unit] with LazyLogging {

  val config: BastionConfig = ConfigFactory.load().as[BastionConfig]("bastion")

  val restClient = TwitterRestClient()
  val tweetSearch = new TweetSearch(restClient)
  val tweetProcessor = new TweetProcessor(restClient)
  val timeoutMinutes = 1

  def handleRequest(event: ScheduledEvent, context: Context) = {

    try {
      logger.info("Starting")
      val userTweetsFuture = tweetSearch.userTweets(config.user)
      config.workflows.foreach(processWorkflow(_, userTweetsFuture))
      logger.info("Shutting down")
      restClient.shutdown()
      logger.info("Exiting")
    }
    catch {
      case _: Throwable => // Avoid retries
    }
  }

  def processWorkflow(workflow: Workflow, userTweetsFuture: Future[Seq[Tweet]]) = {
    workflow.searches.foreach(processSearch(_, workflow, userTweetsFuture))
    logger.info("Process workflow completed")
  }

  def processSearch(query: String, workflow: Workflow, userTweetsFuture: Future[Seq[Tweet]]) {

    val resultType = workflow.result_type match {
      case "popular" => ResultType.Popular
      case "mixed" => ResultType.Mixed
      case "recent" => ResultType.Recent
      case _ => ResultType.Mixed
    }

    val searchFuture = tweetSearch.search(query, resultType, workflow.max_age)

    val searchTweets = getTweets(Seq(searchFuture))
    val userTweets = getTweets(Seq(userTweetsFuture))

    val retweeted = userTweets.map(_.retweeted_status).flatten.map(_.id).toSet
    val validTweets = searchTweets.filter(t => !retweeted.contains(t.id))

    logger.info(s"Total: ${searchTweets.length}, valid: ${validTweets.length} tweets for search: ${query}")

    val newTweets = getTweets(tweetProcessor.process(validTweets, config.user, workflow))

    newTweets.foreach(tweet => {
      logger.info(s"Tweet processed: ${tweet.text}")
    })
    logger.info("Process search completed")
  }

  def getTweets(futures: Seq[Future[Seq[Tweet]]]): Seq[Tweet] = {
    futures.flatMap(Await.result(_, timeoutMinutes minutes))
  }
}

