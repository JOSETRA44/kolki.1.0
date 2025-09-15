package com.example.kolki.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.kolki.R

class AboutKolkiFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_about_kolki, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val number = "953834209"

        view.findViewById<View>(R.id.copyNumberButton)?.setOnClickListener {
            try {
                val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Donación Kolki", number))
                Toast.makeText(requireContext(), "Número copiado: $number", Toast.LENGTH_SHORT).show()
                playDonationCelebration(it)
            } catch (_: Exception) { }
        }

        view.findViewById<View>(R.id.openWhatsappButton)?.setOnClickListener {
            try {
                val uri = Uri.parse("https://wa.me/51$number")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "No se pudo abrir WhatsApp", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<View>(R.id.requestApiKeysButton)?.setOnClickListener {
            Toast.makeText(requireContext(), "Envíanos un WhatsApp para solicitar tus API keys", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playDonationCelebration(anchor: View) {
        if (!isAdded) return
        val root = activity?.findViewById<ViewGroup>(android.R.id.content) ?: return

        // Read optional user name for personalized thank-you
        val prefs = requireContext().getSharedPreferences("kolki_prefs", Context.MODE_PRIVATE)
        val userName = prefs.getString("user_name", null)?.takeIf { it.isNotBlank() }

        // Heart + Thank you overlay (personalized)
        val overlay = android.widget.TextView(requireContext()).apply {
            text = if (userName != null) "¡Gracias, $userName!\nCualquier apoyo es bienvenido ❤️" else "¡Gracias!\nCualquier apoyo es bienvenido ❤️"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 22f
            setPadding(32, 32, 32, 32)
            setBackgroundColor(0x99000000.toInt())
            gravity = android.view.Gravity.CENTER
            alpha = 0f
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        root.addView(overlay)
        android.animation.ObjectAnimator.ofFloat(overlay, View.ALPHA, 0f, 1f).apply { duration = 180 }.start()

        // Celebration origin at screen center
        val screenHForCenter = root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val screenWForCenter = root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val startX = screenWForCenter / 2f
        val startY = screenHForCenter / 2f

        fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

        runCatching {
            val burst = android.widget.ImageView(requireContext()).apply {
                setImageResource(android.R.drawable.star_big_on)
                alpha = 0f
                layoutParams = ViewGroup.LayoutParams(dp(26), dp(26))
                x = startX - dp(13)
                y = startY - dp(13)
            }
            root.addView(burst)
            val a1 = android.animation.ObjectAnimator.ofFloat(burst, View.ALPHA, 0f, 1f, 0f).apply { duration = 520 }
            val a2 = android.animation.ObjectAnimator.ofFloat(burst, View.SCALE_X, 0.2f, 1.6f, 1f).apply { duration = 520 }
            val a3 = android.animation.ObjectAnimator.ofFloat(burst, View.SCALE_Y, 0.2f, 1.6f, 1f).apply { duration = 520 }
            android.animation.AnimatorSet().apply {
                playTogether(a1, a2, a3)
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) { root.removeView(burst) }
                })
                start()
            }
        }

        // Money rain + hearts from anchor
        val icons = intArrayOf(
            com.example.kolki.R.drawable.ic_coin,
            com.example.kolki.R.drawable.ic_bill,
            com.example.kolki.R.drawable.ic_heart
        )
        val screenH = root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val screenW = root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val count = 30
        repeat(count) { i ->
            root.postDelayed({
                try {
                    val iv = android.widget.ImageView(requireContext()).apply {
                        val which = kotlin.random.Random.nextInt(icons.size)
                        setImageResource(icons[which])
                        val size = dp(18 + kotlin.random.Random.nextInt(18))
                        layoutParams = ViewGroup.LayoutParams(size, size)
                        alpha = 0f
                    }
                    root.addView(iv)
                    iv.x = (startX - iv.layoutParams.width / 2f) + kotlin.random.Random.nextInt(-dp(10), dp(10))
                    iv.y = (startY - iv.layoutParams.height / 2f) + kotlin.random.Random.nextInt(-dp(10), dp(10))
                    val endY = screenH + dp(40)
                    val driftX = kotlin.random.Random.nextInt(-screenW / 2, screenW / 2)
                    val rot = if (kotlin.random.Random.nextBoolean()) 540f else -540f
                    val duration = (1200L..1900L).random()
                    val fadeIn = android.animation.ObjectAnimator.ofFloat(iv, View.ALPHA, 0f, 1f).apply { this.duration = 150 }
                    val fallY = android.animation.ObjectAnimator.ofFloat(iv, View.TRANSLATION_Y, 0f, (endY - iv.y)).apply {
                        this.duration = duration
                        interpolator = android.view.animation.AccelerateInterpolator(1.5f)
                    }
                    val swayX = android.animation.ObjectAnimator.ofFloat(iv, View.TRANSLATION_X, 0f, driftX.toFloat()).apply {
                        this.duration = duration
                        interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                    }
                    val rotate = android.animation.ObjectAnimator.ofFloat(iv, View.ROTATION, 0f, rot).apply { this.duration = duration }
                    val fadeOut = android.animation.ObjectAnimator.ofFloat(iv, View.ALPHA, 1f, 0f).apply { this.duration = 260; startDelay = (duration - 260).coerceAtLeast(0) }
                    android.animation.AnimatorSet().apply {
                        playTogether(fadeIn, fallY, swayX, rotate, fadeOut)
                        addListener(object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: android.animation.Animator) { root.removeView(iv) }
                        })
                        start()
                    }
                } catch (_: Exception) {}
            }, i * 28L)
        }

        // Fade out overlay after a short delay
        overlay.postDelayed({
            android.animation.ObjectAnimator.ofFloat(overlay, View.ALPHA, 1f, 0f).apply {
                duration = 300
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) { root.removeView(overlay) }
                })
                start()
            }
        }, 1400L)
    }
}
