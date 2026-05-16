package com.github.pavelkuliaka.repository

import com.github.pavelkuliaka.model.CardType
import com.github.pavelkuliaka.model.Turn
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.JsonArray
import java.lang.reflect.Type
import java.util.UUID

class TurnAdapter : JsonSerializer<Turn>, JsonDeserializer<Turn> {
    override fun serialize(src: Turn, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val obj = JsonObject()
        obj.addProperty("type", src::class.simpleName)
        obj.addProperty("playerId", src.playerId.toString())
        when (src) {
            is Turn.DrawCard -> obj.addProperty("card", src.card.name)
            is Turn.Defuse -> obj.addProperty("insertPosition", src.insertPosition)
            is Turn.Nope -> obj.addProperty("targetTurnIndex", src.targetTurnIndex)
            is Turn.Shuffle -> {
                val arr = JsonArray()
                src.newDrawPile.forEach { arr.add(it.name) }
                obj.add("newDrawPile", arr)
            }
            is Turn.Favor -> obj.addProperty("takenCard", src.takenCard.name)
            is Turn.PlayDouble -> {
                obj.addProperty("card", src.card.name)
                obj.addProperty("stolenCard", src.stolenCard.name)
            }
            is Turn.PlayTriple -> obj.addProperty("card", src.card.name)
            is Turn.Attack, is Turn.Skip, is Turn.SeeTheFuture, is Turn.Pass -> { }
        }
        return obj
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Turn {
        val obj = json.asJsonObject
        val type = obj.get("type").asString
        val playerId = UUID.fromString(obj.get("playerId").asString)
        return when (type) {
            "DrawCard" -> Turn.DrawCard(playerId, CardType.valueOf(obj.get("card").asString))
            "Defuse" -> Turn.Defuse(playerId, obj.get("insertPosition").asInt)
            "Nope" -> Turn.Nope(playerId, obj.get("targetTurnIndex").asInt)
            "Attack" -> Turn.Attack(playerId)
            "Skip" -> Turn.Skip(playerId)
            "SeeTheFuture" -> Turn.SeeTheFuture(playerId)
            "Shuffle" -> Turn.Shuffle(playerId, obj.getAsJsonArray("newDrawPile").map { CardType.valueOf(it.asString) })
            "Favor" -> Turn.Favor(playerId, CardType.valueOf(obj.get("takenCard").asString))
            "Pass" -> Turn.Pass(playerId)
            "PlayDouble" -> Turn.PlayDouble(
                playerId,
                CardType.valueOf(obj.get("card").asString),
                CardType.valueOf(obj.get("stolenCard").asString)
            )
            "PlayTriple" -> Turn.PlayTriple(playerId, CardType.valueOf(obj.get("card").asString))
            else -> throw IllegalArgumentException("Unknown turn type: $type")
        }
    }
}
