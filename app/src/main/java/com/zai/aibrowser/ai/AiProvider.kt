package com.zai.aibrowser.ai

/**
 * Layer 3 of the agent architecture: AI planning.
 * Any backend (hosted LLM, on-device model, mock) plugs in behind this interface.
 * Implementations must return only typed [AgentAction] values — never code.
 */
interface AiProvider {
    /**
     * Given the user task and the latest compact page observation, decide the next
     * action. [history] contains the actions already taken in this run (for loop
     * avoidance); [stepIndex] is 0-based and lets the host enforce step limits.
     */
    suspend fun nextAction(
        task: String,
        observation: PageObservation?,
        history: List<AgentAction>,
        stepIndex: Int
    ): AgentAction
}

/**
 * Deterministic stand-in used until a real backend is configured (Phase 2).
 * It exercises the exact same contract, so swapping providers later changes
 * nothing in the browser or the UI.
 */
class MockAiProvider : AiProvider {
    override suspend fun nextAction(
        task: String,
        observation: PageObservation?,
        history: List<AgentAction>,
        stepIndex: Int
    ): AgentAction {
        return AgentAction.AskUser(
            question = "Mock AI provider active — the planning engine arrives in Phase 2. " +
                "Your command was noted: \"$task\"",
            kind = AgentAction.AskKind.INFO
        )
    }
}

/** Layer 4: deterministic execution of validated actions (Phase 3). */
interface ActionExecutor {
    suspend fun execute(action: AgentAction): AgentActionResult
}

/** Layer 5: verifies outcomes against expectations instead of assuming success (Phase 3). */
interface ActionVerifier {
    suspend fun verify(
        action: AgentAction,
        before: PageObservation?,
        after: PageObservation?
    ): AgentActionResult
}

/** Layer 6: consent and approval for sensitive/consequential actions (Phase 3-4). */
interface ApprovalHandler {
    suspend fun requestApproval(request: ApprovalRequest): ApprovalDecision
}

/** Simple host for the Phase 1 AI command bar: wires the mock provider end-to-end. */
class MockAgentController(private val provider: AiProvider) {

    suspend fun respondToCommand(command: String): String {
        val action = provider.nextAction(
            task = command,
            observation = null,
            history = emptyList(),
            stepIndex = 0
        )
        return when (action) {
            is AgentAction.AskUser -> action.question +
                "\n\nRegistered actions: observe_page, scroll, click_element, fill_field, " +
                "navigate, go_back, ask_user, finish."
            else -> "Mock AI: contract wired. Execution arrives in Phase 3."
        }
    }
}
