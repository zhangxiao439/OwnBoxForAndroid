package moe.matsuri.nb4a.ui

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.res.TypedArrayUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.setPadding
import androidx.core.widget.NestedScrollView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.utils.Theme
import kotlin.math.roundToInt

class ColorPickerPreference @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = TypedArrayUtils.getAttr(
        context,
        androidx.preference.R.attr.editTextPreferenceStyle,
        android.R.attr.editTextPreferenceStyle
    )
) : Preference(context, attrs, defStyle) {

    data class PresetTheme(
        val id: Int,
        val name: String,
        val color: Int,
        val subtitle: String? = null
    )

    companion object {

        /**
         * 各色相主题的标识色，与 res/values/themes.xml 中各 Theme.SagerNet.* 的
         * colorPrimary 取值保持一致，用于选择器中的色块预览。
         */
        private val THEME_COLORS: Map<Int, Int> = mapOf(
            Theme.RED to Color.parseColor("#F44336"),
            Theme.PINK to Color.parseColor("#E91E63"),
            Theme.PINK_SSR to Color.parseColor("#FB7299"),
            Theme.PURPLE to Color.parseColor("#9C27B0"),
            Theme.DEEP_PURPLE to Color.parseColor("#673AB7"),
            Theme.INDIGO to Color.parseColor("#3F51B5"),
            Theme.BLUE to Color.parseColor("#2196F3"),
            Theme.LIGHT_BLUE to Color.parseColor("#03A9F4"),
            Theme.CYAN to Color.parseColor("#00BCD4"),
            Theme.TEAL to Color.parseColor("#009688"),
            Theme.GREEN to Color.parseColor("#4CAF50"),
            Theme.LIGHT_GREEN to Color.parseColor("#8BC34A"),
            Theme.LIME to Color.parseColor("#CDDC39"),
            Theme.YELLOW to Color.parseColor("#FFEB3B"),
            Theme.AMBER to Color.parseColor("#FFC107"),
            Theme.ORANGE to Color.parseColor("#FF9800"),
            Theme.DEEP_ORANGE to Color.parseColor("#FF5722"),
            Theme.BROWN to Color.parseColor("#795548"),
            Theme.GREY to Color.parseColor("#9E9E9E"),
            Theme.BLUE_GREY to Color.parseColor("#607D8B"),
            Theme.VERDANT_MINT to Color.parseColor("#00B96B"),
            Theme.BLACK to Color.BLACK,
            Theme.WHITE to Color.WHITE,
            Theme.LIGHT_GRAY to Color.parseColor("#F5F5F7")
        )

        private fun colorOf(themeId: Int): Int = THEME_COLORS[themeId] ?: Color.GRAY

        /** OwnBox 自有的三套基础主题 */
        private val BASE_THEMES = listOf(
            PresetTheme(Theme.BLACK, "纯黑 (AMOLED Black)", Color.BLACK, "纯黑底色 #000000 · 极致省电高对比"),
            PresetTheme(Theme.WHITE, "纯白 (Pure White)", Color.WHITE, "纯白底色 #FFFFFF · 极简黑白高反差"),
            PresetTheme(Theme.LIGHT_GRAY, "浅灰 (Light Gray)", Color.parseColor("#F5F5F7"), "柔灰底色 #F5F5F7 · 优雅层次悬浮感")
        )

        /** 移植自 ThroneForAndroid 的完整色相主题 */
        private val HUE_THEMES = listOf(
            PresetTheme(Theme.RED, "红色 (Red)", colorOf(Theme.RED), "#F44336 · Material Red"),
            PresetTheme(Theme.PINK, "玫红 (Pink)", colorOf(Theme.PINK), "#E91E63 · Material Pink"),
            PresetTheme(Theme.PINK_SSR, "蜜桃粉 (Pink SSR)", colorOf(Theme.PINK_SSR), "#FB7299 · 经典 SagerNet 主色"),
            PresetTheme(Theme.PURPLE, "紫色 (Purple)", colorOf(Theme.PURPLE), "#9C27B0 · Material Purple"),
            PresetTheme(Theme.DEEP_PURPLE, "深紫 (Deep Purple)", colorOf(Theme.DEEP_PURPLE), "#673AB7 · Material Deep Purple"),
            PresetTheme(Theme.INDIGO, "靛蓝 (Indigo)", colorOf(Theme.INDIGO), "#3F51B5 · Material Indigo"),
            PresetTheme(Theme.BLUE, "蓝色 (Blue)", colorOf(Theme.BLUE), "#2196F3 · Material Blue"),
            PresetTheme(Theme.LIGHT_BLUE, "亮蓝 (Light Blue)", colorOf(Theme.LIGHT_BLUE), "#03A9F4 · Material Light Blue"),
            PresetTheme(Theme.CYAN, "青色 (Cyan)", colorOf(Theme.CYAN), "#00BCD4 · Material Cyan"),
            PresetTheme(Theme.TEAL, "蓝绿 (Teal)", colorOf(Theme.TEAL), "#009688 · Material Teal"),
            PresetTheme(Theme.GREEN, "绿色 (Green)", colorOf(Theme.GREEN), "#4CAF50 · Material Green"),
            PresetTheme(Theme.LIGHT_GREEN, "浅绿 (Light Green)", colorOf(Theme.LIGHT_GREEN), "#8BC34A · Material Light Green"),
            PresetTheme(Theme.LIME, "青柠 (Lime)", colorOf(Theme.LIME), "#CDDC39 · Material Lime"),
            PresetTheme(Theme.YELLOW, "明黄 (Yellow)", colorOf(Theme.YELLOW), "#FFEB3B · Material Yellow"),
            PresetTheme(Theme.AMBER, "琥珀 (Amber)", colorOf(Theme.AMBER), "#FFC107 · Material Amber"),
            PresetTheme(Theme.ORANGE, "橙色 (Orange)", colorOf(Theme.ORANGE), "#FF9800 · Material Orange"),
            PresetTheme(Theme.DEEP_ORANGE, "深橙 (Deep Orange)", colorOf(Theme.DEEP_ORANGE), "#FF5722 · Material Deep Orange"),
            PresetTheme(Theme.BROWN, "棕色 (Brown)", colorOf(Theme.BROWN), "#795548 · Material Brown"),
            PresetTheme(Theme.GREY, "灰色 (Grey)", colorOf(Theme.GREY), "#9E9E9E · Material Grey"),
            PresetTheme(Theme.BLUE_GREY, "蓝灰 (Blue Grey)", colorOf(Theme.BLUE_GREY), "#607D8B · Material Blue Grey"),
            PresetTheme(Theme.VERDANT_MINT, "薄荷绿 (Verdant Mint)", colorOf(Theme.VERDANT_MINT), "#00B96B · Throne 专属配色")
        )
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val widgetFrame = holder.findViewById(android.R.id.widget_frame) as LinearLayout
        widgetFrame.removeAllViews()

        val displayColor = when {
            Theme.isBlackTheme(context) -> Color.BLACK
            Theme.isWhiteTheme(context) -> Color.WHITE
            Theme.isLightGrayTheme(context) -> Color.parseColor("#F5F5F7")
            else -> colorOf(DataStore.appTheme)
        }

        val factor = context.resources.displayMetrics.density
        val size = (44 * factor).roundToInt()
        val widgetIv = ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(size, size)
            setImageDrawable(getColorBadgeDrawable(context.resources, displayColor, false))
        }
        widgetFrame.addView(widgetIv)
        widgetFrame.visibility = View.VISIBLE
    }

    private fun getColorBadgeDrawable(res: Resources, color: Int, isSelected: Boolean): Drawable {
        val factor = res.displayMetrics.density
        val strokeColor = when (color) {
            Color.WHITE -> Color.parseColor("#CCCCCC")
            Color.parseColor("#F5F5F7") -> Color.parseColor("#CBD5E1")
            Color.BLACK -> Color.parseColor("#444444")
            else -> Color.parseColor("#33000000")
        }

        val circle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke((1.5f * factor).roundToInt().coerceAtLeast(1), strokeColor)
        }

        if (!isSelected) {
            return circle
        }

        val checkmark = ResourcesCompat.getDrawable(res, R.drawable.ic_baseline_check_circle_24, null)!!.mutate()
        val checkTint = if (color == Color.WHITE || color == Color.parseColor("#F5F5F7")) {
            Color.parseColor("#212121")
        } else {
            Color.WHITE
        }
        DrawableCompat.setTint(checkmark, checkTint)

        val checkInset = (8 * factor).roundToInt()
        val layer = LayerDrawable(arrayOf(circle, checkmark))
        layer.setLayerInset(1, checkInset, checkInset, checkInset, checkInset)
        return layer
    }

    override fun onClick() {
        super.onClick()

        lateinit var dialog: AlertDialog

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp2px(16), dp2px(10), dp2px(16), dp2px(16))
        }

        val scroll = NestedScrollView(context).apply {
            addView(rootLayout)
        }

        val currentThemeId = if (DataStore.appTheme !in Theme.VALID_THEMES) {
            Theme.LIGHT_GRAY
        } else {
            DataStore.appTheme
        }

        fun applyTheme(themeId: Int) {
            persistInt(themeId)
            DataStore.appTheme = themeId
            Theme.applyNightTheme()
            dialog.dismiss()
            callChangeListener(themeId)
            (context as? Activity)?.let {
                ActivityCompat.recreate(it)
            }
        }

        fun addSectionTitle(text: String) {
            rootLayout.addView(TextView(context).apply {
                this.text = text
                textSize = 13f
                setTextColor(context.getColorAttr(R.attr.primaryOrTextSecondary))
                setTypeface(null, Typeface.BOLD)
                setPadding(dp2px(4), dp2px(4), 0, dp2px(8))
            })
        }

        fun addThemeCards(themes: List<PresetTheme>) {
            for (preset in themes) {
                val isSelected = currentThemeId == preset.id
                val card = MaterialCardView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 0, 0, dp2px(8))
                    }
                    radius = dp2px(12).toFloat()
                    cardElevation = 0f
                    strokeWidth = if (isSelected) dp2px(2) else dp2px(1)
                    strokeColor = if (isSelected) context.getColorAttr(R.attr.colorPrimary) else Color.parseColor("#25888888")
                    setCardBackgroundColor(if (isSelected) Color.parseColor("#0F2196F3") else Color.TRANSPARENT)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        applyTheme(preset.id)
                    }

                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp2px(12), dp2px(10), dp2px(12), dp2px(10))

                        val iv = ImageView(context).apply {
                            val sz = dp2px(36)
                            layoutParams = LinearLayout.LayoutParams(sz, sz).apply {
                                marginEnd = dp2px(12)
                            }
                            setImageDrawable(getColorBadgeDrawable(context.resources, preset.color, isSelected))
                        }
                        addView(iv)

                        val textCol = LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

                            val titleView = TextView(context).apply {
                                text = preset.name
                                textSize = 14f
                                setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                                setTextColor(context.getColorAttr(android.R.attr.textColorPrimary))
                            }
                            addView(titleView)

                            val subtitleView = TextView(context).apply {
                                text = preset.subtitle
                                textSize = 11.5f
                                setTextColor(context.getColorAttr(android.R.attr.textColorSecondary))
                                setPadding(0, dp2px(2), 0, 0)
                            }
                            addView(subtitleView)
                        }
                        addView(textCol)
                    }
                    addView(row)
                }
                rootLayout.addView(card)
            }
        }

        // 1. OwnBox 基础主题（纯黑 / 纯白 / 浅灰）
        addSectionTitle("OwnBox 基础主题")
        addThemeCards(BASE_THEMES)

        // 2. 色相主题（移植自 ThroneForAndroid）
        addSectionTitle("色相主题 (Throne 移植)")
        addThemeCards(HUE_THEMES)

        dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(scroll)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
