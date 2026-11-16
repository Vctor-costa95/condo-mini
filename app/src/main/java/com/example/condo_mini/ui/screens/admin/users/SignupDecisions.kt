package com.example.condo_mini.ui.screens.admin.users

import com.example.condo_mini.data.remote.userMessage
import com.example.condo_mini.domain.model.SignupRequest
import com.example.condo_mini.domain.repository.DirectoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Aceitar um pedido: confere os dados e o apartamento (dá para trocar) antes de criar o cadastro. */
data class SignupReview(
    val request: SignupRequest,
    val unit: String = request.unit,
    val isSaving: Boolean = false,
    val error: String? = null
)

/** Recusar um pedido: o motivo aparece para a pessoa quando ela tenta entrar. */
data class SignupRejection(
    val request: SignupRequest,
    val reason: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

/**
 * Aceitar e recusar pedidos de cadastro, igual para quem decide: a administradora (em Cadastros)
 * e o síndico (em Aprovações). Cada tela só liga as fichas no próprio estado.
 */
class SignupDecisions(
    private val scope: CoroutineScope,
    private val directoryRepository: DirectoryRepository,
    private val onMessage: (String) -> Unit
) {
    private val _review = MutableStateFlow<SignupReview?>(null)
    val review: StateFlow<SignupReview?> = _review.asStateFlow()

    private val _rejection = MutableStateFlow<SignupRejection?>(null)
    val rejection: StateFlow<SignupRejection?> = _rejection.asStateFlow()

    fun onReview(request: SignupRequest) {
        _review.value = SignupReview(request)
    }

    fun onUnitSelected(unit: String) {
        _review.update { it?.copy(unit = unit, error = null) }
    }

    fun onDismissReview() {
        _review.value = null
    }

    fun onApprove() {
        val current = _review.value ?: return
        _review.update { it?.copy(isSaving = true, error = null) }
        scope.launch {
            directoryRepository.approveSignup(current.request.id, current.unit)
                .onSuccess { user ->
                    _review.value = null
                    onMessage("${user.name} já pode entrar no app (${current.unit}).")
                }
                .onFailure { e -> _review.update { it?.copy(isSaving = false, error = e.userMessage()) } }
        }
    }

    fun onReject(request: SignupRequest) {
        _rejection.value = SignupRejection(request)
    }

    fun onReasonChanged(reason: String) {
        _rejection.update { it?.copy(reason = reason.take(255), error = null) }
    }

    fun onDismissRejection() {
        _rejection.value = null
    }

    fun onConfirmRejection() {
        val current = _rejection.value ?: return
        if (current.reason.isBlank()) {
            _rejection.update { it?.copy(error = "Diga o motivo: a pessoa vê ao tentar entrar.") }
            return
        }
        _rejection.update { it?.copy(isSaving = true, error = null) }
        scope.launch {
            directoryRepository.rejectSignup(current.request.id, current.reason)
                .onSuccess {
                    _rejection.value = null
                    onMessage("Pedido de ${current.request.name} recusado.")
                }
                .onFailure { e -> _rejection.update { it?.copy(isSaving = false, error = e.userMessage()) } }
        }
    }
}
