//
//  AudioStreamError.swift
//  iosApp
//
//  Created by Yves Kalume on 15/08/2026.
//


import ElevenLabs
import SwiftUI

enum AudioStreamError: LocalizedError {
    case invalidChunk

    var errorDescription: String? {
        "The audio stream returned an unexpected value."
    }
}
