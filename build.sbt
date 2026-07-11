import Dependencies.*

addCommandAlias("xdup", "dependencyUpdates")
addCommandAlias("integrationTest", "integrationTests/test")

// quill-jdbc-zio pulls zio-schema-json → zio-json 0.9.1 while zio-kafka/http want 0.7.1.
// Declare always-compatible so SBT silently selects the higher version.
ThisBuild / libraryDependencySchemes += "dev.zio" %% "zio-json" % "always"

organization  := "dev.cmartin.aerohex"
scalaVersion  := Versions.scala3  // 3.3.8 LTS
version       := "0.1.0-SNAPSHOT"

scalacOptions := Seq(
  "-encoding", "utf8",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Wunused:all",
  "-Wvalue-discard",
  "-Wnonunit-statement"
)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
libraryDependencies ++= commonTest

// coverageDataDir outside target/ so sbt clean never deletes the statement catalog
val coverageSettings: Seq[Setting[?]] = Seq(
  coverageEnabled := true,
  coverageDataDir := baseDirectory.value / ".coverage-data"
)

// ─── Modules ────────────────────────────────────────────────────────────────

lazy val root = rootProject
  .aggregate(
    sharedKernel,
    domain,
    application,
    persistencePostgres,
    persistenceQuill,
    messagingKafka,
    migration,
    adapterHttp,
    bootstrap
  )
  .settings(
    name           := "aero-hex-ai",
    publish / skip := true
  )
  .disablePlugins(AssemblyPlugin)

lazy val sharedKernel = project
  .in(file("shared-kernel"))
  .settings(
    name := "shared-kernel",
    libraryDependencies ++= Seq(zio)
  )
  .settings(coverageSettings*)
  .disablePlugins(AssemblyPlugin)

lazy val domain = project
  .in(file("domain"))
  .dependsOn(sharedKernel)
  .settings(
    name := "domain",
    libraryDependencies ++= Seq(zio, zioPrelude)
  )
  .settings(coverageSettings*)
  .disablePlugins(AssemblyPlugin)

lazy val application = project
  .in(file("application"))
  .dependsOn(domain)
  .settings(
    name := "application",
    libraryDependencies ++= Seq(zio)
  )
  .settings(coverageSettings*)
  .disablePlugins(AssemblyPlugin)

lazy val persistencePostgres = project
  .in(file("infrastructure/persistence-postgres"))
  .dependsOn(domain)
  .settings(
    name := "persistence-postgres",
    libraryDependencies ++= Seq(
      doobieCore,
      doobieHikari,
      doobiePostgres,
      zioInteropCats,
      postgresql,
      hikaricp
    )
  )
  .settings(coverageSettings*)
  .disablePlugins(AssemblyPlugin)

lazy val persistenceQuill = project
  .in(file("infrastructure/persistence-quill"))
  .dependsOn(domain)
  .settings(
    name := "persistence-quill",
    libraryDependencies ++= Seq(
      quillJdbcZio,
      postgresql,
      hikaricp
    )
  )
  .settings(coverageSettings*)
  .disablePlugins(AssemblyPlugin)

lazy val messagingKafka = project
  .in(file("infrastructure/messaging-kafka"))
  .dependsOn(domain)
  .settings(
    name := "messaging-kafka",
    libraryDependencies ++= Seq(
      zioKafka,
      circeCore,
      circeGeneric,
      circeParser
    )
  )
  .settings(coverageSettings*)
  .disablePlugins(AssemblyPlugin)

lazy val migration = project
  .in(file("infrastructure/migration"))
  .settings(
    name := "migration",
    libraryDependencies ++= Seq(
      flywayCore,
      flywayPostgres,
      postgresql,
      zio
    )
  )
  .settings(coverageSettings*)
  .disablePlugins(AssemblyPlugin)

lazy val adapterHttp = project
  .in(file("adapter-http"))
  .dependsOn(domain, application)
  .settings(
    name := "adapter-http",
    libraryDependencies ++= Seq(
      tapirCore,
      tapirZioHttp,
      tapirJsonCirce,
      tapirSwaggerUi,
      zioHttp,
      circeCore,
      circeGeneric,
      circeParser,
      tapirStubServer,
      sttpClientZio,
      sttpClientCirce
    )
  )
  .settings(coverageSettings*)
  .disablePlugins(AssemblyPlugin)

lazy val bootstrap = project
  .in(file("bootstrap"))
  .dependsOn(domain, application, adapterHttp, persistenceQuill, persistencePostgres, migration)
  .settings(
    name := "bootstrap",
    libraryDependencies ++= Seq(
      zio,
      zioLogging,
      zioLoggingSlf4j,
      logback
    ),
    Compile / mainClass   := Some("dev.cmartin.aerohex.bootstrap.Main"),
    assembly / mainClass  := Some("dev.cmartin.aerohex.bootstrap.OpenApiGenerator"),
    assembly / assemblyMergeStrategy := {
      case PathList("module-info.class")                             => MergeStrategy.discard
      case PathList("META-INF", "versions", _, "module-info.class") => MergeStrategy.discard
      case PathList("META-INF", "services", _*)                     => MergeStrategy.concat
      case PathList("META-INF", "resources", _*)                    => MergeStrategy.first
      case PathList("META-INF", "maven", "org.webjars", _*)         => MergeStrategy.first
      case PathList("META-INF", xs @ _*)                            => MergeStrategy.discard
      case PathList("org", "jline", _*)                             => MergeStrategy.first
      case PathList("scala", "tools", _*)                           => MergeStrategy.first
      case PathList("io", "getquill", _*)                           => MergeStrategy.first
      case "compiler.properties"                                     => MergeStrategy.first
      case "deriving.conf"                                           => MergeStrategy.concat
      case "reference.conf"                                          => MergeStrategy.concat
      case x =>
        val old = (assembly / assemblyMergeStrategy).value
        old(x)
    }
  )
  .settings(coverageSettings*)

// Opt-in integration tests against a real Postgres (Testcontainers). Deliberately NOT
// added to `root`'s .aggregate(...) below, so `sbt compile`, `sbt "testOnly *"`, and
// `sbt coverageAggregate` at the root never reach it — see
// plans/add-persistence-integration-tests.md. Invoke directly with
// `sbt integrationTests/test` or the `integrationTest` alias.
lazy val integrationTests = project
  .in(file("infrastructure/integration-tests"))
  .dependsOn(migration, persistencePostgres, persistenceQuill)
  .settings(
    name           := "integration-tests",
    publish / skip := true,
    Test / fork    := true,
    // Testcontainers 1.21.x's Docker environment probe falls back to a hardcoded API
    // version (1.32) when none is negotiated; recent Docker Desktop releases reject
    // that as below their MinAPIVersion (400 Bad Request), so container startup fails
    // with a misleading "Could not find a valid Docker environment". Pinning a modern
    // api.version sidesteps the probe's stale default.
    Test / javaOptions += "-Dapi.version=1.41",
    libraryDependencies ++= Seq(testcontainersCore, testcontainersPostgres, logback % Test)
  )
  .disablePlugins(AssemblyPlugin)
