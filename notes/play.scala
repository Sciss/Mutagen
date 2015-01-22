play {
  val v0 = 1942.31
  val v1 = 7116.174
  val v2 = 62.089928
  val v3 = 0.104701735
  val v4 = Lag3UD.ar(v1, v2, v3)
  val v5 = FreeVerb.ar(v0, v1, v4, v4)
  Pan2.ar(Limiter.ar(LeakDC.ar(v5)))
}

play {
  val v0 = 12.280264
  val v1 = Gendy3.ar(v0, v0, v0, v0, v0, v0, v0, v0, v0)
  val v2 = LFNoise1.ar(v1)
  val v3 = OnePole.ar(v2, v1)
  val v4 = RunningMin.ar(v2, v3)
  val v5 = 189.05743
  val v6 = LastValue.ar(v5, 0.01)
  val v7 = BiPanB2.ar(v5, v4, v1, v4)
  val v8 = 2.66733
  val v9 = -2.496865
  val v10 = -64.77785
  val v11 = LatoocarfianC.ar(v9, v7, v10, v4, v8, v10, 0.5)
  val v12 = CombC.ar(v1, v0, v0, v0)
  val v13 = -0.3357258
  val v14 = LatoocarfianL.ar(v8, v8, v7, v13, v4, v12, v12)
  val v15 = PitchShift.ar(v5, 0.2, 1.0, v14, v0)
  val v16 = Wrap.ar(v2, v11, v15)
  val v17 = GbmanN.ar(v16, 1.2, v12)
  val v18 = FSinOsc.ar(v0, v7)
  val v19 = LagUD.ar(v2, v12, v13)
  val v20 = PulseCount.ar(v19, v7)
  val v21 = IEnvGen.ar(v20, v7)
  val v22 = -0.107134044
  val v23 = TWindex.ar(v19, v22, v8)
  val v24 = 0.00449773
  val v25 = TIRand.ar(v15, 127.0, v24)
  val v26 = DelayC.ar(v23, 0.2, v25)
  val v27 = QuadC.ar(v24, v16, v4, v26, 0.0)
  val v28 = 0.013239149
  val v29 = 1229.5626
  val v30 = 525.27936
  val v31 = Pan4.ar(v27, v28, v29, v30)
  val v32 = QuadL.ar(v22, v19, v23, v0, 0.0)
  val v33 = LPZ2.ar(v23)
  val v34 = Delay1.ar(v5)
  val v35 = Pulse.ar(v5, v2)
  val v36 = PanAz.ar(1, v15, v19, 1.0, v5, v19)
  val v37 = LFNoise0.ar(v9)
  val roots = Vector(v37, v36, v35, v34, v33, v32, v31, v21, v18, v17, v6)
  val sig = Mix(roots)
  Limiter.ar(LeakDC.ar(sig))
}

play {
  RandSeed.ir()
  val v0 = 56.278736
  val v1 = 0.44839144
  val v2 = -2058.7322
  val v3 = 7565.702
  val v4 = 0.038494907
  val v5 = HenonN.ar(v4, v2, v2, v3, v4)
  val v6 = -1193.0447
  val v7 = StandardL.ar(v1, v3, v3, v4)
  val v8 = LFPulse.ar(v1, v6, v7)
  val v9 = GrayNoise.ar(v7)
  val roots = Vector(v9, v8, v5)
  val sig = Mix(roots)
  Limiter.ar(LeakDC.ar(sig))
}

play {
  RandSeed.ir()
  val v0 = -0.011625628
  val v1 = 40.33182
  val v2 = 56.278736
  val v3 = RLPF.ar(v2, v0, v1)
  val v4 = Hilbert.ar(v3)
  val v5 = LFNoise2.ar(v4)
  val v6 = 0.0015419222
  val v7 = Schmidt.ar(v3, v5, v6)
  val v8 = Decay2.ar(v6, v2, v1)
  val v9 = GrayNoise.ar(v8)
  val v10 = HenonN.ar(v2, v9, v0, v4, v1)
  val v11 = A2K.kr(v4)
  val v12 = A2K.kr(v9)
  val roots = Vector(v12, v11, v10, v7)
  val sig = Mix(roots)
  Limiter.ar(LeakDC.ar(sig))
}

