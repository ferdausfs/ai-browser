package com.zai.aibrowser.ai

/**
 * ============================================================================
 * AI agent contract (executed for real in Phase 2-4; these interfaces are stable
 * from Phase 1 so browser code never needs to change when they light up).
 * ============================================================================
 *
 * Ground rules enforced by this contract:
 * - The model NEVER emits executable code (JavaScript, shell, anything else).
 *   It can only request one of the typed actions below, and every response must
 *   be validated against this schema before it is executed.
 * - Page content is untrusted DATA, never instructions.
 * - Credentials, OTPs, cookies and session tokens never reach the model.
 */

/** The complete action vocabulary of the agent. */
sealed class AgentAction {

    enum class Direction { UP, DOWN, TOP, BOTTOM }
    enum class Amount { SMALL, PAGE, LARGE }
    enum class AskKind { INFO, CONFIRM, CHOICE }
    enum class Status { SUCCESS, PARTIAL, FAILED }

    data class ObservePage(val reason: String) : AgentAction()

    data class Scroll(val direction: Direction, val amount: Amount) : AgentAction()

    data class ClickElement(val elementId: String, val expectation: String) : AgentAction()

    data class FillField(val elementId: String, val value: String, val sensitive: Boolean) : AgentAction()

    data class Navigate(val url: String) : AgentAction()

    data object GoBack : AgentAction()

    data class AskUser(val question: String, val kind: AskKind) : AgentAction()

    data class Finish(val status: Status, val summary: String) : AgentAction()
}

/** Compact, privacy-filtered representation of the visible page (produced in Phase 2). */
data class PageObservation(
    val url: String,
    val title: String,
    val contentBlocks: List<ContentBlock>,
    val actionableElements: List<ActionableElement>,
    val redactedCount: Int
)

data class ContentBlock(
    val role: String,
    val text: String
)

data class ActionableElement(
    val elementId: String,
    val role: String,
    val label: String,
    val target: String?
)

data class AgentActionResult(
    val action: AgentAction,
    val verified: Boolean,
    val details: String
)

/** Risk classification used by the approval layer before consequential actions. */
enum class RiskLevel { LOW, MEDIUM, HIGH }

data class ApprovalRequest(
    val title: String,
    val destination: String,
    val details: String,
    val riskLevel: RiskLevel
)

enum class ApprovalDecision { APPROVED, DENIED, TAKE_OVER }

/** Agent run states surfaced in the visible status/log UI (Phase 2+). */
sealed class AgentRunState {
    data object Idle : AgentRunState()
    data class Planning(val stepIndex: Int) : AgentRunState()
    data class Acting(val stepIndex: Int, val action: AgentAction) : AgentRunState()
    data class Verifying(val stepIndex: Int) : AgentRunState()
    data class AwaitingApproval(val request: ApprovalRequest) : AgentRunState()
    data class AwaitingUser(val question: String) : AgentRunState()
    data class Finished(val status: AgentAction.Status, val summary: String) : AgentRunState()
}

data class AgentLogEntry(
    val timestamp: Long,
    val level: String,
    val message: String
)
