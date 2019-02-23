import com.amazonaws.services.lambda.runtime.events.ScheduledEvent
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.danielasfregola.twitter4s.TwitterRestClient
import com.danielasfregola.twitter4s.entities.enums.ResultType
import com.danielasfregola.twitter4s.entities.{Tweet, User}
import com.dkomlen.bastion.{Action, ActionProcessor, SearchProcessor, UserStatus}
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
                     max_age: Option[Int] = None,
                     actions: List[Action],
                   )

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

  val timeoutMinutes = 1

  val config: BastionConfig = ConfigFactory.load().as[BastionConfig]("bastion")
  val restClient = TwitterRestClient()

  val searchProcessor = new SearchProcessor(restClient)
  val userStatus = getStatus(config.user)
  val actionProcessor = new ActionProcessor(restClient, userStatus)

  def handleRequest(event: ScheduledEvent, context: Context) = {

    try {
      logger.info("Starting")
      config.workflows.foreach(processWorkflow)
      logger.info("Shutting down")
      restClient.shutdown()
      logger.info("Exiting")
    }
    catch {
      case e: Throwable => {
        logger.error("Handle request failed: {}", e.getMessage)
        // Avoid retries
      }
    }
  }

  def getStatus(user: String): UserStatus = {
    val userTweetsFuture = searchProcessor.userTweets(user)
    val userFollowersFuture = searchProcessor.followers(user)
    val userTweets = getTweets(Seq(userTweetsFuture))
    val retweeted = userTweets.map(_.retweeted_status).flatten.map(_.id).toSet
    val followers = Await.result(userFollowersFuture, timeoutMinutes minutes)
    val likes = getTweets(Seq(searchProcessor.likes(user))).map(_.id).toSet

    UserStatus(
      followers = followers,
      retweetIds = retweeted,
      likes = likes
    )
  }

  def processWorkflow(workflow: Workflow) = {
    try {
      workflow.searches.foreach(processSearch(_, workflow))
      logger.info("Process workflow completed")
    } catch {
      case e: Throwable => {
        logger.error("Process workflow failed: {} {}",e.getMessage, workflow)
      }
    }
  }

  def processSearch(query: String, workflow: Workflow) {

    val resultType = workflow.result_type match {
      case "popular" => ResultType.Popular
      case "mixed" => ResultType.Mixed
      case "recent" => ResultType.Recent
      case _ => ResultType.Mixed
    }

    val searchFuture = searchProcessor.search(query, resultType, workflow.max_age)
    val searchTweets = getTweets(Seq(searchFuture))

    val validTweets = searchTweets.filter(t => !userStatus.retweetIds.contains(t.id))

    logger.info(s"Total: ${searchTweets.length}, valid: ${validTweets.length} tweets for search: ${query}")

    val newTweets = getTweets(actionProcessor.process(validTweets, workflow.actions))

    newTweets.foreach(tweet => {
      logger.info(s"Tweet processed: ${tweet.text}")
    })
    logger.info("Process search completed")
  }

  def getTweets(futures: Seq[Future[Seq[Tweet]]]): Seq[Tweet] = {
    futures.flatMap(f => {
      try {
        Await.result(f, timeoutMinutes minutes)
      } catch {
        case e: Throwable => {
          logger.error("Process future failed {}", e.getMessage)
          Seq()
        }
      }
    })
  }
}
