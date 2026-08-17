package com.cloner.app.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.cloner.app.R

/**
 * CalculatorActivity: Màn hình ngụy trang máy tính bỏ túi.
 * Hoạt động như một máy tính thật. Khi nhập đúng mã PIN bí mật + phím "=" sẽ mở ra kho App Cloner.
 */
class CalculatorActivity : Activity() {

    private lateinit var tvDisplay: TextView
    private lateinit var tvResult: TextView

    private var currentInput = StringBuilder()
    private var secretPin = "1234" // Mã PIN bí mật mặc định

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calculator)

        tvDisplay = findViewById(R.id.tvDisplay)
        tvResult = findViewById(R.id.tvResult)

        setupButtonListeners()
    }

    private fun setupButtonListeners() {
        val numButtons = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        )

        for (id in numButtons) {
            findViewById<Button>(id)?.setOnClickListener { btn ->
                val digit = (btn as Button).text.toString()
                currentInput.append(digit)
                updateDisplay()
            }
        }

        val opButtons = listOf(R.id.btnAdd, R.id.btnSub, R.id.btnMul, R.id.btnDiv)
        for (id in opButtons) {
            findViewById<Button>(id)?.setOnClickListener { btn ->
                val op = (btn as Button).text.toString()
                if (currentInput.isNotEmpty() && !isLastCharOperator()) {
                    currentInput.append(" $op ")
                    updateDisplay()
                }
            }
        }

        findViewById<Button>(R.id.btnClear)?.setOnClickListener {
            currentInput.clear()
            tvResult.text = ""
            updateDisplay()
        }

        findViewById<Button>(R.id.btnDelete)?.setOnClickListener {
            if (currentInput.isNotEmpty()) {
                currentInput.deleteCharAt(currentInput.length - 1)
                updateDisplay()
            }
        }

        findViewById<Button>(R.id.btnEqual)?.setOnClickListener {
            checkPinOrCalculate()
        }
    }

    private fun checkPinOrCalculate() {
        val inputStr = currentInput.toString().trim()

        // Kiểm tra xem có khớp mã PIN mở khóa không
        if (inputStr == secretPin) {
            currentInput.clear()
            updateDisplay()
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        // Tính toán thông thường
        try {
            val result = evaluateSimpleExpression(inputStr)
            tvResult.text = "= $result"
        } catch (e: Exception) {
            tvResult.text = "Lỗi phép tính"
        }
    }

    private fun evaluateSimpleExpression(expr: String): Double {
        val tokens = expr.split(" ")
        if (tokens.size < 3) return tokens.firstOrNull()?.toDoubleOrNull() ?: 0.0
        var total = tokens[0].toDoubleOrNull() ?: 0.0
        var i = 1
        while (i < tokens.size - 1) {
            val op = tokens[i]
            val nextVal = tokens[i + 1].toDoubleOrNull() ?: 0.0
            when (op) {
                "+" -> total += nextVal
                "-" -> total -= nextVal
                "×", "*" -> total *= nextVal
                "÷", "/" -> if (nextVal != 0.0) total /= nextVal
            }
            i += 2
        }
        return total
    }

    private fun isLastCharOperator(): Boolean {
        val str = currentInput.trimEnd()
        return str.endsWith("+") || str.endsWith("-") || str.endsWith("×") || str.endsWith("÷") || str.endsWith("*") || str.endsWith("/")
    }

    private fun updateDisplay() {
        tvDisplay.text = if (currentInput.isEmpty()) "0" else currentInput.toString()
    }
}
