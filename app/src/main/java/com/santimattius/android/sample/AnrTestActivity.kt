package com.santimattius.android.sample

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

class AnrTestActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("anr_test_prefs", Context.MODE_PRIVATE)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AnrTestScreen {
                        triggerSharedPreferencesAbuse()
                    }
                }
            }
        }
    }

    private fun triggerSharedPreferencesAbuse() {
        Log.d("AnrTestActivity", "Starting SharedPreferences abuse on main thread...")

        // Perform a large number of blocking read operations
        Log.d("AnrTestActivity", "Starting read operations...")
        for (i in 0..5000) { // Reduced loop for reads to balance with writes
            prefs.getString("key_read_$i", null) // Reading keys that might not exist
        }
        Log.d("AnrTestActivity", "Finished read operations.")

        val editor = prefs.edit()
        // Perform a large number of blocking write operations
        Log.d("AnrTestActivity", "Starting write operations...")
        for (i in 0..10000) {
            editor.putString("key_write_$i", "value_write_$i")
        }
        // commit() is synchronous and will block the main thread
        editor.commit()
        Log.d("AnrTestActivity", "Finished SharedPreferences abuse.")
    }

}

@Composable
fun AnrTestScreen(onTriggerClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = onTriggerClick) {
            Text("Trigger ANR with SharedPreferences (Compose)")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MaterialTheme {
        AnrTestScreen {}
    }
}
