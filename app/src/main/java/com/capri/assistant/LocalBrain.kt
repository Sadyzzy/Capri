package com.capri.assistant

import android.content.Context
import com.google.ai.edge.litertlm.*
import org.json.JSONObject
import java.io.File

/** Cérebro 100% local: roda o modelo .litertlm no próprio celular, sem internet. */
class LocalBrain(private val ctx: Context) : Assistant {
    private var engine: Engine? = null

    private fun engine(): Engine {
        engine?.let { return it }
        val path = File(ctx.filesDir, "model.litertlm").path
        fun make(b: Backend) = Engine(EngineConfig(modelPath = path, backend = b, cacheDir = ctx.cacheDir.path))
            .also { it.initialize() }
        val e = try { make(Backend.GPU()) } catch (t: Throwable) { make(Backend.CPU()) }
        engine = e
        return e
    }

    override fun ask(user: String): Result {
        val conv = engine().createConversation(ConversationConfig(
            systemInstruction = Contents.of(SYSTEM + "\nNotas do usuário:\n" + Notes.read(ctx)),
            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.3)))
        conv.use { c ->
            var msg = user
            repeat(12) {
                val raw = c.sendMessage(msg).toString()
                val j = parse(raw) ?: return Result(raw.trim().ifBlank { "Não entendi." })
                val tool = j.optString("tool")
                if (tool.isBlank()) return Result(j.optString("say", raw).trim())
                val args = j.optJSONObject("args") ?: JSONObject()
                val q = Actions.describe(tool, args)
                if (q != null) return Result(q) { Actions.run(ctx, tool, args) }
                msg = "RESULTADO: " + Actions.run(ctx, tool, args).take(1500)
            }
        }
        return Result("Não consegui terminar isso.")
    }

    private fun parse(s: String): JSONObject? {
        val a = s.indexOf('{'); val b = s.lastIndexOf('}')
        if (a < 0 || b <= a) return null
        return try { JSONObject(s.substring(a, b + 1)) } catch (e: Exception) { null }
    }

    companion object {
        const val SYSTEM = """Você é a Capri, assistente de voz no celular. Responda SEMPRE com UM único objeto JSON e nada mais.
Para agir: {"tool":"nome","args":{...}}
Para falar com o usuário (resposta final, curta, sem markdown): {"say":"texto"}
Ferramentas:
open_app {app} | send_whatsapp {contact,message} | send_sms {contact,message} | call {contact} | navigate {action: home|back|recents|notifications} | read_screen {} | click_text {text} | type_text {text} | scroll {direction: down|up} | wait {seconds} | open_url {url} | set_alarm {hour,minute,label} | remember {note} | search_web {query} | set_timer {seconds,label} | media {action: play_pause|next|prev} | volume {direction: up|down,steps}
Depois de cada ferramenta você recebe "RESULTADO: ...". Continue com outra ferramenta ou finalize com say.
Perguntas gerais: responda direto com say. Antes de pagar, transferir ou apagar, pergunte com say.
Exemplo 1:
Usuário: abra o WhatsApp
{"tool":"open_app","args":{"app":"WhatsApp"}}
RESULTADO: Abrindo WhatsApp.
{"say":"Abri o WhatsApp."}
Exemplo 2:
Usuário: quanto é 12 vezes 8
{"say":"Doze vezes oito é noventa e seis."}"""
    }
}
