//
//  SttEventCollector.swift
//  iosApp
//
//  Created by Yves Kalume on 15/08/2026.
//


import ElevenLabs
import SwiftUI

final class SttEventCollector: NSObject, Kotlinx_coroutines_coreFlowCollector, @unchecked Sendable {
    private let onPartial: @MainActor (String) -> Void
    private let onCommitted: @MainActor (String) -> Void

    init(
        onPartial: @escaping @MainActor (String) -> Void,
        onCommitted: @escaping @MainActor (String) -> Void
    ) {
        self.onPartial = onPartial
        self.onCommitted = onCommitted
    }

    func emit(value: Any?, completionHandler: @escaping (Error?) -> Void) {
        Task { @MainActor in
            switch value {
            case let event as RealtimeSttEventPartialTranscript:
                onPartial(event.text)
            case let event as RealtimeSttEventFinalTranscript:
                onPartial(event.text)
            case let event as RealtimeSttEventCommittedTranscript:
                onCommitted(event.text)
            case is RealtimeSttEventSessionStarted, is RealtimeSttEventUnknown:
                break
            default:
                completionHandler(AudioStreamError.invalidChunk)
                return
            }
            completionHandler(nil)
        }
    }
}
