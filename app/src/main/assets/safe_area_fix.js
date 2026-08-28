(function(){
  if(!document.getElementById('psh-safe-area-fix')){
    var style=document.createElement('style');
    style.id='psh-safe-area-fix';
    style.textContent=`
      /* Data Vault used to occupy the final dashboard-secondary slot and gave the
         home screen trailing scroll clearance. Now that Vault lives in Settings,
         reserve that clearance explicitly on the dashboard footer itself. */
      .dashboard-secondary{
        padding-bottom:90px !important;
      }
    `;
    document.head.appendChild(style);
  }

  /* Keep appearance fixes isolated from the large legacy index.html so light mode
     stays untouched and dark mode can be iterated independently. */
  if(!document.getElementById('psh-dark-mode-script')){
    var dark=document.createElement('script');
    dark.id='psh-dark-mode-script';
    dark.src='dark_mode.js';
    document.head.appendChild(dark);
  }
})();
