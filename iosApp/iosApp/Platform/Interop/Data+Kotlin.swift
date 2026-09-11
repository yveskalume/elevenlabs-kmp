import ElevenLabs
import Foundation

extension Data {
    init(kotlinBytes bytes: KotlinByteArray) {
        var buffer = [UInt8](repeating: 0, count: Int(bytes.size))
        for index in 0..<bytes.size {
            buffer[Int(index)] = UInt8(bitPattern: bytes.get(index: index))
        }
        self.init(buffer)
    }

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
