package com.kevinluo.autoglm.agent

import com.kevinluo.autoglm.model.ChatMessage
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.forAll

/**
 * Property-based tests for AgentContext.
 * 
 * **Feature: autoglm-phone-agent, Property 17: Context message accumulation**
 * **Validates: Requirements 8.2**
 */
class AgentContextPropertyTest : StringSpec({
    
    /**
     * Property 17: Context message accumulation
     * 
     * For any sequence of step completions, the AgentContext should contain all 
     * assistant responses in order, and the message count should equal the number 
     * of completed steps plus the initial system and user messages.
     * 
     * **Validates: Requirements 8.2**
     */
    "context message accumulation - message count equals steps plus initial messages" {
        // Generator for system prompts
        val systemPromptArb = Arb.string(1..100)
        
        // Generator for user message text
        val userTextArb = Arb.string(1..200)
        
        // Generator for assistant response content
        val assistantContentArb = Arb.string(1..500)
        
        // Generator for number of steps (1 to 20)
        val stepCountArb = Arb.int(1..20)
        
        forAll(100, systemPromptArb, userTextArb, assistantContentArb, stepCountArb) { 
            systemPrompt, userText, assistantContent, stepCount ->
            
            val context = AgentContext(systemPrompt, 100)
            
            // Add initial user message (task description)
            context.addUserMessage(userText, null)
            
            // Simulate step completions by adding assistant messages
            repeat(stepCount) { step ->
                context.addAssistantMessage("$assistantContent - step $step")
            }
            
            // Verify message count: 1 system + 1 user + stepCount assistant messages
            val expectedCount = 1 + 1 + stepCount
            context.getMessageCount() == expectedCount &&
            context.getAssistantMessageCount() == stepCount
        }
    }
    
    "context message accumulation - assistant messages are in order" {
        val systemPromptArb = Arb.string(1..50)
        val stepCountArb = Arb.int(1..15)
        
        forAll(100, systemPromptArb, stepCountArb) { systemPrompt, stepCount ->
            val context = AgentContext(systemPrompt, 100)
            
            // Add initial user message
            context.addUserMessage("task", null)
            
            // Add assistant messages with sequential identifiers
            repeat(stepCount) { step ->
                context.addAssistantMessage("response_$step")
            }
            
            val messages = context.getMessages()
            
            // Extract assistant messages and verify order
            val assistantMessages = messages.filterIsInstance<ChatMessage.Assistant>()
            
            assistantMessages.size == stepCount &&
            assistantMessages.mapIndexed { index, msg -> 
                msg.content == "response_$index" 
            }.all { it }
        }
    }
    
    "context message accumulation - interleaved user and assistant messages" {
        val systemPromptArb = Arb.string(1..50)
        val stepCountArb = Arb.int(1..10)
        
        forAll(100, systemPromptArb, stepCountArb) { systemPrompt, stepCount ->
            val context = AgentContext(systemPrompt, 100)
            
            // Simulate multiple steps with user messages (screenshots) and assistant responses
            repeat(stepCount) { step ->
                context.addUserMessage("user_$step", null)
                context.addAssistantMessage("assistant_$step")
            }
            
            val messages = context.getMessages()
            
            // Expected: 1 system + stepCount user + stepCount assistant
            val expectedTotal = 1 + stepCount + stepCount
            
            messages.size == expectedTotal &&
            context.getUserMessageCount() == stepCount &&
            context.getAssistantMessageCount() == stepCount
        }
    }
    
    "context message accumulation - first message is always system prompt" {
        val systemPromptArb = Arb.string(1..100)
        val stepCountArb = Arb.int(0..10)
        
        forAll(100, systemPromptArb, stepCountArb) { systemPrompt, stepCount ->
            val context = AgentContext(systemPrompt, 100)
            
            // Add some messages
            repeat(stepCount) { step ->
                context.addUserMessage("user_$step", null)
                context.addAssistantMessage("assistant_$step")
            }
            
            val messages = context.getMessages()
            val firstMessage = messages.firstOrNull()
            
            firstMessage != null &&
            firstMessage is ChatMessage.System &&
            firstMessage.content == systemPrompt
        }
    }
    
    /**
     * Property 18: Context image cleanup
     * 
     * For any AgentContext with multiple user messages containing images, after adding 
     * a new screenshot, only the most recent user message should contain an image; 
     * all previous images should be removed.
     * 
     * **Feature: autoglm-phone-agent, Property 18: Context image cleanup**
     * **Validates: Requirements 8.3**
     */
    "context image cleanup - only most recent user message contains image" {
        val systemPromptArb = Arb.string(1..50)
        val userTextArb = Arb.string(1..100)
        val imageDataArb = Arb.string(10..200) // Simulated base64 image data
        val stepCountArb = Arb.int(2..10) // At least 2 steps to test cleanup
        
        forAll(100, systemPromptArb, userTextArb, imageDataArb, stepCountArb) { 
            systemPrompt, userText, imageData, stepCount ->
            
            val context = AgentContext(systemPrompt, 100)
            
            // Simulate multiple steps, each with a user message containing an image
            repeat(stepCount) { step ->
                context.addUserMessage("$userText step $step", "$imageData$step")
                context.addAssistantMessage("response $step")
            }
            
            val messages = context.getMessages()
            val userMessages = messages.filterIsInstance<ChatMessage.User>()
            
            // Count how many user messages have images
            val messagesWithImages = userMessages.filter { it.imageBase64 != null }
            
            // Only the last user message should have an image
            val lastUserMessage = userMessages.lastOrNull()
            
            messagesWithImages.size == 1 &&
            lastUserMessage?.imageBase64 != null
        }
    }
    
    "context image cleanup - previous images are removed when new screenshot added" {
        val systemPromptArb = Arb.string(1..50)
        val stepCountArb = Arb.int(3..8)
        
        forAll(100, systemPromptArb, stepCountArb) { systemPrompt, stepCount ->
            val context = AgentContext(systemPrompt, 100)
            
            // Add multiple user messages with images
            repeat(stepCount) { step ->
                context.addUserMessage("task step $step", "image_data_$step")
                context.addAssistantMessage("response $step")
            }
            
            val messages = context.getMessages()
            val userMessages = messages.filterIsInstance<ChatMessage.User>()
            
            // All user messages except the last should have null imageBase64
            val allPreviousImagesRemoved = userMessages.dropLast(1).all { it.imageBase64 == null }
            
            // The last user message should retain its image
            val lastHasImage = userMessages.lastOrNull()?.imageBase64 != null
            
            allPreviousImagesRemoved && lastHasImage
        }
    }
    
    "context image cleanup - text content preserved after image removal" {
        val systemPromptArb = Arb.string(1..50)
        val stepCountArb = Arb.int(2..6)
        
        forAll(100, systemPromptArb, stepCountArb) { systemPrompt, stepCount ->
            val context = AgentContext(systemPrompt, 100)
            
            // Add user messages with unique text and images
            repeat(stepCount) { step ->
                context.addUserMessage("unique_text_$step", "image_$step")
                context.addAssistantMessage("response_$step")
            }
            
            val messages = context.getMessages()
            val userMessages = messages.filterIsInstance<ChatMessage.User>()
            
            // Verify all text content is preserved
            val allTextPreserved = userMessages.mapIndexed { index, msg ->
                msg.text == "unique_text_$index"
            }.all { it }
            
            // Verify image cleanup happened correctly
            val imageCleanupCorrect = userMessages.dropLast(1).all { it.imageBase64 == null } &&
                userMessages.lastOrNull()?.imageBase64 != null
            
            allTextPreserved && imageCleanupCorrect
        }
    }
    
    /**
     * Property 19: Context reset completeness
     * 
     * For any AgentContext after reset, the message list should be empty and ready 
     * for a new task. The context should only contain the system prompt after reset.
     * 
     * **Feature: autoglm-phone-agent, Property 19: Context reset completeness**
     * **Validates: Requirements 8.4**
     */
    "context reset completeness - after reset only system prompt remains" {
        val systemPromptArb = Arb.string(1..100)
        val stepCountArb = Arb.int(1..20)
        
        forAll(100, systemPromptArb, stepCountArb) { systemPrompt, stepCount ->
            val context = AgentContext(systemPrompt, 100)
            
            // Populate context with messages simulating a completed task
            repeat(stepCount) { step ->
                context.addUserMessage("task_$step", "image_$step")
                context.addAssistantMessage("response_$step")
            }
            
            // Verify context has accumulated messages
            val preResetCount = context.getMessageCount()
            val hadMessages = preResetCount > 1
            
            // Reset the context
            context.reset()
            
            // After reset, should only have system prompt
            val messages = context.getMessages()
            val postResetCount = context.getMessageCount()
            
            hadMessages &&
            postResetCount == 1 &&
            messages.size == 1 &&
            messages.first() is ChatMessage.System &&
            (messages.first() as ChatMessage.System).content == systemPrompt
        }
    }
    
    "context reset completeness - context is ready for new task after reset" {
        val systemPromptArb = Arb.string(1..100)
        val stepCountArb = Arb.int(1..15)
        
        forAll(100, systemPromptArb, stepCountArb) { systemPrompt, stepCount ->
            val context = AgentContext(systemPrompt, 100)
            
            // Simulate first task
            repeat(stepCount) { step ->
                context.addUserMessage("first_task_$step", "image_$step")
                context.addAssistantMessage("first_response_$step")
            }
            
            // Reset for new task
            context.reset()
            
            // Verify context is in clean state
            val isClean = context.isEmpty() &&
                context.isInitialized() &&
                context.getSystemPrompt() == systemPrompt &&
                context.getUserMessageCount() == 0 &&
                context.getAssistantMessageCount() == 0
            
            // Simulate second task to verify context works correctly after reset
            context.addUserMessage("second_task", "new_image")
            context.addAssistantMessage("second_response")
            
            val afterSecondTask = context.getMessageCount() == 3 && // system + user + assistant
                context.getUserMessageCount() == 1 &&
                context.getAssistantMessageCount() == 1
            
            isClean && afterSecondTask
        }
    }
    
    "context reset completeness - all message types cleared on reset" {
        val systemPromptArb = Arb.string(1..50)
        val userCountArb = Arb.int(1..10)
        val assistantCountArb = Arb.int(1..10)
        
        forAll(100, systemPromptArb, userCountArb, assistantCountArb) { 
            systemPrompt, userCount, assistantCount ->
            
            val context = AgentContext(systemPrompt, 100)
            
            // Add various user messages
            repeat(userCount) { i ->
                context.addUserMessage("user_$i", if (i % 2 == 0) "image_$i" else null)
            }
            
            // Add various assistant messages
            repeat(assistantCount) { i ->
                context.addAssistantMessage("assistant_$i")
            }
            
            // Verify messages were added
            val preResetUserCount = context.getUserMessageCount()
            val preResetAssistantCount = context.getAssistantMessageCount()
            
            // Reset
            context.reset()
            
            // Verify all non-system messages are cleared
            val messages = context.getMessages()
            val userMessages = messages.filterIsInstance<ChatMessage.User>()
            val assistantMessages = messages.filterIsInstance<ChatMessage.Assistant>()
            val systemMessages = messages.filterIsInstance<ChatMessage.System>()
            
            preResetUserCount == userCount &&
            preResetAssistantCount == assistantCount &&
            userMessages.isEmpty() &&
            assistantMessages.isEmpty() &&
            systemMessages.size == 1 &&
            systemMessages.first().content == systemPrompt
        }
    }
})
