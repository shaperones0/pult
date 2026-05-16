package com.example.pult

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pult.databinding.ActivityLoginBinding
import com.example.pult.db.DatabaseHelper
import com.example.pult.network.PultApi
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class LoginActivity: AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var prefsManager: PrefsManager

    //scanner
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            parseQrCode(result.contents)
        }
        else {
            Toast.makeText(this, "Сканирование отменено", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefsManager = PrefsManager(this)

        if (prefsManager.isLoggedIn()) {
            startMainActivity()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            var url = binding.etServerUrl.text.toString().trim()
            val key = binding.etApiKey.text.toString().trim()

            if (url.isEmpty() || key.isEmpty()) {
                Toast.makeText(
                    this,
                    "Пожалуйста, заполните все поля",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://$url"
            }
            //retrofit requires end with /
            if (!url.endsWith("/")) {
                url = "$url/"
            }

            validateAndLogin(url, key)
        }

        binding.btnScanQr.setOnClickListener {
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            options.setPrompt("Наведите камеру на QR-код")
            options.setCameraId(0) //back cam
            options.setBeepEnabled(false) //no beep (?????)
            options.setOrientationLocked(false) //screen rotate
            barcodeLauncher.launch(options)
        }
    }

    private fun parseQrCode(jsonString: String) {
        try {
            val jsonObject = JSONObject(jsonString)
            val url = jsonObject.getString("url")
            val key = jsonObject.getString("key")

            // Подставляем значения в поля, чтобы пользователь видел, что он отсканировал
            binding.etServerUrl.setText(url)
            binding.etApiKey.setText(key)

            // Автоматически запускаем процесс входа (как будто пользователь сам нажал кнопку)
            binding.btnLogin.performClick()

        } catch (e: Exception) {
            Toast.makeText(this, "Неверный формат QR-кода. Ожидается JSON.", Toast.LENGTH_LONG).show()
        }
    }

    private fun validateAndLogin(url: String, key: String) {
        //block button
        binding.btnLogin.isEnabled = false
        binding.btnLogin.text = "Connecting..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                //temp client
                val testClient = OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .addHeader("X-API-Key", key)
                            .build()
                        chain.proceed(request)
                    }.build()

                val testApi = Retrofit.Builder()
                    .baseUrl(url)
                    .client(testClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(PultApi::class.java)

                //check metrics
                testApi.getMetrics()

                //200 ok
                withContext(Dispatchers.Main) {
                    prefsManager.saveCredentials(url, key)

                    DatabaseHelper(this@LoginActivity).clearAll()

//                    Toast.makeText(this@LoginActivity, "Успешно подключено!", Toast.LENGTH_SHORT).show()
                    startMainActivity()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.btnLogin.isEnabled = true
                    binding.btnLogin.text = "Connect"
                    Toast.makeText(this@LoginActivity, "Ошибка подключения: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}