package com.guildofsmiths.trademesh.planner.state

import com.guildofsmiths.trademesh.planner.types.PlannerStateEnum

/**
 * PlannerStateMachine - Explicit state transitions for the Planner
 * 
 * STATES:
 * EMPTY → DRAFT → COMPILING → COMPILED → TRANSFERRING
 *                     ↓
 *              COMPILE_ERROR
 * 
 * All transitions are explicit. Invalid transitions are rejected.
 */
class PlannerStateMachine {

    enum class PlannerAction {
        INPUT,            // User types in canvas
        CLEAR,            // User clears all content
        COMPILE,          // User triggers compilation
        COMPILE_SUCCESS,  // System: compilation succeeded
        COMPILE_FAIL,     // System: compilation failed
        TRANSFER,         // User triggers transfer
        TRANSFER_SUCCESS, // System: transfer succeeded
        TRANSFER_FAIL     // System: transfer failed
    }

    enum class TransitionName {
        T01_BEGIN_EDITING,
        T02_CLEAR_ALL,
        T03_START_COMPILE,
        T04_COMPILE_SUCCESS,
        T05_COMPILE_FAIL,
        T06_EDIT_AFTER_COMPILE,
        T07_START_TRANSFER,
        T08_CLEAR_AFTER_COMPILE,
        T09_EDIT_AFTER_ERROR,
        T10_CLEAR_AFTER_ERROR,
        T11_TRANSFER_COMPLETE,
        T12_TRANSFER_FAIL
    }

    data class Transition(
        val name: TransitionName,
        val nextState: PlannerStateEnum
    )

    sealed class TransitionResult {
        data class Valid(
            val transition: TransitionName,
            val nextState: PlannerStateEnum
        ) : TransitionResult()

        data class Invalid(
            val error: InvalidTransitionError
        ) : TransitionResult()
    }

    data class InvalidTransitionError(
        val code: String = "INVALID_TRANSITION",
        val from: PlannerStateEnum,
        val action: PlannerAction,
        val message: String
    )

    private val transitionMap: Map<PlannerStateEnum, Map<PlannerAction, Transition>> = mapOf(
        PlannerStateEnum.EMPTY to mapOf(
            PlannerAction.INPUT to Transition(TransitionName.T01_BEGIN_EDITING, PlannerStateEnum.DRAFT)
        ),
        PlannerStateEnum.DRAFT to mapOf(
            PlannerAction.CLEAR to Transition(TransitionName.T02_CLEAR_ALL, PlannerStateEnum.EMPTY),
            PlannerAction.COMPILE to Transition(TransitionName.T03_START_COMPILE, PlannerStateEnum.COMPILING)
        ),
        PlannerStateEnum.COMPILING to mapOf(
            PlannerAction.COMPILE_SUCCESS to Transition(TransitionName.T04_COMPILE_SUCCESS, PlannerStateEnum.COMPILED),
            PlannerAction.COMPILE_FAIL to Transition(TransitionName.T05_COMPILE_FAIL, PlannerStateEnum.COMPILE_ERROR)
        ),
        PlannerStateEnum.COMPILED to mapOf(
            PlannerAction.INPUT to Transition(TransitionName.T06_EDIT_AFTER_COMPILE, PlannerStateEnum.DRAFT),
            PlannerAction.TRANSFER to Transition(TransitionName.T07_START_TRANSFER, PlannerStateEnum.TRANSFERRING),
            PlannerAction.CLEAR to Transition(TransitionName.T08_CLEAR_AFTER_COMPILE, PlannerStateEnum.EMPTY)
        ),
        PlannerStateEnum.COMPILE_ERROR to mapOf(
            PlannerAction.INPUT to Transition(TransitionName.T09_EDIT_AFTER_ERROR, PlannerStateEnum.DRAFT),
            PlannerAction.CLEAR to Transition(TransitionName.T10_CLEAR_AFTER_ERROR, PlannerStateEnum.EMPTY)
        ),
        PlannerStateEnum.TRANSFERRING to mapOf(
            PlannerAction.TRANSFER_SUCCESS to Transition(TransitionName.T11_TRANSFER_COMPLETE, PlannerStateEnum.COMPILED),
            PlannerAction.TRANSFER_FAIL to Transition(TransitionName.T12_TRANSFER_FAIL, PlannerStateEnum.COMPILED)
        )
    )

    fun validateTransition(
        currentState: PlannerStateEnum,
        action: PlannerAction
    ): TransitionResult {
        val stateTransitions = transitionMap[currentState]
        val transition = stateTransitions?.get(action)

        return if (transition != null) {
            TransitionResult.Valid(
                transition = transition.name,
                nextState = transition.nextState
            )
        } else {
            TransitionResult.Invalid(
                InvalidTransitionError(
                    from = currentState,
                    action = action,
                    message = "Action '${action.name}' is not valid in state '${currentState.name}'"
                )
            )
        }
    }

    fun getNextState(currentState: PlannerStateEnum, action: PlannerAction): PlannerStateEnum? {
        val result = validateTransition(currentState, action)
        return when (result) {
            is TransitionResult.Valid -> result.nextState
            is TransitionResult.Invalid -> null
        }
    }

    fun canPerformAction(currentState: PlannerStateEnum, action: PlannerAction): Boolean {
        return validateTransition(currentState, action) is TransitionResult.Valid
    }
}
