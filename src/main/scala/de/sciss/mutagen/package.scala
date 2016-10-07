/*
 *  package.scala
 *  (Mutagen)
 *
 *  Copyright (c) 2014-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss

import de.sciss.topology.Topology

package object mutagen {
  type Top = Topology[Vertex, Edge]

  type Vec[+A]  = scala.collection.immutable.IndexedSeq[A]
  val  Vec      = scala.collection.immutable.IndexedSeq
}
