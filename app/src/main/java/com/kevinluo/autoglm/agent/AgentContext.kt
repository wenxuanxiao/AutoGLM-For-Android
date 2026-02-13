package com.kevinluo.autoglm.agent

import com.kevinluo.autoglm.model.ChatMessage

/**
 * Manages the conversation context for the phone agent.
 * Handles message accumulation, image cleanup, and context reset.
 *
 */
class AgentContext(private val systemPrompt: String) {

    companion object {
        private const val TAG = "AgentContext"
        private const val MAX_HISTORY_ROUNDS = 20
    }

    private val messages: MutableList<ChatMessage> = mutableListOf()

    init {
        // Initialize with system prompt (Requirement 8.1)
        messages.add(ChatMessage.System(systemPrompt))
    }

    /**
     * Adds a user message to the context.
     * Before adding, removes images from all previous user messages to save memory (Requirement 8.3).
     *
     * @param text The text content of the user message
     * @param imageBase64 Optional base64-encoded image data
     */
    fun addUserMessage(text: String, imageBase64: String?) {
        // Remove images from previous user messages (Requirement 8.3)
        removeImagesFromHistory()

        // Add the new user message
        messages.add(ChatMessage.User(text, imageBase64))

        // Limit history to prevent context overflow
        trimHistoryIfNeeded()
    }

    /**
     * Adds an assistant message to the context (Requirement 8.2).
     *
     * @param content The content of the assistant's response
     */
    fun addAssistantMessage(content: String) {
        messages.add(ChatMessage.Assistant(content))

        // Limit history to prevent context overflow
        trimHistoryIfNeeded()
    }

    /**
     * Removes images from all previous user messages to save memory.
     * Only the most recent user message should contain an image (Requirement 8.3).
     */
    fun removeImagesFromHistory() {
        // Only clean up historical images, not the current screenshot
        for (i in 0 until messages.size - 1) {
            val message = messages[i]
            if (message is ChatMessage.User && message.imageBase64 != null) {
                messages[i] = ChatMessage.User(message.text, null)
            }
        }
    }

    /**
     * Trims history to keep only the most recent N rounds of conversation.
     * Each round consists of a user message and an assistant message.
     * Keeps the system prompt and trims oldest user-assistant pairs.
     */
    private fun trimHistoryIfNeeded() {
        // Find the index of the first User message (skip System message)
        val firstUserIndex = messages.indexOfFirst { it is ChatMessage.User }
        if (firstUserIndex == -1) return

        // Collect user and assistant message indices
        val userIndices = mutableListOf<Int>()
        val assistantIndices = mutableListOf<Int>()

        for (i in firstUserIndex until messages.size) {
            when (messages[i]) {
                is ChatMessage.User -> userIndices.add(i)
                is ChatMessage.Assistant -> assistantIndices.add(i)
                else -> {}
            }
        }

        val totalRounds = minOf(userIndices.size, assistantIndices.size)

        if (totalRounds > MAX_HISTORY_ROUNDS) {
            val roundsToRemove = totalRounds - MAX_HISTORY_ROUNDS

            // Remove oldest user-assistant pairs
            val indicesToRemove = mutableListOf<Int>()
            for (i in 0 until roundsToRemove) {
                if (i < userIndices.size) indicesToRemove.add(userIndices[i])
                if (i < assistantIndices.size) indicesToRemove.add(assistantIndices[i])
            }

            // Remove in reverse order to maintain indices
            indicesToRemove.sortedDescending().forEach { index ->
                messages.removeAt(index)
            }
        }
    }

    /**
     * Gets a snapshot of messages for sending to the model.
     * Ensures no images are included in the request.
     *
     * @return List of all chat messages in order, without images
     */
    fun getMessagesForRequest(): List<ChatMessage> {
        // Clean up historical images, keep current screenshot
        removeImagesFromHistory()
        return messages.toList()
    }

    /**
     * Returns a copy of all messages in the context.
     *
     * @return List of all chat messages in order
     */
    fun getMessages(): List<ChatMessage> {
        return messages.toList()
    }

    /**
     * Returns the number of messages in the context.
     *
     * @return The message count
     */
    fun getMessageCount(): Int {
        return messages.size
    }

    /**
     * Resets the context for a new task (Requirement 8.4).
     * Clears all messages and re-initializes with the system prompt.
     */
    fun reset() {
        messages.clear()
        messages.add(ChatMessage.System(systemPrompt))
    }

    /**
     * Checks if the context has been initialized (has at least the system prompt).
     *
     * @return true if initialized, false otherwise
     */
    fun isInitialized(): Boolean {
        return messages.isNotEmpty() && messages.first() is ChatMessage.System
    }

    /**
     * Gets the system prompt used to initialize this context.
     *
     * @return The system prompt string
     */
    fun getSystemPrompt(): String {
        return systemPrompt
    }

    /**
     * Checks if the context is empty (only contains system prompt).
     *
 true if only system     * @return prompt exists, false otherwise
     */
    fun isEmpty(): Boolean {
        return messages.size == 1 && messages.first() is ChatMessage.System
    }

    /**
     * Gets the last message in the context, if any.
     *
     * @return The last ChatMessage or null if empty
     */
    fun getLastMessage(): ChatMessage? {
        return messages.lastOrNull()
    }

    /**
     * Removes the last user message from the context.
     * Used when a step needs to be retried (e.g., after pause).
     *
     * @return true if a user message was removed, false if no user message found
     */
    fun removeLastUserMessage(): Boolean {
        for (i in messages.indices.reversed()) {
            if (messages[i] is ChatMessage.User) {
                messages.removeAt(i)
                return true
            }
        }
        return false
    }

    /**
     * Removes the last assistant message from the context.
     * Used when a step needs to be retried (e.g., after pause).
     *
     * @return true if an assistant message was removed, false if no assistant message found
     */
    fun removeLastAssistantMessage(): Boolean {
        for (i in messages.indices.reversed()) {
            if (messages[i] is ChatMessage.Assistant) {
                messages.removeAt(i)
                return true
            }
        }
        return false
    }

    /**
     * Counts the number of assistant messages (completed steps).
     *
     * @return The number of assistant messages
     */
    fun getAssistantMessageCount(): Int {
        return messages.count { it is ChatMessage.Assistant }
    }

    /**
     * Counts the number of user messages.
     *
     * @return The number of user messages
     */
    fun getUserMessageCount(): Int {
        return messages.count { it is ChatMessage.User }
    }
}
