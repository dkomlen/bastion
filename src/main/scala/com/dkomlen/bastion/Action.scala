package com.dkomlen.bastion

import com.danielasfregola.twitter4s.TwitterRestClient
import com.danielasfregola.twitter4s.entities.Tweet
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future

trait Action extends LazyLogging {
  def run(tweets: Seq[Tweet], userStatus: UserStatus, twitterClient: TwitterRestClient): Seq[Future[Seq[Tweet]]]
}
