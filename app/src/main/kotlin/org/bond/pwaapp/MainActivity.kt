package org.bond.pwaapp

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import org.bond.pwaapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        keepClearOfBarsAndKeyboard()
    }

    private fun keepClearOfBarsAndKeyboard() {
        val sides = WindowInsetsCompat.Type.systemBars() or
            WindowInsetsCompat.Type.displayCutout() or
            WindowInsetsCompat.Type.ime()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val clear = insets.getInsets(sides)
            view.updatePadding(left = clear.left, top = clear.top, right = clear.right, bottom = clear.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }
}
