# Freeze!

Implementation of a spectral freeze and harmonizer algorithms for the AMIwrist

# Context

This work is based on a commission (Freeze! for drum kit and AMIwrist) made by the [Centre of Interdisciplinary Research in Music Media and Technology (CIRMMT)](https://www.cirmmt.org/en) to [Jason Noble](https://jasonnoble.ca/), a composer and professor at Moncton University. This commisision also included [Edu Meneses](https://www.edumeneses.com/) as the electronics & mapping designer and [Martin Daigle](https://www.martindrum.com/) as the performer (drums).

Freeze! for drum kit and AMIwrist, gives the drummer control over musical features outside the usual scope of the kit such as melody and harmony, effectively making him soloist, conductor, and ensemble for a mini-concerto. The title refers to spectral freeze, with which all signal processing in the piece begins, and also to psychological withdrawal, expressed here by a transition from the real time of acoustical sounds to the frozen time of a digital soundworld. 

The piece was premièred [May 26, 2022](https://www.cirmmt.org/en/events/live-cirmmt/student-commissions) at the Multimedia Room (MMR) in Montreal.

# Electronics

This piece essenialy uses a, a rever, a spectral freeze, and an inovative harmonizer to create the soundscape for the conductor role of the performer.

The spectral freeze was primariy created by Jason Noble in Max, and then ported to SuperCollider by Edu Meneses. 

We created an harmonizer based on [Bob Hasegawa](https://hasegawa.research.mcgill.ca/)'s proposition. Hasegawa proposed that complex harmonies used in new music (e.g., Schoenberg or Grisey) can be analyzed as upper partials of a hypothetical virtual fundamental. 

# Implementation

The mapping goes as follows:

Four buffers for spectral freeze (originally done with vb.freezer~, by [Volker Böhm](https://vboehm.net/))


1. 
   1. Freeze indexed to RH jab out
   2. Choke indexed to RH jab down
   3. Pitch shifter on buffer 1 indexed to RH gesture controller (central position = 0 transposition, raise arm = raise pitch, lower arm = lower pitch)
2. 
   1. Granulation w/ aleatoric pitch shift and durations, swell-shrink envelopes applied to buffer 2 
   2. Pitch shifter on granulation indexed to LH
   3. Pitch shifter on buffer 1 indexed to RH; volume also indexed, such that lowered arm = 0 volume, raising arm increases volume
   4. Jabs down for RH and LH choke their respective buffers
3. 
   1. RH Jab out freeze
   2. RH descent = fallaway gesture (automated pitch descent and decrescendo al niente)
   3. Sustained bass note = filtered, harmonized and transposed low tom
4.   
   1. Harmonizer per Hasegawa’s virtual fundamental idea (incoming pitch is assigned a harmonic rank aleatorically, harmonized with up to 12 other pitches selected from the same harmonic series, but high enough in the series that the sense of “tonality” may be obscured)
   2. Sustained bass pitch shifted aleatorically
5. 
   1. Harmonizer controlled by RH rotation = number of partials, between 1 and 12; spectrum automatically reset at 1
   2. Pitch of both RH and LH indexed to vertical position
   3. Ends with gradual thickening of texture by adding all of the buffers, then diminuendo al niente

# Licensing

The code in this project is licensed under the MIT license, unless otherwise specified within the file.