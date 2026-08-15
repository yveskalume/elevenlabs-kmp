import AVFoundation
import ElevenLabs

@MainActor
final class MicrophoneRecorder {
    private let engine = AVAudioEngine()
    private var converter: AVAudioConverter?
    private var tapInstalled = false

    func requestPermission() async -> Bool {
        await withCheckedContinuation { continuation in
            AVAudioApplication.requestRecordPermission { granted in
                continuation.resume(returning: granted)
            }
        }
    }

    func start(
        sampleRate: Double = 16_000,
        onChunk: @escaping @Sendable (Data) -> Void
    ) throws {
        stop()

        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.record, mode: .measurement)
        try session.setPreferredSampleRate(sampleRate)
        try session.setActive(true)

        let input = engine.inputNode
        let inputFormat = input.outputFormat(forBus: 0)
        guard inputFormat.sampleRate > 0, inputFormat.channelCount > 0 else {
            throw IOSMicrophoneRecorderError.unavailableInput
        }
        guard let outputFormat = AVAudioFormat(
            commonFormat: .pcmFormatInt16,
            sampleRate: sampleRate,
            channels: 1,
            interleaved: true
        ), let converter = AVAudioConverter(from: inputFormat, to: outputFormat) else {
            throw IOSMicrophoneRecorderError.invalidFormat
        }
        self.converter = converter

        input.installTap(onBus: 0, bufferSize: 4_096, format: inputFormat) { buffer, _ in
            let ratio = outputFormat.sampleRate / inputFormat.sampleRate
            let capacity = AVAudioFrameCount(ceil(Double(buffer.frameLength) * ratio))
            guard let converted = AVAudioPCMBuffer(
                pcmFormat: outputFormat,
                frameCapacity: max(capacity, 1)
            ) else { return }

            var suppliedInput = false
            var conversionError: NSError?
            let status = converter.convert(to: converted, error: &conversionError) { _, inputStatus in
                if suppliedInput {
                    inputStatus.pointee = .noDataNow
                    return nil
                }
                suppliedInput = true
                inputStatus.pointee = .haveData
                return buffer
            }
            guard status != .error, conversionError == nil, converted.frameLength > 0 else {
                return
            }

            let audioBuffer = converted.audioBufferList.pointee.mBuffers
            guard let bytes = audioBuffer.mData else { return }
            onChunk(Data(bytes: bytes, count: Int(audioBuffer.mDataByteSize)))
        }
        tapInstalled = true

        engine.prepare()
        try engine.start()
    }

    func stop() {
        if tapInstalled {
            engine.inputNode.removeTap(onBus: 0)
            tapInstalled = false
        }
        engine.stop()
        engine.reset()
        converter = nil
        try? AVAudioSession.sharedInstance().setActive(
            false,
            options: .notifyOthersOnDeactivation
        )
    }
}

extension Data {
    func kotlinByteArray() -> KotlinByteArray {
        let result = KotlinByteArray(size: Int32(count))
        withUnsafeBytes { rawBuffer in
            guard let bytes = rawBuffer.bindMemory(to: UInt8.self).baseAddress else { return }
            for index in indices {
                result.set(index: Int32(index), value: Int8(bitPattern: bytes[index]))
            }
        }
        return result
    }
}

private enum IOSMicrophoneRecorderError: LocalizedError {
    case unavailableInput
    case invalidFormat

    var errorDescription: String? {
        switch self {
        case .unavailableInput:
            return "No microphone input is available."
        case .invalidFormat:
            return "iOS could not create the 16 kHz microphone format."
        }
    }
}
