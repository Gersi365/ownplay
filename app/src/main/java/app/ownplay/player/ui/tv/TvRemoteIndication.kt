package app.ownplay.player.ui.tv

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

internal class TvRemoteIndication(
    private val focusColor: Color,
    private val pressedColor: Color,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        TvRemoteIndicationNode(
            interactionSource = interactionSource,
            focusColor = focusColor,
            pressedColor = pressedColor,
        )

    override fun equals(other: Any?): Boolean =
        other is TvRemoteIndication &&
            other.focusColor == focusColor &&
            other.pressedColor == pressedColor

    override fun hashCode(): Int = 31 * focusColor.hashCode() + pressedColor.hashCode()
}

private class TvRemoteIndicationNode(
    private val interactionSource: InteractionSource,
    private val focusColor: Color,
    private val pressedColor: Color,
) : Modifier.Node(), DrawModifierNode {
    private var focusCount = 0
    private var pressCount = 0

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is FocusInteraction.Focus -> focusCount += 1
                    is FocusInteraction.Unfocus -> focusCount = (focusCount - 1).coerceAtLeast(0)
                    is PressInteraction.Press -> pressCount += 1
                    is PressInteraction.Release -> pressCount = (pressCount - 1).coerceAtLeast(0)
                    is PressInteraction.Cancel -> pressCount = (pressCount - 1).coerceAtLeast(0)
                }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val cornerRadius = CornerRadius(12.dp.toPx())

        if (focusCount > 0) {
            drawRoundRect(
                color = focusColor,
                alpha = 0.10f,
                cornerRadius = cornerRadius,
            )
        }

        drawContent()

        if (pressCount > 0) {
            drawRoundRect(
                color = pressedColor,
                alpha = 0.18f,
                cornerRadius = cornerRadius,
            )
        }

        if (focusCount > 0) {
            drawRoundRect(
                color = focusColor,
                alpha = 0.98f,
                cornerRadius = cornerRadius,
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}