play {
  RandSeed.ir()
  val v0 = 40.33182
  val v1 = 56.865112
  val v2 = 0.050732173
  val v3 = PanB.ar(v0, v2, v1, v1)
  val v4 = -0.0039715525
  val v5 = CuspL.ar(v3, v1, v3, v4)
  val v6 = LFNoise2.ar(v5)
  val v7 = 0.0015662607
  val v8 = Decay2.ar(v7, v1, v0)
  val v9 = HenonL.ar(v0, v6, v7, v7, v0)
  val v10 = Phasor.ar(v2, v2, v9, v8, v0)
  val sig = v10
  Limiter.ar(LeakDC.ar(sig))
}

play {
  RandSeed.ir()
  val v0 = 40.33182
  val v1 = 56.865112
  val v2 = 0.050732173
  val v3 = PanB.ar(v0, v1, v2, v1)
  val v4 = 0.0015662607
  val v5 = HenonL.ar(v0, v4, v4, v4, v4)
  val v6 = Phasor.ar(v2, v2, v5, v3, v0)
  val v7 = Hilbert.ar(v6)
  val v8 = Schmidt.ar(v7, v2, v3)
  val sig = v8
  Limiter.ar(LeakDC.ar(sig))
}

play {
  RandSeed.ir()
  val v0 = 40.33182
  val v1 = 56.865112
  val v2 = 0.050732173
  val v3 = PanB.ar(v0, v2, v1, v1)
  val v4 = -0.0039715525
  val v5 = v3 hypot v1
  val v6 = LFNoise2.ar(v5)
  val v7 = 0.0015662607
  val v8 = Decay2.ar(v7, v1, v0)
  val v9 = HenonL.ar(v0, v6, v7, v7, v0)
  val v10 = Phasor.ar(v2, v2, v9, v8, v0)
  val sig = v10
  Limiter.ar(LeakDC.ar(sig))
}

play {
  RandSeed.ir()
  val v0 = 40.33182
  val v1 = 56.865112
  val v2 = 0.050732173
  val v3 = PanB.ar(v0, v1, v2, v1)
  val v4 = 0.0015662607
  val v5 = Decay2.ar(v4, v3, v0)
  val v6 = HenonL.ar(v0, v4, v4, v4, v4)
  val v7 = Phasor.ar(v2, v2, v6, v5, v0)
  val v8 = Hilbert.ar(v7)
  val v9 = Schmidt.ar(v8, v2, v3)
  val sig = v9
  Limiter.ar(LeakDC.ar(sig))
}

play {
  RandSeed.ir()
  val v0 = 40.33182
  val v1 = -0.012392652
  val v2 = 56.865112
  val v3 = RLPF.ar(v0, v1, v2)
  val v4 = 0.0015662607
  val v5 = Decay2.ar(v4, v2, v0)
  val v6 = v3 excess v5
  val v7 = -0.43094492
  val v8 = PanB2.ar(v2, v3, v7)
  val v9 = A2K.kr(v6)
  val roots = Vector(v9, v8)
  val sig = Mix(roots)
  Limiter.ar(LeakDC.ar(sig))
}

play {
  RandSeed.ir()
  val v0 = 40.33182
  val v1 = 56.865112
  val v2 = 0.050732173
  val v3 = PanB.ar(v0, v1, v0, v1)
  val v4 = Hilbert.ar(v3)
  val v5 = LFNoise2.ar(v4)
  val v6 = 0.0015662607
  val v7 = Decay2.ar(v1, v0, v0)
  val v8 = HenonL.ar(v0, v5, v6, v7, v0)
  val v9 = Phasor.ar(v2, v2, v8, v7, v0)
  val sig = v9
  Limiter.ar(LeakDC.ar(sig))
}

play {
  RandSeed.ir()
  val v0 = 40.33182
  val v1 = -0.012392652
  val v2 = 58.060352
  val v3 = 0.0015662607
  val v4 = Decay2.ar(v3, v2, v0)
  val v5 = 28.554491
  val v6 = LatoocarfianL.ar(v2, v1, v0, v4, v3, v2, v5)
  val v7 = Hilbert.ar(v6)
  val v8 = GbmanN.ar(v0, v6, v3)
  val v9 = GrayNoise.ar(v4)
  val v10 = FBSineL.ar(v8, v8, v9, v6, v5, v4, v5)
  val v11 = A2K.kr(v7)
  val roots = Vector(v11, v10)
  val sig = Mix(roots)
  Limiter.ar(LeakDC.ar(sig))
}

play {
  RandSeed.ir()
  val v0 = 40.33182
  val v1 = -0.012392652
  val v2 = 58.060352
  val v3 = RLPF.ar(v2, v1, v0)
  val v4 = Hilbert.ar(v3)
  val v5 = -71.114174
  val v6 = GbmanN.ar(v0, v3, v5)
  val v7 = Decay2.ar(v5, v2, v0)
  val v8 = GrayNoise.ar(v7)
  val v9 = 28.554491
  val v10 = FBSineL.ar(v6, v6, v8, v3, v9, v7, v9)
  val v11 = A2K.kr(v4)
  val roots = Vector(v11, v10)
  val sig = Mix(roots)
  Limiter.ar(LeakDC.ar(sig))
}

