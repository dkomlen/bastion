package com.dkomlen.bastion

import com.danielasfregola.twitter4s.TwitterRestClient
import com.danielasfregola.twitter4s.entities.{Tweet, User}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future

trait CustomAction extends LazyLogging {
  def run(tweet: Tweet, user: User, userStatus: UserStatus, twitterClient: TwitterRestClient): Future[Seq[Tweet]]
}
