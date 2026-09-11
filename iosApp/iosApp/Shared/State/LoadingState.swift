enum LoadingState<Content> {
    case loading
    case error(message: String)
    case idle(data: Content)

    var dataOrNil: Content? {
        switch self {
        case let .idle(data):
            data
        case .loading, .error:
            nil
        }
    }
}
