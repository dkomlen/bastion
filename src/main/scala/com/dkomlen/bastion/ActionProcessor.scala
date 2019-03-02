package com.dkomlen.bastion

import com.danielasfregola.twitter4s.TwitterRestClient
import com.danielasfregola.twitter4s.entities.enums.TweetMode
import com.danielasfregola.twitter4s.entities.{Tweet, User}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

case class UserStatus(
                       followers: Set[User],
                       retweetIds: Set[Long],
                       likes: Set[Long]
                     )

case class Action(
                   filter: List[String] = List(),
                   order: List[String] = List("like-desc"),
                   take: Int = 1,
                   act: List[String],
                   comments: List[String] = List()
                 )

class ActionProcessor(twitterClient: TwitterRestClient, userStatus: UserStatus) extends LazyLogging {

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
          case ("like", _) if !isLiked(tweet) => {
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
          case ("retweet", _) if !isRetweeted(tweet) => {
            logger.info(s"Re-tweeting: ${tweet.id_str}")
            twitterClient.retweet(tweet.id, tweet_mode = TweetMode.Extended).map(Seq(_))
          }
          case (action, Some(user)) => action.split(":") match {
            case Array("custom", classname) => {
              applyCustomAction(tweet, user, classname)
            }
            case _ => Future(Seq[Tweet]())
          }
        }
        future :: applyActs(acts.tail, comments)(tweet)
      }
    }
  }

  private def applyCustomAction(tweet: Tweet, user: User, classname: String) = {
    val action: CustomAction = Class.forName(classname).newInstance.asInstanceOf[CustomAction]
    action.run(tweet, user, userStatus, twitterClient)
  }

  def applyFilters(filters: List[String])(tweets: Seq[Tweet]): Seq[Tweet] = filters match {
    case Nil => tweets
    case filter :: tail => {
      val filtered = filter match {
        case "not-following" => {
          tweets.filter(tw => tw.user.isDefined && !userStatus.followers.map(_.screen_name).contains(tw.user.get.screen_name))
        }
        case "not-reply" => {
          tweets.filter(tw => !tw.is_quote_status &&
            tw.in_reply_to_screen_name.isEmpty &&
            tw.in_reply_to_status_id.isEmpty &&
            tw.in_reply_to_user_id.isEmpty)
        }
        case "not-liked" => {
          tweets.filter(!isLiked(_))
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
        case "like-desc" => tweets.sortBy(_.favorite_count).reverse
        case "friends-desc" => tweets.sortBy(_.user.map(_.friends_count).getOrElse(0)).reverse
        case "age-asc" => tweets.sortBy(_.created_at).reverse
        case _ => tweets
      }
      applyOrders(tail)(ordered)
    }
  }

  def applyTake(n: Int)(tweets: Seq[Tweet]): Seq[Tweet] = {
    tweets.take(n)
  }

  private def isLiked(tweet: Tweet) = tweet.favorited || userStatus.likes.contains(tweet.id)

  private def isRetweeted(tweet: Tweet) = tweet.retweeted || userStatus.retweetIds.contains(tweet.id)

}
