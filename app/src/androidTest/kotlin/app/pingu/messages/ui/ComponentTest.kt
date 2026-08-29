package app.pingu.messages.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.pingu.messages.domain.model.Conversation
import app.pingu.messages.domain.model.MessageStatus
import app.pingu.messages.domain.model.Recipient
import app.pingu.messages.ui.components.ConversationListItem
import app.pingu.messages.ui.components.EmptyState
import app.pingu.messages.ui.theme.PinguTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Component-level UI tests.
 *
 * These check the parts of the interface that carry meaning rather than decoration: that a
 * conversation row shows the right name and preview, that a draft is labelled as one, and that an
 * empty state offers its action.
 */
@RunWith(AndroidJUnit4::class)
class ComponentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val conversation = Conversation(
        threadId = 1L,
        recipients = listOf(Recipient("+447700900123", displayName = "Ada Lovelace")),
        snippet = "See you at six",
        lastMessageTimestamp = System.currentTimeMillis(),
        snippetStatus = MessageStatus.DELIVERED,
    )

    @Test
    fun conversationRowShowsTheContactNameAndPreview() {
        composeRule.setContent {
            PinguTheme {
                ConversationListItem(
                    conversation = conversation,
                    selected = false,
                    selectionMode = false,
                    onClick = {},
                    onLongClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Ada Lovelace").assertIsDisplayed()
        composeRule.onNodeWithText("See you at six").assertIsDisplayed()
    }

    @Test
    fun conversationRowLabelsAnUnsentDraft() {
        composeRule.setContent {
            PinguTheme {
                ConversationListItem(
                    conversation = conversation.copy(draftText = "half written"),
                    selected = false,
                    selectionMode = false,
                    onClick = {},
                    onLongClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Draft: half written").assertIsDisplayed()
    }

    @Test
    fun conversationRowReportsTaps() {
        var clicked = false
        composeRule.setContent {
            PinguTheme {
                ConversationListItem(
                    conversation = conversation,
                    selected = false,
                    selectionMode = false,
                    onClick = { clicked = true },
                    onLongClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Ada Lovelace").performClick()
        assertThat(clicked).isTrue()
    }

    @Test
    fun emptyStateOffersItsAction() {
        var started = false
        composeRule.setContent {
            PinguTheme {
                EmptyState(
                    icon = Icons.Outlined.Inbox,
                    title = "No messages yet",
                    body = "Conversations show up here.",
                    actionLabel = "Start a conversation",
                    onAction = { started = true },
                )
            }
        }

        composeRule.onNodeWithText("No messages yet").assertIsDisplayed()
        composeRule.onNodeWithText("Start a conversation").performClick()
        assertThat(started).isTrue()
    }
}
