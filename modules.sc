/*
 * Freeze! modules
 * Composition: Jason Noble (2021)
 * Puara: Gestural controllers and programming: Edu Meneses (2022-2023)
 *
 * Dependency: vbUGens (plugin), from Volker Böhm (https://github.com/v7b1/vb_UGens)
 * Check placement with Platform.systemExtensionDir
 *
 *
 * OSC commands:
 *
 * NAMESPACE:           ARGUMENT_1          ARGUMENT_2                 ARGUMENT_3 (optional)   ARGUMENT_4 (optional)
 * /freeze/panic
 * /freeze/store        input(i)            bufferNumber(i)
 * /freeze/play         bufferNumber (i)
 * /freeze/stop         bufferNumber (i)
 * /freeze/stopAll
 * /freeze/erase        bufferNumber (i)
 * /freeze/eraseAll
 * /freeze/now          input(i)            bufferNumber(i)
 * /freeze/pitch        bufferNumber (i)    shift (f, 0--4)
 * /freeze/pointer      bufferNumber (i)    pointer_pos (f, 0--1)
 * /harmonizer/play     bufferNumber (i)    number_of_harmonics (i)    low_harm (i)            high_harm (i)
 * /harmonizer/stop     bufferNumber (i)
 * /harmonizer/stopAll
 */

/////////////////////////////////////////
//////// Boot and load SynthDefs ////////
/////////////////////////////////////////

