package com.capri.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class Result(val say: String, val confirm: (() -> String)? = null)

interface Assistant { fun ask(user: String): Result }

class Brain(private val ctx: Context) : Assistant {
    private val messages = JSONArray()

    override fun ask(user: String): Result {
        if (messages.length() > 30) while (messages.length() > 0) messages.remove(0)
        messages.put(JSONObject().put("role", "user").put("content", user))
        repeat(15) {
            val content = post().getJSONArray("content")
            messages.put(JSONObject().put("role", "assistant").put("content", content))
            val results = JSONArray()
            var say = ""
            var confirm: (() -> String)? = null
            for (k in 0 until content.length()) {
                val b = content.getJSONObject(k)
                if (b.getString("type") == "text") say += b.getString("text") + " "
                if (b.getString("type") == "tool_use") {
                    val name = b.getString("name")
                    val input = b.getJSONObject("input")
                    val q = Actions.describe(name, input)
                    val out = if (q != null) {
                        say = q
                        confirm = { Actions.run(ctx, name, input) }
                        "Aguardando confirmação por voz do usuário."
                    } else Actions.run(ctx, name, input)
                    results.put(JSONObject().put("type", "tool_result")
                        .put("tool_use_id", b.getString("id")).put("content", out))
                }
            }
            if (results.length() == 0) return Result(say.trim())
            messages.put(JSONObject().put("role", "user").put("content", results))
            if (confirm != null) return Result(say.trim(), confirm)
        }
        return Result("Não consegui terminar isso.")
    }

    private fun post(): JSONObject {
        val key = ctx.getSharedPreferences("capri", 0).getString("key", "") ?: ""
        val body = JSONObject().put("model", ctx.getSharedPreferences("capri", 0).getString("model", MODEL) ?: MODEL).put("max_tokens", 1024).put("system", SYSTEM + "\nNotas do usuário (use quando relevante):\n" + Notes.read(ctx))
            .put("tools", JSONArray(TOOLS)).put("messages", messages)
        val c = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 15000; c.readTimeout = 60000
        c.setRequestProperty("content-type", "application/json")
        c.setRequestProperty("x-api-key", key)
        c.setRequestProperty("anthropic-version", "2023-06-01")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val ok = c.responseCode in 200..299
        val text = (if (ok) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
        if (!ok) throw Exception("API ${c.responseCode}")
        return JSONObject(text)
    }

    companion object {
        const val MODEL = "claude-haiku-5-5"
        const val SYSTEM = "Você é a Capri, assistente de voz no celular do usuário, brincalhona e carinhosa, mas direta. Responda em português do Brasil, " +
            "de forma curta e natural, sem markdown nem emojis, pois sua resposta será falada. " +
            "Use as ferramentas para controlar o celular quando o usuário pedir uma ação. " +
            "Para tarefas em qualquer app, trabalhe em passos: open_app, read_screen para ver a tela, click_text, type_text, scroll, wait, e confira o resultado lendo a tela de novo, até concluir. " +
            "Antes de passos irreversíveis (pagar, transferir, apagar, publicar, enviar dados sensíveis), pergunte e espere o usuário dizer sim. " +
            "Quando o usuário contar algo duradouro sobre si, use remember. Para perguntas gerais, apenas responda."
        const val TOOLS = """[
{"name":"open_app","description":"Abre um app instalado pelo nome","input_schema":{"type":"object","properties":{"app":{"type":"string"}},"required":["app"]}},
{"name":"send_whatsapp","description":"Envia mensagem no WhatsApp","input_schema":{"type":"object","properties":{"contact":{"type":"string","description":"nome do contato ou número"},"message":{"type":"string"}},"required":["contact","message"]}},
{"name":"send_sms","description":"Envia SMS","input_schema":{"type":"object","properties":{"contact":{"type":"string"},"message":{"type":"string"}},"required":["contact","message"]}},
{"name":"call","description":"Liga para um contato ou número","input_schema":{"type":"object","properties":{"contact":{"type":"string"}},"required":["contact"]}},
{"name":"navigate","description":"Botões do sistema","input_schema":{"type":"object","properties":{"action":{"type":"string","enum":["home","back","recents","notifications"]}},"required":["action"]}},
{"name":"remember","description":"Guarda uma nota duradoura sobre o usuário","input_schema":{"type":"object","properties":{"note":{"type":"string"}},"required":["note"]}},
{"name":"search_web","description":"Pesquisa no Google","input_schema":{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}},
{"name":"set_timer","description":"Cria um timer","input_schema":{"type":"object","properties":{"seconds":{"type":"integer"},"label":{"type":"string"}},"required":["seconds"]}},
{"name":"media","description":"Controla a música","input_schema":{"type":"object","properties":{"action":{"type":"string","enum":["play_pause","next","prev"]}},"required":["action"]}},
{"name":"volume","description":"Ajusta o volume","input_schema":{"type":"object","properties":{"direction":{"type":"string","enum":["up","down"]},"steps":{"type":"integer"}},"required":["direction"]}},
{"name":"click_text","description":"Toca em um botão/item da tela pelo texto visível ou descrição","input_schema":{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}},
{"name":"type_text","description":"Digita texto no campo focado (ou no primeiro campo da tela)","input_schema":{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}},
{"name":"scroll","description":"Rola a tela","input_schema":{"type":"object","properties":{"direction":{"type":"string","enum":["down","up"]}},"required":["direction"]}},
{"name":"wait","description":"Espera alguns segundos (máx 10) para a tela carregar","input_schema":{"type":"object","properties":{"seconds":{"type":"integer"}}}},
{"name":"open_url","description":"Abre um link no navegador ou app","input_schema":{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}},
{"name":"set_alarm","description":"Cria um alarme","input_schema":{"type":"object","properties":{"hour":{"type":"integer"},"minute":{"type":"integer"},"label":{"type":"string"}},"required":["hour","minute"]}},
{"name":"read_screen","description":"Lê o texto da tela atual","input_schema":{"type":"object","properties":{}}}
]"""
    }
}
