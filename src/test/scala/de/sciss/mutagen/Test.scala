//package de.sciss.mutagen
//
//import de.sciss.file._
//import de.sciss.osc
//import de.sciss.processor.Processor
//import de.sciss.synth.impl.DefaultUGenGraphBuilderFactory
//import de.sciss.synth.{addToHead, message, ugen, Ops, SynthDef, Server}
//
//import scala.concurrent.duration.Duration
//import scala.concurrent.{Promise, Await, ExecutionContext}
//import scala.util.{Failure, Success}
//
//object Test extends App {
//  import ExecutionContext.Implicits._
//  val cfg         = Mutagen.Config()
//  // cfg.seed        = 0L
//  cfg.in          = file("test-sound1.aif")
//  cfg.population  = 30
//  val done        = Promise[Unit]()
//  val proc: Mutagen = Mutagen.run(cfg) {
//    case Processor.Result(_, Success(xs)) =>
//      xs.foreach(println)
//      xs.headOption.foreach { x =>
//        import Ops._
//        val graph = x.graph
//        Server.run { s =>
//          import ugen._
//          val df = SynthDef("test", graph.expand(DefaultUGenGraphBuilderFactory))
//          df.play(s)
//
//          val n = play(target = s, addAction = addToHead) {
//            SendTrig.kr(MouseButton.kr(lag = 0))
//          }
//
//          message.Responder.add(s) {
//            case osc.Message("/tr", n.id, 0, _) =>
//              done.trySuccess(())
//          }
//        }
//      }
//      // done.success(())
//    case Processor.Result(_, Failure(ex)) =>
//      ex.printStackTrace()
//      done.failure(ex)
//  }
//  Await.ready(done.future, Duration.Inf)
//  sys.exit()
//}
