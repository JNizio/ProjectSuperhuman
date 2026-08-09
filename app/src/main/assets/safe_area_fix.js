(function(){
  if(document.getElementById('psh-safe-area-fix')) return;
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
})();
