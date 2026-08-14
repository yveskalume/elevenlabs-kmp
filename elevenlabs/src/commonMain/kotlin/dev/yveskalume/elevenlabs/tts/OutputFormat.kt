package dev.yveskalume.elevenlabs.tts

data class OutputFormat(val value: String) {
    init {
        require(value.isNotBlank()) { "The output format cannot be blank." }
    }

    companion object {
        val Mp3_44100_128: OutputFormat = OutputFormat("mp3_44100_128")
        val Mp3_44100_192: OutputFormat = OutputFormat("mp3_44100_192")
        val Pcm_16000: OutputFormat = OutputFormat("pcm_16000")
        val Pcm_24000: OutputFormat = OutputFormat("pcm_24000")
        val Pcm_44100: OutputFormat = OutputFormat("pcm_44100")
        val Wav_44100: OutputFormat = OutputFormat("wav_44100")
        val Ulaw_8000: OutputFormat = OutputFormat("ulaw_8000")
    }
}