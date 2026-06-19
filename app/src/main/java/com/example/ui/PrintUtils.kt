package com.example.ui

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

object PrintUtils {
    fun printTicket(context: Context, htmlContent: String) {
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val printAdapter = webView.createPrintDocumentAdapter("Ticket_RestFlow")
                printManager.print("Ticket_RestFlow", printAdapter, PrintAttributes.Builder().build())
            }
        }
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    }

    fun printReport(context: Context, htmlContent: String, jobName: String = "Reporte_RestFlow") {
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val printAdapter = webView.createPrintDocumentAdapter(jobName)
                
                val printAttributes = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.NA_LETTER)
                    .build()
                    
                printManager.print(jobName, printAdapter, printAttributes)
            }
        }
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    }

    fun generateAdminReportHtml(title: String, headers: List<String>, rows: List<List<String>>, totalUsd: Double, tasaCambio: Double): String {
        val headersHtml = headers.joinToString("") { 
            "<th style='border: 1px solid #ddd; padding: 8px; background-color: #f2f2f2; text-align: left;'>$it</th>"
        }
        
        val rowsHtml = rows.joinToString("") { row ->
            "<tr>" + row.joinToString("") { cell ->
                "<td style='border: 1px solid #ddd; padding: 8px;'>$cell</td>"
            } + "</tr>"
        }

        return """
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; margin: 20px; }
                    .header { text-align: center; margin-bottom: 20px; border-bottom: 2px solid #333; padding-bottom: 10px; }
                    table { width: 100%; border-collapse: collapse; margin-bottom: 20px; font-size: 12px; }
                    .summary { margin-top: 30px; text-align: right; border-top: 1px solid #000; padding-top: 10px; }
                    h1 { margin: 0; color: #333; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>$title</h1>
                    <p>Fecha Generación: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}</p>
                </div>
                <table>
                    <thead>
                        <tr>$headersHtml</tr>
                    </thead>
                    <tbody>
                        $rowsHtml
                    </tbody>
                </table>
                <div class="summary">
                    <p><strong>TOTAL GENERAL USD:</strong> ${"%.2f".format(totalUsd)}</p>
                    <p><strong>TOTAL GENERAL VES:</strong> ${"%.2f".format(totalUsd * tasaCambio)}</p>
                    <p style="font-size: 10px; color: #666;">(Calculado a tasa de $tasaCambio VES/USD)</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    fun generateReceiptHtml(mesa: String, items: List<com.example.data.ItemPedido>, totalUsd: Double, tasaCambio: Double): String {
        val totalVes = totalUsd * tasaCambio
        val itemsHtml = items.joinToString("") { item ->
            """
            <tr>
                <td style="text-align:left;">${item.producto} x${item.cantidad}</td>
                <td style="text-align:right;">${"%.2f".format(item.precio * item.cantidad)}</td>
            </tr>
            """.trimIndent()
        }

        return """
            <html>
            <head>
                <style>
                    body { font-family: 'Courier New', Courier, monospace; width: 80mm; padding: 5px; color: #000; }
                    .header { text-align: center; border-bottom: 1px dashed #000; padding-bottom: 10px; }
                    .items { width: 100%; border-collapse: collapse; margin-top: 10px; }
                    .total { border-top: 1px dashed #000; margin-top: 10px; padding-top: 10px; font-weight: bold; }
                    .footer { text-align: center; margin-top: 20px; font-size: 0.8em; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h2>RESTAURANT FLOW</h2>
                    <p>Mesa: $mesa</p>
                    <p>Fecha: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}</p>
                </div>
                <table class="items">
                    $itemsHtml
                </table>
                <div class="total">
                    <div style="display: flex; justify-content: space-between;">
                        <span>TOTAL USD:</span>
                        <span>${"%.2f".format(totalUsd)}</span>
                    </div>
                    <div style="display: flex; justify-content: space-between; font-size: 1.1em;">
                        <span>TOTAL VES:</span>
                        <span>${"%.2f".format(totalVes)}</span>
                    </div>
                    <p style="font-size: 0.7em; text-align: center;">Tasa del día: $tasaCambio VES/USD</p>
                </div>
                <div class="footer">
                    <p>¡Gracias por su visita!</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
