package s3

import javax.inject.{Inject, Singleton}
import play.api.Configuration

@Singleton
class S3Configuration @Inject()(val configuration: Configuration) {

  lazy val bucketName = configuration.get[String]("nio.s3Config.bucketName")

  lazy val chunkSizeInMb = 1024 * 1024 * configuration.get[Int](
    "nio.s3Config.chunkSizeInMb")

  lazy val expirationCheckInSeconds =
    configuration.get[Int]("nio.s3Config.expirationCheckPeriodInSeconds")

  lazy val expirationInDays =
    configuration.get[Int]("nio.s3Config.expirationInDays")

  lazy val endpoint = configuration.get[String]("nio.s3Config.host")

  lazy val region = configuration.get[String]("nio.s3Config.region")

  lazy val access = configuration.get[String]("nio.s3Config.accessKey")

  lazy val secret = configuration.get[String]("nio.s3Config.secretKey")

  lazy val v4auth = configuration.get[Boolean]("nio.s3Config.v4auth")
}
