name               := "Mutagen"
version            := "0.1.0"
organization       := "de.sciss"
scalaVersion       := "2.11.8"
crossScalaVersions := Seq("2.11.8", "2.10.6")
description        := "An experiment with genetic programming and ScalaCollider"
homepage           := Some(url("https://github.com/Sciss/" + name.value))
licenses           := Seq("GPL v3+" -> url("http://www.gnu.org/licenses/gpl-3.0.txt"))

lazy val mutaVersion               = "0.7.0"
lazy val soundProcessesVersion     = "3.8.0"
lazy val ugensVersion              = "1.16.0"
lazy val strugatzkiVersion         = "2.13.0"
lazy val fileCacheVersion          = "0.3.3"
lazy val kollFlitzVersion          = "0.2.0"
lazy val scalaColliderSwingVersion = "1.31.0"
lazy val audioFileVersion          = "1.4.4"
lazy val lucreSwingVersion         = "1.4.0"
lazy val pdflitzVersion            = "1.2.1"
lazy val subminVersion             = "0.2.1"

// required for Play JSON
resolvers += "Typesafe Releases" at "https://repo.typesafe.com/typesafe/maven-releases/"

libraryDependencies ++= Seq(
  "de.sciss" %% "muta"                    % mutaVersion,
  "de.sciss" %% "soundprocesses-core"     % soundProcessesVersion,
  "de.sciss" %% "scalacolliderugens-core" % ugensVersion,
  "de.sciss" %  "scalacolliderugens-spec" % ugensVersion,
  "de.sciss" %% "strugatzki"              % strugatzkiVersion,
  "de.sciss" %% "filecache-mutable"       % fileCacheVersion,
  "de.sciss" %% "kollflitz"               % kollFlitzVersion,
  "de.sciss" %% "scalacolliderswing-core" % scalaColliderSwingVersion,
  "de.sciss" %% "scalaaudiofile"          % audioFileVersion,
  "de.sciss" %% "lucreswing"              % lucreSwingVersion,
  "de.sciss" %% "pdflitz"                 % pdflitzVersion,
  "de.sciss" %  "submin"                  % subminVersion
)

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture", "-Xlint")

// ---- assembly ----

target  in assembly := baseDirectory.value

assemblyJarName in assembly := s"${name.value}.jar"

assemblyMergeStrategy in assembly := {
  case PathList("org", "w3c", "dom", "events", xs @ _*) => MergeStrategy.first // bloody Apache Batik
  case x =>
    val old = (assemblyMergeStrategy in assembly).value
    old(x)
}

// ---- publishing ----

publishMavenStyle := true

publishTo :=
  Some(if (isSnapshot.value)
    "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  else
    "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
  )

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := { val n = name.value
<scm>
  <url>git@github.com:Sciss/{n}.git</url>
  <connection>scm:git:git@github.com:Sciss/{n}.git</connection>
</scm>
<developers>
  <developer>
    <id>sciss</id>
    <name>Hanns Holger Rutz</name>
    <url>http://www.sciss.de</url>
  </developer>
</developers>
}
