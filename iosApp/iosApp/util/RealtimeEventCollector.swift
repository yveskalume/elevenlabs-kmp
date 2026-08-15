//
//  RealtimeEventCollector.swift
//  iosApp
//
//  Created by Yves Kalume on 15/08/2026.
//


import ElevenLabs
import SwiftUI

final class RealtimeEventCollector: NSObject, Kotlinx_coroutines_coreFlowCollector, @unchecked Sendable {
    private let onAudio: @MainActor (KotlinByteArray) throws -> Void
    private let onFinished: @MainActor () -> Void

    init(
        onAudio: @escaping @MainActor (KotlinByteArray) throws -> Void,
        onFinished: @escaping @MainActor () -> Void
    ) {
        self.onAudio = onAudio
        self.onFinished = onFinished
    }

    func emit(value: Any?, completionHandler: @escaping (Error?) -> Void) {
        Task { @MainActor in
            do {
                switch value {
                case let event as RealtimeTtsEventAudio:
                    try onAudio(event.bytes)
                case is RealtimeTtsEventFinished:
                    onFinished()
                default:
                    throw AudioStreamError.invalidChunk
                }
                completionHandler(nil)
            } catch {
                completionHandler(error)
            }
        }
    }
}