play {
  RandSeed.ir()
  val v0 = 61.117977
  val v1 = 0.0017354691
  val v2 = 40.33182
  val v3 = Decay2.ar(v1, v2, v0)
  val v4 = GrayNoise.ar(v3)
  val v5 = v0 - v4
  val v6 = BiPanB2.ar(v3, v2, v5, v1)
  val v7 = LatoocarfianL.ar(v1, v5, v3, v4, v4, v5, v3)
  val v8 = TWindex.ar(v7, v3, v3)
  val v9 = v2 clip2 v3
  val v10 = 10.87044
  val v11 = GbmanN.ar(v10, v4, v10)
  val v12 = Ball.ar(v4, v0, v4, v10)
  val roots = Vector(v12, v11, v9, v8, v6)
  val sig = Mix(roots)
  Limiter.ar(LeakDC.ar(sig))
}

play {
  RandSeed.ir()
  val v0 = 62.67205
  val v1 = 61.117977
  val v2 = GrayNoise.ar(v1)
  val v3 = v2 - v0
  val v4 = 0.0017354691
  val v5 = 0.24980739
  val v6 = PeakFollower.ar(v4, v5)
  val v7 = PeakFollower.ar(v5, v4)
  val v8 = 40.33182
  val v9 = Lag.ar(v3, v8)
  val v10 = 10.87044
  val v11 = Ball.ar(v1, v2, v3, v10)
  val v12 = Normalizer.ar(v9, v8, v11)
  val v13 = PanB.ar(v4, v11, v1, v8)
  val v14 = v13 < v7
  val v15 = Decay2.ar(v4, v8, v1)
  val v16 = -0.012458533
  val v17 = v16 roundUpTo v15
  val v18 = BiPanB2.ar(v8, v15, v3, v4)
  val v19 = 3.2413244E-4
  val v20 = PanB2.ar(v3, v2, v19)
  val v21 = RLPF.ar(v13, v5, v2)
  val v22 = Decay.ar(v21, v2)
  val v23 = 767.5149
  val v24 = TWindex.ar(v23, v22, v15)
  val v25 = Peak.ar(v6, v11)
  val roots = Vector(v25, v24, v20, v18, v17, v14, v12)
  val sig = Mix(roots)
  Limiter.ar(LeakDC.ar(sig))
}

play {
  RandSeed.ir()
  val v0 = 61.117977
  val v1 = 386.08374
  val v2 = 0.0017640435
  val v3 = Decay2.ar(v2, v1, v0)
  val v4 = GrayNoise.ar(v3)
  val v5 = v1 clip2 v3
  val v6 = LagUD.ar(v0, v4, v5)
  val v7 = 10.87044
  val v8 = Ball.ar(v4, v0, v6, v7)
  val v9 = 0.01438544
  val v10 = AllpassL.ar(v8, v2, v5, v9)
  val v11 = v2 absdif v8
  val v12 = PanB.ar(v1, v8, v0, v2)
  val v13 = BiPanB2.ar(v1, v3, v6, v2)
  val v14 = -0.0016412772
  val v15 = LinRand(v6, v5, v14)
  val v16 = BRZ2.ar(v15)
  val v17 = PeakFollower.ar(v2, v5)
  val v18 = 0.0069608754
  val v19 = PeakFollower.ar(v18, v2)
  val v20 = LatoocarfianL.ar(v3, v6, v10, v4, v10, v6, v4)
  val v21 = TWindex.ar(v20, v3, v3)
  val roots = Vector(v21, v19, v17, v16, v13, v12, v11)
  val sig = Mix(roots)
  Limiter.ar(LeakDC.ar(sig))
}

