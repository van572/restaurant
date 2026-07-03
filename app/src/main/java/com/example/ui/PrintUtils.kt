package com.example.ui

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.formatQty

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

    fun generateReceiptHtml(
        mesa: String,
        items: List<com.example.data.ItemPedido>,
        totalUsd: Double,
        tasaCambio: Double,
        clienteNombre: String = "",
        clienteRif: String = "",
        clienteDireccion: String = ""
    ): String {
        val baseImponible = totalUsd / 1.16
        val iva16 = totalUsd - baseImponible
        val totalVes = totalUsd * tasaCambio
        val baseImponibleVes = baseImponible * tasaCambio
        val iva16Ves = iva16 * tasaCambio
        val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val randomInvoiceNum = (100000..999999).random()

        val itemsHtml = items.joinToString("") { item ->
            val uPrecio = item.precio
            val totalItem = item.precio * item.cantidad
            val itemBase = totalItem / 1.16
            val itemIva = totalItem - itemBase
            val uBase = uPrecio / 1.16
            val uIva = uPrecio - uBase
            """
            <tr style="border-bottom: 1px dotted #ccc;">
                <td style="text-align:left; padding: 6px 0; font-size: 11px; line-height: 1.3;">
                    <strong>${item.producto.uppercase()} (G)</strong><br/>
                    <span style="font-size:10px; color:#444;">
                        Especificación: Servicio Gastronómico Consumo en Sala<br/>
                        ${item.cantidad.formatQty()} Unid. x $${"%.2f".format(uPrecio)} USD<br/>
                        <span style="color:#555;">S.B. Unitario: $${"%.2f".format(uBase)} | Alícuota: 16.00% | IVA Unit: $${"%.2f".format(uIva)}</span><br/>
                        <span style="font-weight:600; color:#111;">B.I. Item: $${"%.2f".format(itemBase)} | IVA Item: $${"%.2f".format(itemIva)}</span>
                    </span>
                </td>
                <td style="text-align:right; padding: 6px 0; font-size: 11px; font-weight: bold; vertical-align: bottom; white-space: nowrap;">
                    $${"%.2f".format(totalItem)} USD<br/>
                    <span style="font-size: 9px; color: #555; font-weight: normal;">Bs. ${"%.2f".format(totalItem * tasaCambio)}</span>
                </td>
            </tr>
            """.trimIndent()
        }

        return """
            <html>
            <head>
                <style>
                    @page { margin: 0; }
                    body {
                        font-family: 'Courier New', Courier, monospace;
                        width: 76mm;
                        margin: 0;
                        padding: 10px;
                        color: #000;
                        background: #fff;
                        font-size: 11px;
                        line-height: 1.4;
                    }
                    .fiscal-header {
                        text-align: center;
                        margin-bottom: 8px;
                    }
                    .company-name {
                        font-size: 16px;
                        font-weight: 900;
                        margin-bottom: 2px;
                    }
                    .company-rif {
                        font-size: 12px;
                        font-weight: bold;
                        margin-bottom: 2px;
                    }
                    .company-details {
                        font-size: 10px;
                        color: #333;
                        margin-bottom: 3px;
                    }
                    .fiscal-banner {
                        text-align: center;
                        font-size: 14px;
                        font-weight: 900;
                        border-top: 1px dashed #000;
                        border-bottom: 1px dashed #000;
                        padding: 4px 0;
                        margin: 8px 0;
                        letter-spacing: 1px;
                    }
                    .meta-info {
                        font-size: 11px;
                        margin-bottom: 8px;
                        border-bottom: 1px dashed #000;
                        padding-bottom: 6px;
                    }
                    .items-table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-bottom: 8px;
                    }
                    .items-table th {
                        border-bottom: 1px dashed #000;
                        text-align: left;
                        padding-bottom: 4px;
                        font-size: 10px;
                    }
                    .total-box {
                        border-top: 1px dashed #000;
                        margin-top: 8px;
                        padding-top: 6px;
                    }
                    .total-row {
                        display: flex;
                        justify-content: space-between;
                        font-size: 11px;
                        margin-bottom: 2px;
                    }
                    .total-row-highlight {
                        font-size: 13px;
                        font-weight: 900;
                        border-top: 1px dashed #000;
                        border-bottom: 1px dashed #000;
                        padding: 5px 0;
                        margin: 6px 0;
                    }
                    .footer {
                        text-align: center;
                        font-size: 9px;
                        border-top: 1px dashed #000;
                        padding-top: 8px;
                        margin-top: 12px;
                        line-height: 1.3;
                    }
                </style>
            </head>
            <body>
                <div class="fiscal-header">
                    <div class="company-name">FOGÓN GUAROTUYERO</div>
                    <div class="company-details" style="font-weight: bold; font-size: 11px;">TASCA RESTAURANTE</div>
                    <div class="company-rif">RIF: J-303602550</div>
                    <div class="company-details">MÚSICA EN VIVO Y LOS MEJORES DJS</div>
                </div>
                
                <div class="fiscal-banner">*** FACTURA FISCAL ***</div>
                
                <div class="meta-info">
                    <div style="font-size: 11px; margin-bottom: 4px; border-bottom: 1px dashed #eee; padding-bottom: 4px;">
                        <strong>DATOS DEL RECEPTOR:</strong><br/>
                        <strong>RAZÓN SOCIAL:</strong> ${if (clienteNombre.isNotBlank()) clienteNombre.uppercase() else "CONSUMIDOR FINAL (MESA ${mesa.uppercase()})"}<br/>
                        <strong>RIF / C.I.:</strong> ${if (clienteRif.isNotBlank()) clienteRif.uppercase() else "V-99999999-9"}<br/>
                        <strong>DOMICILIO:</strong> ${if (clienteDireccion.isNotBlank()) clienteDireccion.uppercase() else "AV. PRINCIPAL, CARACAS"}<br/>
                    </div>
                    <div><strong>📅 FECHA / HORA:</strong> $dateStr</div>
                    <div><strong>👤 ATENDIÓ:</strong> Mesero App</div>
                    <div><strong>🆔 FACTURA FISCAL NRO:</strong> F-$randomInvoiceNum</div>
                    <div><strong>🔢 NRO. CONTROL:</strong> 00-00$randomInvoiceNum</div>
                    <div><strong>💳 ESTADO PAGO:</strong> CANCELADO</div>
                </div>
                
                <table class="items-table">
                    <thead>
                        <tr>
                            <th style="text-align:left;">CONCEPTO / DESCRIPCIÓN</th>
                            <th style="text-align:right;">MONTO</th>
                        </tr>
                    </thead>
                    <tbody>
                        $itemsHtml
                    </tbody>
                </table>
                
                <div class="total-box">
                    <div class="total-row">
                        <span>BASE IMPONIBLE (G 16.00%):</span>
                        <span>$${"%.2f".format(baseImponible)}</span>
                    </div>
                    <div class="total-row">
                        <span>I.V.A. EXENTO:</span>
                        <span>$0.00</span>
                    </div>
                    <div class="total-row">
                        <span>I.V.A. (16.00%):</span>
                        <span>$${"%.2f".format(iva16)}</span>
                    </div>
                    
                    <div class="total-row total-row-highlight">
                        <span>TOTAL FACTURA USD:</span>
                        <span>$${"%.2f".format(totalUsd)}</span>
                    </div>

                    <div class="total-row" style="font-weight: bold; margin-top: 4px;">
                        <span>BI. VES (Bs.):</span>
                        <span>Bs. ${"%.2f".format(baseImponibleVes)}</span>
                    </div>
                    <div class="total-row" style="font-weight: bold;">
                        <span>IVA. VES (Bs.):</span>
                        <span>Bs. ${"%.2f".format(iva16Ves)}</span>
                    </div>
                    <div class="total-row" style="font-size: 12px; font-weight: 900; color: #000; margin-top: 2px;">
                        <span>TOTAL VES (Bs.):</span>
                        <span>Bs. ${"%.2f".format(totalVes)}</span>
                    </div>
                    <div style="font-size: 8px; text-align: right; color: #444; margin-top: 2px; font-style: italic;">
                        Tasa Oficial de Cambio: Bs. ${"%.2f".format(tasaCambio)}
                    </div>
                </div>
                
                <div class="footer">
                    <p style="font-weight: bold; margin: 0;">MÁQUINA FISCAL: DFG0012345</p>
                    <p style="font-weight: bold; margin: 2px 0 0 0;">NRO. REGISTRO: SENIAT-000456123</p>
                    <p style="margin-top: 10px; font-style: italic;">*** GRACIAS POR SU VISITA Y COMPRA ***</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    fun generateKitchenComandaHtml(mesa: String, items: List<com.example.data.ItemPedido>, pedidoId: String, atendió: String): String {
        val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        val itemsHtml = items.joinToString("") { item ->
            """
            <tr>
                <td style="text-align:left; font-size: 18px; padding: 6px 0; border-bottom: 1px dashed #ddd; line-height: 1.3;">
                    <span style="font-size: 22px; font-weight: 900; background: #000; color: #fff; padding: 2px 6px; border-radius: 4px; margin-right: 6px;">${item.cantidad.formatQty()}x</span> 
                    <strong>${item.producto.uppercase()}</strong>
                </td>
            </tr>
            ${if (item.notas.isNotBlank()) """<tr><td style="text-align:left; font-size: 15px; padding-left: 15px; padding-bottom: 8px; color: #333; font-weight: bold; font-style: italic; background: #f9f9f9; border-left: 3px solid #000;">📝 Notas: ${item.notas}</td></tr>""" else ""}
            """.trimIndent()
        }

        return """
            <html>
            <head>
                <style>
                    @page { margin: 0; }
                    body {
                        font-family: 'Courier New', Courier, monospace;
                        width: 76mm;
                        margin: 0;
                        padding: 10px;
                        color: #000;
                        background: #fff;
                    }
                    .ticket-title {
                        text-align: center;
                        font-size: 22px;
                        font-weight: 900;
                        margin-bottom: 6px;
                        border: 3px double #000;
                        padding: 6px 0;
                        letter-spacing: 1px;
                    }
                    .meta-info {
                        font-size: 14px;
                        margin-bottom: 12px;
                        border-bottom: 2px dashed #000;
                        padding-bottom: 6px;
                        line-height: 1.5;
                    }
                    .client-highlight {
                        font-size: 20px;
                        font-weight: 900;
                        background: #f0f0f0;
                        padding: 6px;
                        border: 1px solid #000;
                        margin-bottom: 6px;
                        text-align: center;
                    }
                    .items-table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-bottom: 15px;
                    }
                    .footer {
                        text-align: center;
                        font-size: 13px;
                        border-top: 2px dashed #000;
                        padding-top: 8px;
                        margin-top: 15px;
                        font-weight: bold;
                    }
                </style>
            </head>
            <body>
                <div class="ticket-title">TICKET DE COCINA</div>
                <div class="client-highlight">🎯 CLIENTE: ${mesa.uppercase()}</div>
                <div class="meta-info">
                    <div>📅 <strong>Fecha:</strong> $dateStr</div>
                    <div>👤 <strong>Atendió:</strong> ${atendió.uppercase()}</div>
                    <div>🆔 <strong>Pedido Nro:</strong> #$pedidoId</div>
                </div>
                <table class="items-table">
                    <tbody>
                        $itemsHtml
                    </tbody>
                </table>
                <div class="footer">
                    <p>*** FIN DE COMANDA COCINA ***</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
