package de.sciss.mutagen

case class FeatureExtractionFailed(cause: Throwable) extends Exception(cause)