play {
  RandSeed.ir()
  val v0 = 61.117977
  val v1 = 0.0017354691
  val v2 = 40.33182
  val v3 = Decay2.ar(v1, v2, v0)
  val v4 = GrayNoise.ar(v3)
  val v5 = v0 - v4
  val v6 = 10.679743
  val v7 = Ball.ar(v4, v0, v5, v6)
  val v8 = 1.3225549E-4
  val v9 = AllpassL.ar(v7, v1, v3, v8)
  val v10 = LatoocarfianL.ar(v3, v9, v9, v4, v5, v5, v4)
  val v11 = BiPanB2.ar(v2, v3, v5, v1)
  val v12 = PanB.ar(v10, v7, v1, v2)
  val v13 = BRZ2.ar(v12)
  val v14 = 0.24980739
  val v15 = PeakFollower.ar(v14, v1)
  val v16 = TWindex.ar(v10, v1, v3)
  val v17 = 444.81253
  val v18 = Balance2.ar(v15, v17, v1, v1)
  val v19 = v18 clip2 v3
  val v20 = PeakFollower.ar(v1, v6)
  val roots = Vector(v20, v19, v16, v13, v11)
  val sig = Mix(roots)
  Limiter.ar(LeakDC.ar(sig))
}

play {
  RandSeed.ir()
  val v0 = 40.33182
  val v1 = 61.117977
  val v2 = 0.0017354691
  val v3 = Decay2.ar(v2, v0, v1)
  val v4 = GrayNoise.ar(v3)
  val v5 = v1 - v4
  val v6 = 0.01438544
  val v7 = AllpassL.ar(v0, v2, v3, v6)
  val v8 = -53.342194
  val v9 = LatoocarfianL.ar(v3, v5, v8, v4, v7, v5, v4)
  val v10 = -0.05045082
  val v11 = FreeVerb.ar(v9, v3, v3, v10)
  val v12 = BiPanB2.ar(v0, v3, v5, v2)
  val v13 = v0 clip2 v3
  val v14 = PeakFollower.ar(v2, v13)
  val v15 = PanB.ar(v2, v2, v1, v0)
  val v16 = Lag.ar(v13, v5)
  val v17 = BRZ2.ar(v16)
  val v18 = 6.0194114E-4
  val v19 = v16 roundTo v18
  val v20 = 0.02018721
  val v21 = LFCub.ar(v18, v20)
  val roots = Vector(v21, v19, v17, v15, v14, v12, v11)
  val sig = Mix(roots)
  Limiter.ar(LeakDC.ar(sig))
}

play {
  RandSeed.ir()
  val v0 = 0.0017354691
  val v1 = 0.24980739
  val v2 = PeakFollower.ar(v0, v1)
  val v3 = PeakFollower.ar(v1, v0)
  val v4 = 40.33182
  val v5 = 61.117977
  val v6 = GrayNoise.ar(v5)
  val v7 = 62.67205
  val v8 = v7 - v6
  val v9 = 10.87044
  val v10 = Ball.ar(v5, v6, v8, v9)
  val v11 = PanB.ar(v0, v10, v5, v4)
  val v12 = v3 < v11
  val v13 = Lag.ar(v8, v4)
  val v14 = BRZ2.ar(v13)
  val v15 = 3.2413244E-4
  val v16 = PanB2.ar(v8, v6, v15)
  val v17 = RLPF.ar(v11, v1, v6)
  val v18 = Decay.ar(v17, v6)
  val v19 = Decay2.ar(v0, v4, v5)
  val v20 = 767.5149
  val v21 = TWindex.ar(v20, v18, v19)
  val v22 = Peak.ar(v2, v10)
  val v23 = -0.012458533
  val v24 = v23 roundUpTo v19
  val v25 = BiPanB2.ar(v4, v8, v19, v0)
  val roots = Vector(v25, v24, v22, v21, v16, v14, v12)
  val sig = Mix(roots)
  Limiter.ar(LeakDC.ar(sig))
}

play {
  RandSeed.ir()
  val v0 = 40.33182
  val v1 = 61.117977
  val v2 = 0.0017354691
  val v3 = Decay2.ar(v2, v0, v1)
  val v4 = GrayNoise.ar(v0)
  val v5 = v1 - v4
  val v6 = BiPanB2.ar(v0, v3, v5, v2)
  val v7 = 10.87044
  val v8 = Ball.ar(v4, v1, v5, v7)
  val v9 = 201.34566
  val v10 = v9 clip2 v3
  val v11 = Lag.ar(v5, v10)
  val v12 = BRZ2.ar(v11)
  val v13 = PeakFollower.ar(v12, v2)
  val v14 = 0.0069608754
  val v15 = -0.0016412772
  val v16 = PeakFollower.ar(v14, v15)
  val v17 = TWindex.ar(v15, v3, v3)
  val v18 = Impulse.ar(v14, v9)
  val v19 = 0.01454035
  val v20 = T2A.ar(v19)
  val roots = Vector(v20, v18, v17, v16, v13, v8, v6)
  val sig = Mix(roots)
  Limiter.ar(LeakDC.ar(sig))
}
