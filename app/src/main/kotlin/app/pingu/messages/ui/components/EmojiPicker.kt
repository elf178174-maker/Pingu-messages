package app.pingu.messages.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A built-in emoji keyboard.
 *
 * The system keyboard already has one, but reaching it costs two taps and it closes the message
 * field on some keyboards. A small curated set covering the emoji people actually send is enough,
 * and keeps the app free of a several-megabyte emoji database.
 *
 * Rendering is left to the platform font, so the user sees the same emoji here as everywhere else
 * on their phone.
 */
@Composable
fun EmojiPicker(
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    recents: List<String> = emptyList(),
) {
    var categoryIndex by remember { mutableIntStateOf(0) }
    val categories = remember(recents) {
        if (recents.isEmpty()) EmojiCatalog.categories else {
            listOf(EmojiCategory("Recent", recents.take(RECENT_LIMIT))) + EmojiCatalog.categories
        }
    }
    val category = categories.getOrElse(categoryIndex) { categories.first() }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            categories.forEachIndexed { index, item ->
                FilterChip(
                    selected = index == categoryIndex,
                    onClick = { categoryIndex = index },
                    label = { Text(item.emoji.firstOrNull().orEmpty(), fontSize = 15.sp) },
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 44.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(PICKER_HEIGHT),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
        ) {
            items(category.emoji) { emoji ->
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { onEmojiSelected(emoji) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = emoji, fontSize = 24.sp)
                }
            }
        }
    }
}

/** A row of reaction emoji shown above the message action sheet. */
@Composable
fun ReactionPicker(
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { emoji ->
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (emoji == selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        },
                    )
                    .clickable { onSelect(emoji) },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = emoji, fontSize = 24.sp)
            }
        }
    }
}

data class EmojiCategory(val name: String, val emoji: List<String>)

/** A compact, hand-picked catalogue: the emoji that actually appear in text messages. */
object EmojiCatalog {

    private val smileys = (
        "😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 " +
            "😋 😛 😜 🤪 😝 🤗 🤭 🤫 🤔 🤐 😐 😑 😶 😏 😒 🙄 😬 😔 😪 🤤 " +
            "😴 😷 🤒 🤕 🤢 🤮 🥵 🥶 😵 🤯 🤠 🥳 😎 🤓 🧐 😕 😟 🙁 😮 😯 " +
            "😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 " +
            "🤬 😈 💀 🤡 👻 👽 🤖 😺 😻 😼"
        ).split(" ")

    private val people = (
        "👋 🤚 ✋ 🖖 👌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👍 👎 ✊ 👊 " +
            "🤛 🤜 👏 🙌 👐 🤲 🤝 🙏 💪 🦵 👀 👁️ 👄 💋 🧠 👶 🧒 👦 👧 🧑 " +
            "👨 👩 🧓 👴 👵 🙋 🤦 🤷 💁 🙆 🙅 🧏 🚶 🏃 💃 🕺 👫 👬 👭 👪"
        ).split(" ")

    private val nature = (
        "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🐤 🦆 " +
            "🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🕷️ 🐢 🐍 🦎 🐙 🦑 🦐 " +
            "🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🦓 🦍 🐘 🦏 🐪 🦒 🐄 🐖 🐑 🐎 🐕 🐈 " +
            "🌵 🎄 🌲 🌳 🌴 🌱 🌿 ☘️ 🍀 🎍 🌾 🌺 🌻 🌹 🌷 🌸 💐 🍄 🌰 🐚"
        ).split(" ")

    private val food = (
        "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🥑 🍆 " +
            "🥔 🥕 🌽 🌶️ 🥒 🥬 🥦 🧄 🧅 🍄 🥜 🌰 🍞 🥐 🥖 🥨 🥯 🥞 🧇 🧀 " +
            "🍖 🍗 🥩 🥓 🍔 🍟 🍕 🌭 🥪 🌮 🌯 🥙 🧆 🥚 🍳 🥘 🍲 🥣 🥗 🍿 " +
            "🍱 🍘 🍙 🍚 🍛 🍜 🍝 🍠 🍢 🍣 🍤 🍥 🥮 🍡 🥟 🍦 🍰 🎂 🍫 ☕"
        ).split(" ")

