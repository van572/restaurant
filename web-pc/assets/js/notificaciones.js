const Toast = {
    container: null,

    init() {
        if (!this.container) {
            this.container = document.createElement('div');
            this.container.className = 'toast-container';
            document.body.appendChild(this.container);
        }
    },

    show(options) {
        this.init();
        const { title, message, type = 'info', duration = 4000 } = options;

        const toast = document.createElement('div');
        toast.className = `toast ${type}`;
        
        const icons = {
            success: 'checkmark-circle',
            error: 'alert-circle',
            warning: 'warning',
            info: 'information-circle'
        };

        toast.innerHTML = `
            <div class="toast-icon">
                <ion-icon name="${icons[type]}"></ion-icon>
            </div>
            <div class="toast-content">
                <div class="toast-title">${title}</div>
                <div class="toast-message">${message}</div>
            </div>
            <div class="toast-close">
                <ion-icon name="close-outline"></ion-icon>
            </div>
            <div class="toast-progress" style="animation-duration: ${duration}ms"></div>
        `;

        this.container.appendChild(toast);

        const closeBtn = toast.querySelector('.toast-close');
        closeBtn.onclick = () => this.dismiss(toast);

        setTimeout(() => this.dismiss(toast), duration);
    },

    dismiss(toast) {
        if (toast.classList.contains('fade-out')) return;
        toast.classList.add('fade-out');
        setTimeout(() => {
            if (toast.parentNode) {
                toast.parentNode.removeChild(toast);
            }
        }, 300);
    },

    success(title, message) { this.show({ title, message, type: 'success' }); },
    error(title, message) { this.show({ title, message, type: 'error' }); },
    warning(title, message) { this.show({ title, message, type: 'warning' }); },
    info(title, message) { this.show({ title, message, type: 'info' }); }
};
