package com.dkomlen.bastion

import com.danielasfregola.twitter4s.TwitterRestClient
import com.danielasfregola.twitter4s.entities.Tweet
import com.danielasfregola.twitter4s.entities.enums.TweetMode
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

case class UserStatus(
                       followers: Set[Long],
                       retweetIds: Set[Long],
                       likes: Set[Long],
                       tweets: Seq[Tweet]
                     )

case class ActionConfig(
                         filter: List[String] = List(),
                         order: List[String] = List("like-desc"),
                         take: Int = 1,
                         act: List[String],
                         comments: List[String] = List()
                       )

class ActionProcessor(twitterClient: TwitterRestClient, userStatus: UserStatus) extends LazyLogging {

  def process(tweets: Seq[Tweet], actions: Seq[ActionConfig]): Seq[Future[Seq[Tweet]]] = {

    actions.flatMap(action => {

      val select = (applyFilters(action.filter)(_)).andThen(applyOrders(action.order)(_)).andThen(applyTake(action.take)(_))
      val selected = select(tweets)
      action.act.flatMap(applyAction(_, action.comments, selected))
    })
  }

  def applyAction(action: String, comments: Seq[String], tweets: Seq[Tweet]): Seq[Future[Seq[Tweet]]] = {

    action.split(":") match {
      case Array("custom", classname) => {
        applyCustomAction(tweets, classname)
      }
      case _ => {
        tweets.map(tweet => {
          (action, tweet.user) match {
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
          }
        })
      }
    }
  }

  private def applyCustomAction(tweets: Seq[Tweet], classname: String) = {
    val action: Action = Class.forName(classname).newInstance.asInstanceOf[Action]
    action.run(tweets, userStatus, twitterClient)
  }

  def applyFilters(filters: List[String])(tweets: Seq[Tweet]): Seq[Tweet] = filters match {
    case Nil => tweets
    case filter :: tail => {
      val filtered = filter.split(":") match {
        case Array("custom", classname) => {
          applyCustomFilter(tweets, classname)
        }
        case Array("not-following") => {
          tweets.filter(tw => tw.user.isDefined && !userStatus.followers.contains(tw.user.get.id))
        }
        case Array("not-reply") => {
          tweets.filter(tw => !tw.is_quote_status &&
            tw.in_reply_to_screen_name.isEmpty &&
            tw.in_reply_to_status_id.isEmpty &&
            tw.in_reply_to_user_id.isEmpty)
        }
        case Array("not-liked") => {
          tweets.filter(!isLiked(_))
        }
        case Array("follows") => {
          tweets.filter(tw => tw.user.isDefined && userStatus.followers.contains(tw.user.get.id))
        }
        case Array("not-retweeted") => {
          tweets.filter(t => !userStatus.retweetIds.contains(t.id))
        }
        case _ => tweets
      }
      applyFilters(tail)(filtered)
    }
  }

  def applyCustomFilter(tweets: Seq[Tweet], classname: String): Seq[Tweet] = {
    val filter: Filter = Class.forName(classname).newInstance.asInstanceOf[Filter]
    filter.run(tweets)
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
