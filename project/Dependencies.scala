import sbt.*

object Dependencies:

  // ZIO
  val zio            = "dev.zio" %% "zio"              % Versions.zio
  val zioStreams     = "dev.zio" %% "zio-streams"      % Versions.zio
  val zioTest        = "dev.zio" %% "zio-test"         % Versions.zio % Test
  val zioTestSbt     = "dev.zio" %% "zio-test-sbt"     % Versions.zio % Test
  val zioInteropCats = "dev.zio" %% "zio-interop-cats" % Versions.zioInteropCats

  // Doobie (PostgreSQL persistence)
  val doobieCore     = "org.tpolecat" %% "doobie-core"     % Versions.doobie
  val doobieHikari   = "org.tpolecat" %% "doobie-hikari"   % Versions.doobie
  val doobiePostgres = "org.tpolecat" %% "doobie-postgres" % Versions.doobie

  // ZIO HTTP + Tapir
  val zioHttp        = "dev.zio"                     %% "zio-http"                % Versions.zioHttp
  val tapirCore      = "com.softwaremill.sttp.tapir" %% "tapir-core"              % Versions.tapir
  val tapirZioHttp   = "com.softwaremill.sttp.tapir" %% "tapir-zio-http-server"   % Versions.tapir
  val tapirJsonCirce = "com.softwaremill.sttp.tapir" %% "tapir-json-circe"        % Versions.tapir
  val tapirSwaggerUi = "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % Versions.tapir

  // ZIO Kafka
  val zioKafka = "dev.zio" %% "zio-kafka" % Versions.zioKafka

  // Flyway
  val flywayCore     = "org.flywaydb" % "flyway-core"                % Versions.flyway
  val flywayPostgres = "org.flywaydb" % "flyway-database-postgresql" % Versions.flyway

  // Database drivers / pool
  val postgresql = "org.postgresql" % "postgresql" % Versions.postgresql
  val hikaricp   = "com.zaxxer"     % "HikariCP"   % Versions.hikaricp

  // Circe JSON
  val circeCore    = "io.circe" %% "circe-core"    % Versions.circe
  val circeGeneric = "io.circe" %% "circe-generic" % Versions.circe
  val circeParser  = "io.circe" %% "circe-parser"  % Versions.circe

  // Logging
  val zioLogging      = "dev.zio"        %% "zio-logging"        % Versions.zioLogging
  val zioLoggingSlf4j = "dev.zio"        %% "zio-logging-slf4j2" % Versions.zioLogging
  val logback         = "ch.qos.logback"  % "logback-classic"    % Versions.logback

  // Test — HTTP adapter
  val tapirStubServer = "com.softwaremill.sttp.tapir"  %% "tapir-sttp-stub4-server" % Versions.tapir        % Test
  val sttpClientZio   = "com.softwaremill.sttp.client4" %% "zio"                    % Versions.sttpClient4  % Test

  // ProtoQuill (ZIO JDBC)
  val quillJdbcZio = "io.getquill" %% "quill-jdbc-zio" % Versions.protoQuill

  val commonTest: Seq[ModuleID] = Seq(zioTest, zioTestSbt)
