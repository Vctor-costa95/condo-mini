package com.example.condo_mini.data.remote

import com.example.condo_mini.domain.model.Condominium
import com.example.condo_mini.domain.model.SignupForm
import com.example.condo_mini.domain.repository.SignupRepository
import org.json.JSONObject

/** Rotas sem login: quem ainda não tem conta não tem token. */
class ApiSignupRepository(private val api: ApiClient) : SignupRepository {

    override suspend fun condos(): Result<List<Condominium>> = runCatching {
        api.get("public/condos").list { it.toCondo() }
    }

    override suspend fun submit(form: SignupForm): Result<Unit> = runCatching {
        api.send(
            "POST",
            "signup",
            JSONObject()
                .put("condoId", form.condoId)
                .put("unit", form.unit)
                .put("name", form.name.trim())
                .put("email", form.email.trim())
                .put("phone", form.phone.trim())
                .put("login", form.login.trim().lowercase())
                .put("password", form.password)
        )
        Unit
    }
}
