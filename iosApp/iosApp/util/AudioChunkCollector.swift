//
//  AudioChunkCollector.swift
//  iosApp
//
//  Created by Yves Kalume on 15/08/2026.
//


import ElevenLabs
import SwiftUI

final class AudioChunkCollector: NSObject, Kotlinx_coroutines_coreFlowCollector, @unchecked Sendable {
    private(set) var data = Data()

    func emit(value: Any?, completionHandler: @escaping (Error?) -> Void) {
        guard let chunk = value as? AudioChunk else {
            completionHandler(AudioStreamError.invalidChunk)
            return
        }

        data.append(Data(kotlinBytes: chunk.bytes))
        completionHandler(nil)
    }
}