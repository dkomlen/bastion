package com.dkomlen.bastion

import com.danielasfregola.twitter4s.TwitterRestClient
import com.danielasfregola.twitter4s.entities.{Tweet, User}
import com.danielasfregola.twitter4s.entities.enums.TweetMode
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

case class Action(
                   filter: List[String] = List(),
                   order: List[String] = List("like"),
                   take: Int = 1,
                   act: List[String],
                   comments: List[String] = List()
                 )

class ActionProcessor(twitterClient: TwitterRestClient, followers: Set[User]) extends LazyLogging {

  def process(tweets: Seq[Tweet], actions: Seq[Action]): Seq[Future[Seq[Tweet]]] = {

    actions.flatMap(action => {

      val select = (applyFilters(action.filter)(_)).andThen(applyOrders(action.order)(_)).andThen(applyTake(action.take)(_))
      select(tweets).flatMap(applyActs(action.act, action.comments))

    })
  }

  def applyActs(acts: Seq[String], comments: Seq[String])(tweet: Tweet): List[Future[Seq[Tweet]]] = {

    acts.headOption match {
      case None => List()
      case Some(act) => {
        val future = (act, tweet.user) match {
          case ("like", _) if !tweet.favorited => {
            logger.info(s"Liking tweet: ${tweet.id_str}")
            twitterClient.favoriteStatus(tweet.id).map(Seq(_))
          }
          case ("follow", Some(user)) if !user.following => {
            logger.info(s"Following user: @${user.screen_name}")
            twitterClient.followUserId(user.id)
            Future(Seq[Tweet]())
          }
          case ("comment", Some(user)) => {
            val comment = Random.shuffle(comments).head
            logger.info(s"Commenting on tweet: ${tweet.id_str}, $comment")
            twitterClient.createTweet(s"@${user.screen_name} $comment", in_reply_to_status_id = Some(tweet.id)).map(Seq(_))
          }
          case ("retweet", _) => {
            logger.info(s"Re-tweeting: ${tweet.id_str}")
            twitterClient.retweet(tweet.id, tweet_mode = TweetMode.Extended).map(Seq(_))
          }
          case (_, _) => Future(Seq[Tweet]())
        }
        future :: applyActs(acts.tail, comments)(tweet)
      }
    }
  }

  def applyFilters(filters: List[String])(tweets: Seq[Tweet]): Seq[Tweet] = filters match {
    case Seq() => tweets
    case filter :: tail => {
      val filtered = filter match {
        case "not-following" => {
          tweets.filter(tw => tw.user.isDefined && !followers.map(_.screen_name).contains(tw.user.get.screen_name))
        }
        case _ => tweets
      }
      applyFilters(tail)(filtered)
    }
  }

  def applyOrders(orders: List[String])(tweets: Seq[Tweet]): Seq[Tweet] = orders match {
    case Seq() => tweets
    case order :: tail => {
      val ordered = order match {
        case "like" => tweets.sortBy(_.favorite_count).reverse
        case _ => tweets
      }
      applyOrders(tail)(ordered)
    }
  }

  def applyTake(n: Int)(tweets: Seq[Tweet]): Seq[Tweet] = {
    tweets.take(n)
  }
}
