document.addEventListener("DOMContentLoaded", function () {
  if (window.bootstrap) {
    setTimeout(function () {
      document.querySelectorAll(".alert").forEach(function (alertEl) {
        try {
          new bootstrap.Alert(alertEl).close();
        } catch (e) {
          // Keep page stable if bootstrap alert is unavailable on this view.
        }
      });
    }, 2200);
  }
});
