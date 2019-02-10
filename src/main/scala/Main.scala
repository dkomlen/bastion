import com.amazonaws.services.lambda.runtime.events.ScheduledEvent
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.danielasfregola.twitter4s.TwitterRestClient
import com.danielasfregola.twitter4s.entities.Tweet
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

case class BastionConfig(
                          user: String,
                          searches: List[String])

class Main extends RequestHandler[ScheduledEvent, Future[Unit]] with LazyLogging {

  val config: Config = ConfigFactory.load()
  val test: BastionConfig = config.as[BastionConfig]("bastion")

  val restClient = TwitterRestClient()
  val tweetSearch = new TweetSearch(restClient)
  val tweetProcessor = new TweetProcessor(restClient)
  val timeoutMinutes = 2

  def handleRequest(event: ScheduledEvent, context: Context) = {

    test.searches.par.foreach(processSearch(_))

    logger.info("Shutting down")
    restClient.shutdown()
  }

  def processSearch(query: String) = {
    val searchFuture = tweetSearch.search(query)
    val userTweetsFuture = tweetSearch.userTweets(test.user)

    val searchTweets = getTweets(searchFuture)
    val userTweets = getTweets(userTweetsFuture)

    val retweeted = userTweets.map(_.retweeted_status).flatten.map(_.id).toSet
    val validTweets = searchTweets.filter(t => !retweeted.contains(t.id))

    val newTweet = getTweets(tweetProcessor.process(validTweets))
    logger.info(s"New tweet created: ${newTweet.headOption.map(_.text)}")
  }

  def getTweets(tweetsFuture: Future[Seq[Tweet]]): Seq[Tweet] = {
    Await.result(tweetsFuture, timeoutMinutes minutes)
  }
}

