name               := "Mutagen"

version            := "0.1.0-SNAPSHOT"

organization       := "de.sciss"

scalaVersion       := "2.11.5"

crossScalaVersions := Seq("2.11.5", "2.10.4")

description        := "An experiment with genetic programming and ScalaCollider"

homepage           := Some(url("https://github.com/Sciss/" + name.value))

licenses           := Seq("GPL v3+" -> url("http://www.gnu.org/licenses/gpl-3.0.txt"))

lazy val soundProcessesVersion  = "2.14.1"

lazy val ugensVersion           = "1.13.1"

lazy val strugatzkiVersion      = "2.9.0"

lazy val mutaVersion            = "0.5.0"

lazy val fileCacheVersion       = "0.3.2"

// lazy val audioWidgetsVersion    = "1.7.2"

lazy val scalaColliderSwingVersion = "1.24.0"

lazy val audioFileVersion       = "1.4.4"

lazy val webLaFVersion          = "1.28"

libraryDependencies ++= Seq(
  "de.sciss" %% "soundprocesses-core"     % soundProcessesVersion,
  "de.sciss" %% "scalacolliderugens-core" % ugensVersion,
  "de.sciss" %  "scalacolliderugens-spec" % ugensVersion,
  "de.sciss" %% "strugatzki"              % strugatzkiVersion,
  "de.sciss" %% "muta"                    % mutaVersion,
  "de.sciss" %% "filecache-mutable"       % fileCacheVersion,
  // "de.sciss" %% "audiowidgets-swing"      % audioWidgetsVersion,
  "de.sciss" %% "scalacolliderswing-core" % scalaColliderSwingVersion,
  "de.sciss" %% "scalaaudiofile"          % audioFileVersion,
  "de.sciss" %  "weblaf"                  % webLaFVersion
)

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture")

// ---- assembly ----

target  in assembly := baseDirectory.value

jarName in assembly := s"${name.value}.jar"

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