(
s = Server.local;
~number_of_buffers = 4;
~buffer_duration = 0.5; // in seconds

s.waitForBoot({
	s.meter;
	s.plotTree;

	// ALLOCATE BUFFERS
	// load a soundfile into a buffer and create a buffer that will hold the spectral data
	~buffers = Dictionary.new;
	~player = List.newClear(~number_of_buffers);
	~harmonizer = List.newClear(~number_of_buffers);
	~harmonizer_players = List.new!~number_of_buffers;
	~number_of_buffers.do({ | item |
		var audioBuf;
		audioBuf = Buffer.alloc(s, s.sampleRate * ~buffer_duration, 1);
		~buffers.add(("audio"++"_"++item).asSymbol -> audioBuf);
		~buffers.add(("spectral"++"_"++item).asSymbol -> VBPVoc.createBuffer(s, 2048, ~buffers[("audio"++"_"++item).asSymbol]));
	});

	// PITCH SHIFTER TIME DOMAIN
	SynthDef(\pitchShiftTime, { | input = 0, output = 0, gate = 0, volume = 1, windowSize = 0.2, pitchRatio = 1, pitchDispersion = 0, timeDispersion = 0 |
		var signal;
		signal = PitchShift.ar(In.ar(input), windowSize, pitchRatio, pitchDispersion, timeDispersion, volume);
		Out.ar(output, EnvGen.kr(Env.asr(0.5,0.7,1), gate) * signal);
	}).add;

	// GRANULATOR
	SynthDef(\granulator, { | input = 0, output = 0, gate = 0, volume = 1, channels = 1, freq = 10, dur_ms = 10, pan = 0 |
		var signal;
		signal = GrainIn.ar(1, Impulse.kr(freq), (dur_ms/1000), In.ar(input), pan, mul: volume);
		Out.ar(output, EnvGen.kr(Env.asr(0.5,0.7,1), gate) * signal);
	}).add;

	SynthDef(\granulator, { | sndbuf, output = 0, gate = 1, volume = 1 |
		var env, freqdev;
		env = EnvGen.kr(
			Env([0, 1, 0], [1, 1], \sin, 1),
			gate,
			levelScale: volume,
			doneAction: Done.freeSelf);
		Out.ar(output,
			GrainBuf.ar(1, Impulse.kr(10), 0.1, sndbuf, LFNoise1.kr.range(0.5, 2),
				LFNoise2.kr(0.1).range(0, 1), 2, 0, -1) * env)
	}).add;

	// (CHEAP) PITCH RECOGNITION FOR HARMONIZER
	SynthDef(\pitchRecognition,{ | input=0, output=0 |
		var signal;
		signal = In.ar(input);
		Out.kr(output, Pitch.kr(signal));
	}).add;

	// BAND-PASS FILTER
	SynthDef(\bandpass, { | input = 0, output = 0, freq = 440, bw = 1.0, volume = 1, gate = 0 |
		var signal;
		signal = BBandPass.ar(In.ar(input), freq, bw, volume);
		Out.ar(output, EnvGen.kr(Env.asr(0.5,0.7,1), gate) * signal);
	}).add;

	// ANALYSE AUDIO BUFFER AFTER RECORDING
	OSCdef(\analyseFFT, { | msg |
		var id = msg[3].asInteger;
		"Analysing buffer ".post; id.post; "or number msg ".post; msg.postln;
		~buffers[("spectral"++"_"++id).asSymbol].pvocAnal(~buffers[("audio"++"_"++id).asSymbol], 2048);
	}, '/analyseFFT');

	// RECORD
	SynthDef(\record, { | in, duration, audioBuffer, bufferID |
		var timer, recorder, sender;
		timer = Line.kr(0,1, duration);
		RecordBuf.ar(SoundIn.ar(in), audioBuffer, loop: 0);
		sender = SendReply.kr(Done.kr(timer), '/analyseFFT', [bufferID]);
		FreeSelf.kr(Done.kr(timer));
	}).add;

	// RECORD BUS
	SynthDef(\recordBus, { | in, duration, audioBuffer, bufferID |
		var timer, recorder, sender;
		timer = Line.kr(0,1, duration);
		RecordBuf.ar(In.ar(in), audioBuffer, loop: 0);
		sender = SendReply.kr(Done.kr(timer), '/analyseFFT', [bufferID]);
		FreeSelf.kr(Done.kr(timer));
	}).add;

	// PLAYBACK
	SynthDef(\playback, {| out = 0, buffer = 0, volume = 1 |
		Out.ar(out, PlayBuf.ar(1, buffer, BufRateScale.kr(buffer), doneAction: Done.freeSelf) * volume);
	}).add;

	// DUAL PLAYBACK
	SynthDef(\dualPlayback, {| out1 = 0, out2 = 0, buffer = 0, volume1 = 1, volume2 = 1 |
		Out.ar(out1, PlayBuf.ar(1, buffer, BufRateScale.kr(buffer), doneAction: Done.freeSelf) * volume1);
		Out.ar(out2, PlayBuf.ar(1, buffer, BufRateScale.kr(buffer), doneAction: Done.freeSelf) * volume2);
	}).add;

	//SPECTRAL PLAYBACK
	SynthDef(\sPlayback, {| out = 0, buffer = 0, pos = 0.5, volume = 1, windowSize = 0.2, pitch = 1, harm_relationship = 1, pitchDispersion = 0, timeDispersion = 0 |
		var buffer_reading, signal;
		buffer_reading = VBPVoc.ar(1, buffer, pos);
		signal = PitchShift.ar(buffer_reading, windowSize, (pitch * harm_relationship), pitchDispersion, timeDispersion, volume);
		Out.ar(out, EnvGen.kr(Env.asr(0.5,0.7,1), volume) * signal);
	}).add;

	// MASTER OUTPUT
	SynthDef(\masterOut, { | input = 0, output = 0, gate = 0, volume = 1 |
		var signal;
		signal = In.ar(input);
		Out.ar(output, EnvGen.kr(Env.asr(0.5,0.7,1), gate) * signal * volume);
	}).add;

/////////////////////////////////////////
///////////////// Inputs ////////////////
/////////////////////////////////////////

	// PANIC! (stop all synths)
	OSCdef(\panic, { | msg |
		s.freeAll;
		~player.do({ | item, i | ~player.put(i, nil) });
		~harmonizer.do({ | item, i | ~harmonizer.put(i, nil) });
		~harmonizer_players.do({ | item, i | ~harmonizer_players[i].clear});
		"\nPANIC ACTIVATED!\n".postln;
	}, '/freeze/panic');

	// FREEZE SOUND
	OSCdef(\freeze, { | msg | // arguments: input (int), bufferNumber (int)
		var input, bufferNumber, bufferName;
		input = msg[1];
		bufferNumber = msg[2];
		if ((input >= 0) &&
			(input < s.options.numInputBusChannels) &&
			(bufferNumber >= 0) &&
			(bufferNumber <= ~number_of_buffers),
			{
				bufferName = ("audio"++"_"++bufferNumber).asSymbol;
				Synth.new(\record, [\in, input, \duration, ~buffer_duration, \audioBuffer, ~buffers[bufferName.asSymbol], \bufferID, bufferNumber]);
			},{
				"Cannot freeze the sound. Please check arguments.".postln;
			}
		);
	}, '/freeze/store');

	// PLAY FROZEN SOUND
	OSCdef(\freezePlay, { | msg | // arguments: bufferNumber (int)
		var bufferNumber, sBufferName;
		bufferNumber = msg[1];
		sBufferName = ("spectral"++"_"++bufferNumber).asSymbol;
		if (~player[bufferNumber] == nil,
			{
				~player.put(bufferNumber, Synth.new(\sPlayback, [\buffer, ~buffers[sBufferName]]));
			},{
				~player.put(bufferNumber, Synth.replace(~player[bufferNumber], \sPlayback, [\buffer, ~buffers[sBufferName]]));
		});
	}, '/freeze/play');

	// STOP PLAYING THE FROZEN SOUND
	OSCdef(\freezeStop, { | msg | // arguments: bufferNumber (int)
		var bufferNumber;
		bufferNumber = msg[1];
		if (~player[bufferNumber] != nil,
			{
				~player[bufferNumber].free;
				~player.put(bufferNumber,nil);
			},{
				"Could not found the desired buffer".postln;
		});
	}, '/freeze/stop');

	// STOP PLAYING ALL FROZEN SOUNDS
	OSCdef(\freezeStopAll, { | msg |
		~player.do({ | item, i |
			item.free;
			~player.put(i,nil);
		});
	}, '/freeze/stopAll');

	// ERASE THE FROZEN SOUND
	OSCdef(\freezeErase, { | msg | // arguments: bufferNumber (int)
		var bufferNumber, bufferName, sBufferName;
		bufferNumber = msg[1];
		sBufferName = ("spectral"++"_"++bufferNumber).asSymbol;
		bufferName = ("audio"++"_"++bufferNumber).asSymbol;
		if ( (bufferNumber >= ~number_of_buffers) || (bufferNumber < 0),
			{
				"Could not found the desired buffer".postln;
			},{
				~buffers[bufferName].zero;
				~buffers[sBufferName].zero;
		});
	}, '/freeze/erase');

	// ERASE ALL THE FROZEN SOUND
	OSCdef(\freezeEraseAll, { | msg |
		~number_of_buffers.do({ | item |
			~buffers[("audio"++"_"++item).asSymbol].zero;
			~buffers[("spectral"++"_"++item).asSymbol].zero;
		});
		~player.do({ | item, i |
			item.free;
			~player.put(i,nil);
		});
	}, '/freeze/eraseAll');

	// FREEZE SOUND AND PLAY
	OSCdef(\freezeNow, { | msg | // arguments: input (int), bufferNumber (int)
		var input, bufferNumber, bufferName, sBufferName;
		input = msg[1];
		bufferNumber = msg[2];
		if ((input >= 0) &&
			(input < s.options.numInputBusChannels) &&
			(bufferNumber >= 0) &&
			(bufferNumber <= ~number_of_buffers),
			{
				bufferName = ("audio"++"_"++bufferNumber).asSymbol;
				sBufferName = ("spectral"++"_"++bufferNumber).asSymbol;
				Synth.new(\record, [\in, input, \duration, ~buffer_duration, \audioBuffer, ~buffers[bufferName.asSymbol], \bufferID, bufferNumber]);
				if (~player[bufferNumber] == nil,
					{
						~player.put(bufferNumber, Synth.new(\sPlayback, [\buffer, ~buffers[sBufferName]]));
					},{
						~player.put(bufferNumber, Synth.replace(~player[bufferNumber], \sPlayback, [\buffer, ~buffers[sBufferName]]));
					}
				);
			},{
				"Cannot freeze the sound. Please check arguments.".postln;
			}
		);
	}, '/freeze/now');

	// CONTROLLING PITCH SHIFT FOR FORZEN SOUNDS
	OSCdef(\pitch, { | msg | // arguments: bufferNumber (int), shift (float from 0 to 4 with 1 sig. digit, % of freq shifting)
		var bufferNumber, shift;
		bufferNumber = msg[1];
		shift = case
		    { msg[2] < 0 } { 0 }
		    { msg[2] > 4 } { 4 }
		    { msg[2].round(0.1) };
		~player[bufferNumber].set(\pitch, shift);
	}, '/freeze/pitch');

	// SCAN FROZEN FILE (MOVE POINTER/NEEDLE)
	OSCdef(\pointer, { | msg | // arguments: bufferNumber (int), pointer (float from 0 to 1)
		var bufferNumber, pointer;
		bufferNumber = msg[1];
		pointer = case
		    { msg[2] < 0 } { 0 }
		    { msg[2] > 1 } { 1 }
		    { msg[2] };
		~player[bufferNumber].set(\pos, pointer);
	}, '/freeze/pointer');

	// HARMONIZER
	OSCdef(\BHharmonizer, { | msg | // arguments: bufferNumber (int), number_of_harmonics (int), low_harm (int, optional), high_harm (int, optional)
		var bufferNumber, number_of_harmonics, source_harm, low_harm, high_harm, sBufferName, harm_relationship;
		bufferNumber = msg[1];
		number_of_harmonics = msg[2];
		if ( number_of_harmonics >= 2 , {
			if (bufferNumber <= ~number_of_buffers, {
				if ( msg[3] != nil,{low_harm = msg[3]},{low_harm = 5});
				if ( msg[4] != nil,{high_harm = msg[4]},{high_harm = 16});
				source_harm = rrand(low_harm, high_harm);
				sBufferName = ("spectral"++"_"++bufferNumber).asSymbol;
				harm_relationship = ( rrand(low_harm, high_harm) / source_harm);
				if (~harmonizer[bufferNumber] == nil,
					{
						~harmonizer.put(bufferNumber, Group.new);
						~harmonizer_players[bufferNumber].clear;
						~harmonizer_players[bufferNumber].add(Synth.new(\sPlayback, [\buffer, ~buffers[sBufferName], \volume, (1/(number_of_harmonics+1))], ~harmonizer[bufferNumber]));
						(number_of_harmonics-1).do({
							~harmonizer_players[bufferNumber].add(Synth.new(\sPlayback, [\buffer, ~buffers[sBufferName], \harm_relationship, harm_relationship, \volume, (1/(number_of_harmonics+1))], ~harmonizer[bufferNumber]));
						});
					},{
						var difference = number_of_harmonics - ~harmonizer_players[bufferNumber].size;
						case
						{difference > 0}{difference.do({~harmonizer_players[bufferNumber].add(Synth.new(\sPlayback, [\buffer, ~buffers[sBufferName], \harm_relationship, harm_relationship], ~harmonizer[bufferNumber]))})}
						{difference < 0}{difference.abs.do({ | i |
							~harmonizer_players[bufferNumber][~harmonizer_players[bufferNumber].size-i-1].free;
							~harmonizer_players[bufferNumber].pop
						})}
						{difference == 0}{}
				});
			});
		},{
			"Error: number of harmonics cannot be smaller than 2.".postln;
		});
	}, '/harmonizer/play');

	// STOP PLAYING THE HARMONIZER
	OSCdef(\BHharmonizerStop, { | msg | // arguments: bufferNumber (int)
		var bufferNumber;
		bufferNumber = msg[1];
		if (~harmonizer[bufferNumber] != nil,
			{
				~harmonizer[bufferNumber].free;
				~harmonizer.put(bufferNumber,nil);
			},{
				"Could not found the desired harmonizer".postln;
		});
	}, '/harmonizer/stop');

	// STOP PLAYING ALL HARMONIZERS
	OSCdef(\BHharmonizerStopAll, { | msg |
		~harmonizer.do({ | item, i |
			~harmonizer[i].free;
			~harmonizer.put(i,nil);
		});
	}, '/harmonizer/stopAll');

});

);

/////////////////////////////////////////
////////// Gestural controllers /////////
/////////////////////////////////////////

(

// MOUSE
	SynthDef(\mouseCtl, {
		var mouseX, mouseY, mouseClick;
		mouseX = MouseX.kr(0, 1, 0);
		mouseY = MouseY.kr(0, 1, 0);
		mouseClick = MouseButton.kr(-1, 1);
		SendReply.kr(Impulse.kr(50), '/mousePos', [mouseX, mouseY]);
		SendReply.kr(Changed.kr(mouseClick), '/mouseTrig', [mouseClick]);
	}).add;
	~mouseBus = Bus.control(s);
	~mousePosMapper = OSCFunc({ | msg |
		var x = msg[3];
		var y = msg[4];
		//x.postln;
		~mouseBus.value = x;
	}, '/mousePos');
    ~mouse = Synth.new(\mouseCtl); // Start acquiring mouse data
    //~mouseMapper.free; // Free the OSC func if needed

);
