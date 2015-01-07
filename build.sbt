name               := "Mutagen"

version            := "0.1.0-SNAPSHOT"

organization       := "de.sciss"

scalaVersion       := "2.11.4"

crossScalaVersions := Seq("2.11.4", "2.10.4")

description        := "An experiment with genetic programming and ScalaCollider"

homepage           := Some(url("https://github.com/Sciss/" + name.value))

licenses           := Seq("GPL v2+" -> url("http://www.gnu.org/licenses/gpl-2.0.txt"))

lazy val soundProcessesVersion= "2.11.1-SNAPSHOT"

lazy val scalaColliderVersion = "1.15.0"

lazy val ugensVersion         = "1.12.0"

lazy val strugatzkiVersion    = "2.7.0"

lazy val topologyVersion      = "1.0.0"

libraryDependencies ++= Seq(
  "de.sciss" %% "soundprocesses-core"     % soundProcessesVersion,
  "de.sciss" %% "scalacollider"           % scalaColliderVersion,
  "de.sciss" %  "scalacolliderugens-spec" % ugensVersion,
  "de.sciss" %% "strugatzki"              % strugatzkiVersion,
  "de.sciss" %% "topology"                % topologyVersion
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
