import com.danielasfregola.twitter4s.TwitterRestClient
import com.danielasfregola.twitter4s.entities.Tweet
import com.danielasfregola.twitter4s.entities.enums.TweetMode
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TweetProcessor(twitterClient: TwitterRestClient) extends LazyLogging {

  def process(tweets: Seq[Tweet]): Future[Seq[Tweet]] = {

    val topTweet = tweets.sortBy(_.favorite_count)
      .reverse
      .headOption

    topTweet match {
      case Some(tweet) => {
        logger.info(s"Liking tweet: ${tweet.id_str}")
        twitterClient.favoriteStatus(tweet.id)
        logger.info(s"Re-tweeting: ${tweet.id_str}")

        tweet.user match {
          case Some(user) => {
            logger.info(s"Following user: @${user.name}")
            twitterClient.followUserId(user.id)
          }
          case None => {
            logger.info(s"No user availble, skipping follow")
            Future(Seq.empty)
          }
        }

        twitterClient.retweet(tweet.id, tweet_mode = TweetMode.Extended).map(Seq(_))
      }
      case None => Future(Seq())
    }
  }
}
