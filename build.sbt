name               := "Mutagen"

version            := "0.1.0-SNAPSHOT"

organization       := "de.sciss"

scalaVersion       := "2.11.5"

crossScalaVersions := Seq("2.11.5", "2.10.4")

description        := "An experiment with genetic programming and ScalaCollider"

homepage           := Some(url("https://github.com/Sciss/" + name.value))

licenses           := Seq("GPL v3+" -> url("http://www.gnu.org/licenses/gpl-3.0.txt"))

lazy val soundProcessesVersion  = "2.12.0-SNAPSHOT"

lazy val ugensVersion           = "1.13.0-SNAPSHOT"

lazy val strugatzkiVersion      = "2.8.0-SNAPSHOT"

lazy val mutaVersion            = "0.5.0-SNAPSHOT"

lazy val fileCacheVersion       = "0.3.2"

lazy val webLaFVersion          = "1.28"

libraryDependencies ++= Seq(
  "de.sciss" %% "soundprocesses-core"     % soundProcessesVersion,
  "de.sciss" %  "scalacolliderugens-spec" % ugensVersion,
  "de.sciss" %% "strugatzki"              % strugatzkiVersion,
  "de.sciss" %% "muta"                    % mutaVersion,
  "de.sciss" %% "filecache-mutable"       % fileCacheVersion,
  "de.sciss" %  "weblaf"                  % webLaFVersion
)

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture")

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