    private val activities = (
        "⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🏓 🏸 🥅 🏒 🏑 🏏 ⛳ 🏹 🎣 🥊 🥋 🎽 " +
            "⛸️ 🥌 🛷 🎿 ⛷️ 🏂 🏋️ 🤼 🤸 ⛹️ 🤺 🤾 🏌️ 🏇 🧘 🏄 🏊 🤽 🚣 🧗 " +
            "🚵 🚴 🏆 🥇 🥈 🥉 🎖️ 🏅 🎗️ 🎫 🎟️ 🎪 🎭 🎨 🎬 🎤 🎧 🎼 🎹 🥁 " +
            "🎷 🎺 🎸 🎻 🎲 ♟️ 🎯 🎳 🎮 🎰"
        ).split(" ")

    private val travel = (
        "🚗 🚕 🚙 🚌 🚎 🏎️ 🚓 🚑 🚒 🚐 🚚 🚛 🚜 🛴 🚲 🛵 🏍️ 🚨 🚔 🚍 " +
            "🚝 🚄 🚅 🚈 🚂 🚆 🚇 🚊 🚉 ✈️ 🛫 🛬 🛩️ 💺 🚁 🚟 🚠 🚡 🛰️ 🚀 " +
            "🛸 🚢 ⛵ 🛶 🚤 ⛴️ 🛳️ ⚓ 🗺️ 🗿 🗽 🗼 🏰 🏯 🏟️ 🎡 🎢 🎠 ⛲ ⛱️ " +
            "🏖️ 🏝️ 🏔️ ⛰️ 🌋 🗻 🏕️ ⛺ 🏠 🏡 🏘️ 🏢 🏬 🏣 🏤 🏥 🏦 🏨 🏪 🏫"
        ).split(" ")

    private val objects = (
        "⌚ 📱 💻 ⌨️ 🖥️ 🖨️ 🖱️ 💽 💾 💿 📀 📷 📸 📹 🎥 📞 ☎️ 📟 📠 📺 " +
            "📻 ⏰ ⏳ ⌛ 🔋 🔌 💡 🔦 🕯️ 🧯 🛢️ 💸 💵 💴 💶 💷 💰 💳 💎 ⚖️ " +
            "🔧 🔨 ⚒️ 🛠️ ⛏️ 🔩 ⚙️ 🧱 ⛓️ 🧲 🔫 💣 🔪 🚬 ⚰️ 🏺 🔮 📿 💈 ⚗️ " +
            "🔭 🔬 🕳️ 💊 💉 🌡️ 🧹 🧺 🧻 🚽 🚿 🛁 🧼 🪒 🧽 🧴 🔑 🗝️ 🚪 🛏️"
        ).split(" ")

    private val symbols = (
        "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ☮️ " +
            "✝️ ☪️ 🕉️ ☸️ ✡️ 🔯 🕎 ☯️ ☦️ ⛎ ♈ ♉ ♊ ♋ ♌ ♍ ♎ ♏ ♐ ♑ " +
            "♒ ♓ 🆔 ⚛️ ✅ ❌ ❎ ➕ ➖ ➗ ✖️ ♾️ ‼️ ⁉️ ❓ ❔ ❕ ❗ 〽️ ⚠️ " +
            "🚸 🔱 ⚜️ 🔰 ♻️ 🈯 💯 🔔 🔕 🎵 🎶 💤 💢 💥 💫 💦 💨 🕐 ⭐ 🌟"
        ).split(" ")

    val categories: List<EmojiCategory> = listOf(
        EmojiCategory("Smileys", smileys),
        EmojiCategory("People", people),
        EmojiCategory("Nature", nature),
        EmojiCategory("Food", food),
        EmojiCategory("Activities", activities),
        EmojiCategory("Travel", travel),
        EmojiCategory("Objects", objects),
        EmojiCategory("Symbols", symbols),
    )
}

private val PICKER_HEIGHT = 240.dp
private const val RECENT_LIMIT = 24
