import com.danielasfregola.twitter4s.TwitterRestClient
import com.danielasfregola.twitter4s.entities.Tweet
import com.danielasfregola.twitter4s.entities.enums.TweetMode
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

class TweetProcessor(twitterClient: TwitterRestClient) extends LazyLogging {

  def process(tweets: Seq[Tweet], user: String, workflow: Workflow): Seq[Future[Seq[Tweet]]] = {

    val topTweet = tweets.sortBy(_.favorite_count)
      .reverse
      .headOption

    topTweet match {
      case Some(tweet) => {
        val futures = ListBuffer[Future[Seq[Tweet]]]()

        if (workflow.actions.contains("like")) {
          logger.info(s"Liking tweet: ${tweet.id_str}")
          futures += twitterClient.favoriteStatus(tweet.id).map(Seq(_))
        }

        tweet.user match {
          case Some(user) => {
            if (workflow.actions.contains("follow")) {
              logger.info(s"Following user: @${user.name}")
              twitterClient.followUserId(user.id)
            }

            if (workflow.actions.contains("comment")) {
              val comment = Random.shuffle(workflow.comments).head
              futures += twitterClient.createTweet(s"@${user.screen_name} $comment", in_reply_to_status_id = Some(tweet.id)).map(Seq(_))
            }
          }
          case None => {
            logger.info(s"No user available, skipping follow and comment")
          }
        }

        if (workflow.actions.contains("retweet")) {
          logger.info(s"Re-tweeting: ${tweet.id_str}")
          futures += twitterClient.retweet(tweet.id, tweet_mode = TweetMode.Extended).map(Seq(_))
        }
        futures.toList
      }
      case None => Seq()
    }
  }

}
