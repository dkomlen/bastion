import java.util.Calendar

import com.danielasfregola.twitter4s.TwitterRestClient
import com.danielasfregola.twitter4s.entities.Tweet
import com.danielasfregola.twitter4s.entities.enums.Language
import com.danielasfregola.twitter4s.entities.enums.ResultType.ResultType
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TweetSearch(twitterClient: TwitterRestClient) extends LazyLogging {

  def search(query: String, resultType: ResultType, maxAge: Option[Int], max_id: Option[Long] = None): Future[Seq[Tweet]] = {
    logger.info(s"Starting tweet search: [$query]")

    def extractNextMaxId(params: Option[String]): Option[Long] = {
      params.getOrElse("").split("&").find(_.contains("max_id")).map(_.split("=")(1).toLong)
    }

    twitterClient.searchTweet(
      query, count = 20, result_type = resultType,
      max_id = max_id, language = Some(Language.English)).flatMap { ratedData =>

      val result = ratedData.data
      val nextMaxId = extractNextMaxId(result.search_metadata.next_results)
      val tweets = maxAge match {
        case Some(age) => {
          val cal = Calendar.getInstance()
          cal.add(Calendar.MINUTE, -age)
          result.statuses.filter(_.created_at.after(cal.getTime))
        }
        case None => result.statuses
      }

      if (tweets.size >= 20) search(query, resultType, maxAge, nextMaxId).map(_ ++ tweets)
      else Future(tweets.sortBy(_.created_at))
    } recover { case _ => Seq.empty }
  }

  def userTweets(userId: String): Future[Seq[Tweet]] = {
    logger.info(s"Starting search for latest user tweets: $userId")
    twitterClient.userTimelineForUser(userId).map {
      ratedData =>
        val tweets = ratedData.data
        tweets
    } recover {
      case _ => Seq.empty
    }
  }
}

