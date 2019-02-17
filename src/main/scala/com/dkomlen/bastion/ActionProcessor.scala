package com.dkomlen.bastion

import com.danielasfregola.twitter4s.TwitterRestClient
import com.danielasfregola.twitter4s.entities.Tweet
import com.danielasfregola.twitter4s.entities.enums.TweetMode
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

case class Action(
                   filter: Option[Seq[String]] = Some(Seq()),
                   order: Seq[String] = Seq("like"),
                   take: Option[Int] = Some(1),
                   act: Seq[String],
                   comments: Option[Seq[String]] = Some(Seq())
                 )

class ActionProcessor(twitterClient: TwitterRestClient) extends LazyLogging {

  def process(tweets: Seq[Tweet], user: String, actions: Seq[Action]): Seq[Future[Seq[Tweet]]] = {

    val futures = actions.map(action => {

      val topTweet = tweets.sortBy(_.favorite_count)
        .reverse
        .headOption

      topTweet match {
        case Some(tweet) => {
          val futures = ListBuffer[Future[Seq[Tweet]]]()

          if (action.act.contains("like")) {
            logger.info(s"Liking tweet: ${tweet.id_str}")
            futures += twitterClient.favoriteStatus(tweet.id).map(Seq(_))
          }

          tweet.user match {
            case Some(user) => {
              if (action.act.contains("follow")) {
                logger.info(s"Following user: @${user.name}")
                twitterClient.followUserId(user.id)
              }

              if (action.act.contains("comment")) {
                val comment = Random.shuffle(action.comments.get).head
                futures += twitterClient.createTweet(s"@${user.screen_name} $comment", in_reply_to_status_id = Some(tweet.id)).map(Seq(_))
              }
            }
            case None => {
              logger.info(s"No user available, skipping follow and comment")
            }
          }

          if (action.act.contains("retweet")) {
            logger.info(s"Re-tweeting: ${tweet.id_str}")
            futures += twitterClient.retweet(tweet.id, tweet_mode = TweetMode.Extended).map(Seq(_))
          }
          futures.toList
        }
        case None => Seq()
      }
    })

    futures.flatten
  }
}
