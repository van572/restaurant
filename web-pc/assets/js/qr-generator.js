/**
 * LOGICA GENERADOR DE QR
 * Automatiza la creación de etiquetas para las mesas del restaurante.
 */

document.addEventListener('DOMContentLoaded', () => {
    // Detectar URL Base actual
    const currentPath = window.location.pathname;
    let basePath = window.location.origin + currentPath.substring(0, currentPath.lastIndexOf('/') + 1);
    const clientUrl = basePath + "cliente.html";
    
    document.getElementById('url-base').value = clientUrl;
    document.getElementById('detected-url').textContent = clientUrl;
});

function generarLoteQR() {
    const urlBase = document.getElementById('url-base').value.trim();
    const inicio = parseInt(document.getElementById('mesa-inicio').value);
    const fin = parseInt(document.getElementById('mesa-fin').value);
    
    if (!urlBase) {
        alert("Por favor ingresa una URL válida.");
        return;
    }

    if (isNaN(inicio) || isNaN(fin) || inicio > fin) {
        alert("Rango de mesas inválido.");
        return;
    }

    const grid = document.getElementById('qr-grid');
    grid.innerHTML = '';
    document.getElementById('result-container').style.display = 'block';

    for(let i = inicio; i <= fin; i++) {
        const tableUrl = `${urlBase}?mesa=${i}`;
        crearCardQR(i, tableUrl);
    }
}

function crearCardQR(numeroMesa, url) {
    const grid = document.getElementById('qr-grid');
    
    const card = document.createElement('div');
    card.className = 'qr-item';
    
    const label = document.createElement('span');
    label.className = 'qr-label';
    label.textContent = `MESA ${numeroMesa}`;
    card.appendChild(label);

    const urlPreview = document.createElement('div');
    urlPreview.className = 'url-preview';
    urlPreview.textContent = url;
    card.appendChild(urlPreview);

    const qrBox = document.createElement('div');
    qrBox.className = 'qr-code-box';
    qrBox.id = `qr-table-${numeroMesa}`;
    card.appendChild(qrBox);

    const btn = document.createElement('button');
    btn.className = 'btn-download';
    btn.innerHTML = '<ion-icon name="download-outline"></ion-icon> Descargar PNG';
    btn.onclick = () => descargarQR(numeroMesa);
    card.appendChild(btn);

    grid.appendChild(card);

    // Generar el QR usando la librería
    new QRCode(qrBox, {
        text: url,
        width: 160,
        height: 160,
        colorDark : "#000000",
        colorLight : "#ffffff",
        correctLevel : QRCode.CorrectLevel.H
    });
}

function descargarQR(numero) {
    const box = document.getElementById(`qr-table-${numero}`);
    const img = box.querySelector('img');
    
    if (img) {
        const link = document.createElement('a');
        link.href = img.src;
        link.download = `QR_Mesa_${numero}.png`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    } else {
        // A veces tarda milisegundos en renderizar canvas a img
        alert("La imagen aún se está generando. Intenta en un segundo.");
    }
}
