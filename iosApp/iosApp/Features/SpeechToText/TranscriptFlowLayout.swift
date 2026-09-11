import SwiftUI

struct TranscriptFlowLayout: Layout {
    let horizontalSpacing: Double
    let verticalSpacing: Double

    func sizeThatFits(
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) -> CGSize {
        positions(proposal: proposal, subviews: subviews).size
    }

    func placeSubviews(
        in bounds: CGRect,
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) {
        let layout = positions(
            proposal: ProposedViewSize(width: bounds.width, height: proposal.height),
            subviews: subviews
        )
        for (index, point) in layout.points.enumerated() {
            subviews[index].place(
                at: CGPoint(x: bounds.minX + point.x, y: bounds.minY + point.y),
                anchor: .topLeading,
                proposal: .unspecified
            )
        }
    }

    private func positions(
        proposal: ProposedViewSize,
        subviews: Subviews
    ) -> (size: CGSize, points: [CGPoint]) {
        let availableWidth = proposal.width ?? .infinity
        var points: [CGPoint] = []
        var x = 0.0
        var y = 0.0
        var rowHeight = 0.0
        var contentWidth = 0.0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x > 0, x + size.width > availableWidth {
                x = 0
                y += rowHeight + verticalSpacing
                rowHeight = 0
            }
            points.append(CGPoint(x: x, y: y))
            x += size.width + horizontalSpacing
            rowHeight = max(rowHeight, size.height)
            contentWidth = max(contentWidth, x - horizontalSpacing)
        }

        return (
            CGSize(
                width: proposal.width ?? contentWidth,
                height: subviews.isEmpty ? 0 : y + rowHeight
            ),
            points
        )
    }
}
